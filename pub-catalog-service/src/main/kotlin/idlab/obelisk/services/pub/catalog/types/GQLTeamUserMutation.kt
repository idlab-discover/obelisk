package idlab.obelisk.services.pub.catalog.types

import graphql.schema.DataFetchingEnvironment
import idlab.obelisk.definitions.catalog.AccessManager
import idlab.obelisk.definitions.catalog.MetaStore
import idlab.obelisk.definitions.catalog.codegen.UserUpdate
import idlab.obelisk.services.pub.catalog.impl.Response
import idlab.obelisk.services.pub.catalog.impl.TeamUser
import idlab.obelisk.services.pub.catalog.impl.errorResponse
import idlab.obelisk.services.pub.catalog.types.util.GQLFetcher
import idlab.obelisk.services.pub.catalog.types.util.GQLType
import idlab.obelisk.services.pub.catalog.types.util.Operations
import java.util.concurrent.CompletionStage
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
@GQLType("TeamUserMutation")
class GQLTeamUserMutation @Inject constructor(private val metaStore: MetaStore, accessManager: AccessManager) :
    Operations(accessManager) {

    @GQLFetcher
    fun setManager(env: DataFetchingEnvironment): CompletionStage<Response<TeamUser>> {
        return withAccess(env) {
            val source = env.getSource<TeamUser>()
            metaStore.getUser(source.userId)
                .toSingle()
                .flatMapCompletable { user ->
                    metaStore.updateUser(user.id!!, UserUpdate(teamMemberships = user.teamMemberships.map {
                        if (it.teamId == source.teamId) {
                            it.copy(manager = env.getArgument("teamManager"))
                        } else {
                            it
                        }
                    }))
                }
                .toSingleDefault(Response(item = source))
                .onErrorReturn { errorResponse(env, it) }
        }
    }

    @GQLFetcher
    fun remove(env: DataFetchingEnvironment): CompletionStage<Response<TeamUser>> {
        return withAccess(env) {
            val source = env.getSource<TeamUser>()
            metaStore.getUser(source.userId)
                .toSingle()
                .flatMapCompletable { user ->
                    metaStore.updateUser(
                        user.id!!,
                        UserUpdate(teamMemberships = user.teamMemberships.filterNot { it.teamId == source.teamId })
                    )
                }
                .toSingleDefault(Response(item = source))
                .onErrorReturn { errorResponse(env, it) }
        }
    }
}