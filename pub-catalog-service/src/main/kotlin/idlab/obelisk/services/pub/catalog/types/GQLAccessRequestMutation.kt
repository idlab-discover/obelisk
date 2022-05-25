package idlab.obelisk.services.pub.catalog.types

import graphql.schema.DataFetchingEnvironment
import idlab.obelisk.definitions.Eq
import idlab.obelisk.definitions.catalog.AccessManager
import idlab.obelisk.definitions.catalog.AccessRequest
import idlab.obelisk.definitions.catalog.DatasetMembership
import idlab.obelisk.definitions.catalog.MetaStore
import idlab.obelisk.definitions.catalog.codegen.ClientField
import idlab.obelisk.definitions.catalog.codegen.TeamUpdate
import idlab.obelisk.definitions.catalog.codegen.UserUpdate
import idlab.obelisk.services.pub.catalog.impl.Response
import idlab.obelisk.services.pub.catalog.impl.errorResponse
import idlab.obelisk.services.pub.catalog.types.util.GQLFetcher
import idlab.obelisk.services.pub.catalog.types.util.GQLType
import idlab.obelisk.services.pub.catalog.types.util.Operations
import idlab.obelisk.utils.service.reactive.flatMap
import idlab.obelisk.utils.service.reactive.flatMapSingle
import idlab.obelisk.utils.service.utils.unpage
import java.util.concurrent.CompletionStage
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
@GQLType("AccessRequestMutation")
class GQLAccessRequestMutation @Inject constructor(
    private val metaStore: MetaStore,
    accessManager: AccessManager
) :
    Operations(accessManager) {


    @GQLFetcher
    fun accept(env: DataFetchingEnvironment): CompletionStage<Response<AccessRequest>> {
        return withAccess(env) { _ ->
            val source = env.getSource<AccessRequest>()
            val roleIds = env.getArgumentOrDefault("roleIds", emptyList<String>())
            val memberhip = DatasetMembership(source.datasetId, roleIds.toSet())
            (if (source.teamId != null) {
                metaStore.getTeam(source.teamId!!)
                    .flatMapCompletable { team ->
                        metaStore.updateTeam(
                            source.teamId!!, TeamUpdate(
                                datasetMemberships = team.datasetMemberships.filterNot { it.datasetId == source.datasetId }
                                    .plus(memberhip)
                            )
                        ).flatMap { invalidateTeam(team.id!!, metaStore) }
                    }
            } else {
                metaStore.getUser(source.userId)
                    .flatMapCompletable { user ->
                        metaStore.updateUser(source.userId,
                            UserUpdate(datasetMemberships = user.datasetMemberships.filterNot { it.datasetId == source.datasetId }
                                .plus(memberhip))
                        ).flatMap { invalidateUser(user.id!!, metaStore) }
                    }
            }).flatMapSingle { metaStore.removeAccessRequest(source.id!!).toSingleDefault(source) }
                .map { Response(item = it) }
                .onErrorReturn { errorResponse(env, it) }
        }
    }

    @GQLFetcher
    fun remove(env: DataFetchingEnvironment): CompletionStage<Response<AccessRequest>> {
        return withAccess(env) { _ ->
            val source = env.getSource<AccessRequest>()
            metaStore.removeAccessRequest(source.id!!).toSingleDefault(source)
                .map { Response(item = it) }
                .onErrorReturn { errorResponse(env, it) }
        }
    }

}