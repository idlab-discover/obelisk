package idlab.obelisk.services.pub.catalog.types

import graphql.schema.DataFetchingEnvironment
import hu.akarnokd.rxjava3.bridge.RxJavaBridge
import idlab.obelisk.definitions.catalog.AccessManager
import idlab.obelisk.definitions.catalog.DatasetMembership
import idlab.obelisk.definitions.catalog.MetaStore
import idlab.obelisk.definitions.catalog.codegen.TeamUpdate
import idlab.obelisk.definitions.catalog.codegen.UserUpdate
import idlab.obelisk.services.pub.catalog.impl.Invite
import idlab.obelisk.services.pub.catalog.impl.Response
import idlab.obelisk.services.pub.catalog.impl.errorResponse
import idlab.obelisk.services.pub.catalog.types.util.*
import idlab.obelisk.services.pub.catalog.types.util.getInvites
import idlab.obelisk.utils.service.http.UnauthorizedException
import idlab.obelisk.utils.service.reactive.flatMap
import io.reactivex.Completable
import io.vertx.core.json.Json
import org.redisson.api.RedissonClient
import java.util.concurrent.CompletionStage
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
@GQLType("InviteMutation")
class GQLInviteMutation @Inject constructor(
    private val redis: RedissonClient,
    private val metaStore: MetaStore,
    accessManager: AccessManager
) : Operations(accessManager) {

    @GQLFetcher
    fun accept(env: DataFetchingEnvironment): CompletionStage<Response<Invite>> {
        return acceptHelper(env)
    }

    @GQLFetcher
    fun acceptAsTeam(env: DataFetchingEnvironment): CompletionStage<Response<Invite>> {
        return acceptHelper(env)
    }

    private fun acceptHelper(env: DataFetchingEnvironment): CompletionStage<Response<Invite>> {
        return withAccess(env) { token ->
            val source = env.getSource<Invite>()
            val teamId: String? = env.getArgument("teamId")
            redis.getInvites()[redisInviteKeyOrPattern(source.datasetId, source.id)]
                .to(RxJavaBridge.toV2Maybe())
                .toSingle()
                .map { Json.decodeValue(it, Invite::class.java) }
                .flatMap { invite ->
                    if (teamId != null) {
                        if (!invite.disallowTeams) {
                            metaStore.getTeam(teamId)
                                .flatMapCompletable { team ->
                                    // Find currently assigned roles
                                    val oldRoles = team.datasetMemberships.find { it.datasetId == invite.datasetId }
                                    // Update team datasetMemberships so membership for the target dataset is oldRoles + invite specified roles...
                                    metaStore.updateTeam(
                                        teamId, TeamUpdate(
                                            datasetMemberships = team.datasetMemberships.filterNot { it.datasetId == invite.datasetId }
                                                .plus(
                                                    DatasetMembership(
                                                        invite.datasetId,
                                                        invite.roleIds.plus(
                                                            oldRoles?.assignedRoleIds
                                                                ?: emptySet()
                                                        )
                                                    )
                                                )
                                        )
                                    )
                                }
                                .flatMap { invalidateTeam(teamId, metaStore) }
                        } else {
                            Completable.error { UnauthorizedException("The invite link cannot be used by Teams!") }
                        }
                    } else {
                        metaStore.getUser(token.user.id!!)
                            .flatMapCompletable { user ->
                                // Find currently assigned roles
                                val oldRoles = user.datasetMemberships.find { it.datasetId == invite.datasetId }
                                // Update team datasetMemberships so membership for the target dataset is oldRoles + invite specified roles...
                                metaStore.updateUser(
                                    user.id!!, UserUpdate(
                                        datasetMemberships = user.datasetMemberships.filterNot { it.datasetId == invite.datasetId }
                                            .plus(
                                                DatasetMembership(
                                                    invite.datasetId,
                                                    invite.roleIds.plus(
                                                        oldRoles?.assignedRoleIds
                                                            ?: emptySet()
                                                    )
                                                )
                                            )
                                    )
                                )
                            }
                            .flatMap { invalidateUser(token.user.id!!, metaStore) }
                    }.toSingleDefault(Response(item = invite))
                }
                .onErrorReturn { errorResponse(env, it) }
        }
    }

    @GQLFetcher
    fun revoke(env: DataFetchingEnvironment): CompletionStage<Response<Invite>> {
        return withAccess(env) {
            val source = env.getSource<Invite>()
            redis.getInvites()
                .remove(redisInviteKeyOrPattern(source.datasetId, source.id))
                .to(RxJavaBridge.toV2Maybe())
                .toSingle()
                .map { Response(item = Json.decodeValue(it, Invite::class.java)) }
                .onErrorReturn { errorResponse(env, it) }
        }
    }

}