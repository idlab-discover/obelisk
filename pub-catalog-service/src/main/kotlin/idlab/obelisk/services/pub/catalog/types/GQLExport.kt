package idlab.obelisk.services.pub.catalog.types

import graphql.schema.DataFetchingEnvironment
import idlab.obelisk.definitions.catalog.*
import idlab.obelisk.definitions.ratelimiting.RateLimiter
import idlab.obelisk.services.pub.catalog.types.util.GQLFetcher
import idlab.obelisk.services.pub.catalog.types.util.GQLType
import idlab.obelisk.services.pub.catalog.types.util.Operations
import io.reactivex.Maybe
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
@GQLType("DataExport")
class GQLExport @Inject constructor(
    private val metaStore: MetaStore,
    accessManager: AccessManager
) :
    Operations(accessManager) {
    @GQLFetcher
    fun user(env: DataFetchingEnvironment): CompletionStage<User> {
        return withAccess(env) {
            val parent = env.getSource<DataExport>()
            metaStore.getUser(parent.userId).toSingle()
        }
    }

    @GQLFetcher
    fun team(env: DataFetchingEnvironment): CompletionStage<Team> {
        return withAccessMaybe(env) {
            val parent = env.getSource<DataExport>()
            parent.teamId?.let { metaStore.getTeam(it) } ?: Maybe.empty()
        }
    }


    @GQLFetcher
    fun timestampPrecision(env: DataFetchingEnvironment): CompletionStage<TimeUnit> {
        return CompletableFuture.completedStage(env.getSource<DataExport>().timestampPrecision.unit)
    }

}