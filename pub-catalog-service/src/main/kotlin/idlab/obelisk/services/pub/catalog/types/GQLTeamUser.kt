package idlab.obelisk.services.pub.catalog.types

import graphql.schema.DataFetchingEnvironment
import idlab.obelisk.definitions.catalog.AccessManager
import idlab.obelisk.definitions.catalog.MetaStore
import idlab.obelisk.definitions.catalog.User
import idlab.obelisk.services.pub.catalog.impl.TeamUser
import idlab.obelisk.services.pub.catalog.types.util.GQLFetcher
import idlab.obelisk.services.pub.catalog.types.util.GQLType
import idlab.obelisk.services.pub.catalog.types.util.Operations
import java.util.concurrent.CompletionStage
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
@GQLType("TeamUser")
class GQLTeamUser @Inject constructor(accessManager: AccessManager, private val metaStore: MetaStore) :
    Operations(accessManager) {

    @GQLFetcher
    fun manager(env: DataFetchingEnvironment): CompletionStage<Boolean> {
        return withAccess(env) {
            val source = env.getSource<TeamUser>()
            metaStore.getUser(source.userId)
                .toSingle()
                .map { user -> user.teamMemberships.any { it.teamId == source.teamId && it.manager } }
        }
    }

    @GQLFetcher
    fun user(env: DataFetchingEnvironment): CompletionStage<User> {
        return withAccess(env) {
            val source = env.getSource<TeamUser>()
            metaStore.getUser(source.userId).toSingle()
        }
    }


}