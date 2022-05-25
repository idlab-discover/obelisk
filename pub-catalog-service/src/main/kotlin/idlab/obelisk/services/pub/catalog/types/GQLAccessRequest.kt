package idlab.obelisk.services.pub.catalog.types

import graphql.schema.DataFetchingEnvironment
import idlab.obelisk.definitions.catalog.*
import idlab.obelisk.services.pub.catalog.types.util.GQLFetcher
import idlab.obelisk.services.pub.catalog.types.util.GQLType
import idlab.obelisk.services.pub.catalog.types.util.Operations
import io.reactivex.Maybe
import java.util.concurrent.CompletionStage
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
@GQLType("AccessRequest")
class GQLAccessRequest @Inject constructor(
    private val metaStore: MetaStore,
    accessManager: AccessManager
) :
    Operations(accessManager) {

    @GQLFetcher
    fun user(env: DataFetchingEnvironment): CompletionStage<User> {
        return withAccessMaybe(env) {
            val accessRequest = env.getSource<AccessRequest>()
            metaStore.getUser(accessRequest.userId)
        }
    }

    @GQLFetcher
    fun team(env: DataFetchingEnvironment): CompletionStage<Team> {
        return withAccessMaybe(env) {
            val accessRequest = env.getSource<AccessRequest>()
            accessRequest.teamId?.let { teamId -> metaStore.getTeam(teamId) } ?: Maybe.empty()
        }
    }

    @GQLFetcher
    fun dataset(env: DataFetchingEnvironment): CompletionStage<Dataset> {
        return withAccessMaybe(env) {
            val accessRequest = env.getSource<AccessRequest>()
            metaStore.getDataset(accessRequest.datasetId)
        }
    }

}