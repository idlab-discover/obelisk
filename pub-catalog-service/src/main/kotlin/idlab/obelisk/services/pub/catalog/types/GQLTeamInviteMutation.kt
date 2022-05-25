package idlab.obelisk.services.pub.catalog.types

import graphql.schema.DataFetchingEnvironment
import hu.akarnokd.rxjava3.bridge.RxJavaBridge
import idlab.obelisk.definitions.Eq
import idlab.obelisk.definitions.Field
import idlab.obelisk.definitions.catalog.AccessManager
import idlab.obelisk.definitions.catalog.MetaStore
import idlab.obelisk.definitions.catalog.TeamMembership
import idlab.obelisk.definitions.catalog.codegen.TeamMembershipField
import idlab.obelisk.definitions.catalog.codegen.UserField
import idlab.obelisk.definitions.catalog.codegen.UserUpdate
import idlab.obelisk.services.pub.catalog.impl.Response
import idlab.obelisk.services.pub.catalog.impl.TeamInvite
import idlab.obelisk.services.pub.catalog.impl.errorResponse
import idlab.obelisk.services.pub.catalog.types.util.*
import idlab.obelisk.services.pub.catalog.types.util.getTeamInvites
import io.reactivex.Completable
import io.reactivex.Single
import io.vertx.core.json.Json
import org.redisson.api.RedissonClient
import java.util.concurrent.CompletionStage
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
@GQLType("TeamInviteMutation")
class GQLTeamInviteMutation @Inject constructor(
    accessManager: AccessManager,
    private val redis: RedissonClient,
    private val metaStore: MetaStore
) :
    Operations(accessManager) {

    @GQLFetcher
    fun accept(env: DataFetchingEnvironment): CompletionStage<Response<TeamInvite>> {
        return withAccess(env) { token ->
            val source = env.getSource<TeamInvite>()
            // Check if the team can have more members?
            metaStore.getTeam(source.teamId)
                .flatMapSingle { team -> metaStore.getUsagePlanOrDefault(team.usagePlanId) }
                .map { plan -> plan.maxUsers }
                .zipWith(
                    metaStore.countUsers(
                        Eq(
                            Field(UserField.TEAM_MEMBERSHIPS, TeamMembershipField.TEAM_ID),
                            source.teamId
                        )
                    )
                ) { maxUsers, userCount -> Pair(maxUsers, userCount) }
                .flatMap { (maxUsers, userCount) ->
                    if ((userCount + 1) <= maxUsers) {
                        // Accept invite and create user-team relationship
                        redis.getTeamInvites()[redisInviteKeyOrPattern(source.teamId, source.id)]
                            .to(RxJavaBridge.toV2Maybe())
                            .toSingle()
                            .map { Json.decodeValue(it, TeamInvite::class.java) }
                            .flatMap { invite ->
                                metaStore.getUser(token.user.id!!)
                                    .flatMapCompletable { user ->
                                        if (!user.teamMemberships.any { it.teamId == invite.teamId }) {
                                            metaStore.updateUser(
                                                user.id!!, UserUpdate(
                                                    teamMemberships = user.teamMemberships.plus(
                                                        TeamMembership(
                                                            invite.teamId
                                                        )
                                                    )
                                                )
                                            )
                                        } else {
                                            Completable.complete()
                                        }
                                    }
                                    .toSingleDefault(Response(item = invite))
                            }
                            .flatMap {
                                // Invalidate the token, so the access permission for the user are re-evaluated
                                accessManager.invalidateToken(token).toSingleDefault(it)
                            }
                            .onErrorReturn { errorResponse(env, it) }
                    } else {
                        Single.error { IllegalStateException("Cannot exceed maximum users for the team ($maxUsers)!") }
                    }
                }
        }
    }

    @GQLFetcher
    fun revoke(env: DataFetchingEnvironment): CompletionStage<Response<TeamInvite>> {
        return withAccess(env) {
            val source = env.getSource<TeamInvite>()
            redis.getTeamInvites()
                .remove(redisInviteKeyOrPattern(source.teamId, source.id))
                .to(RxJavaBridge.toV2Maybe())
                .toSingle()
                .map { Response(item = Json.decodeValue(it, TeamInvite::class.java)) }
                .onErrorReturn { errorResponse(env, it) }
        }
    }
}