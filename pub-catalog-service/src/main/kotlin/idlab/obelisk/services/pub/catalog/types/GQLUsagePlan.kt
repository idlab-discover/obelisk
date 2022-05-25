package idlab.obelisk.services.pub.catalog.types

import graphql.schema.DataFetchingEnvironment
import idlab.obelisk.definitions.catalog.*
import idlab.obelisk.services.pub.catalog.types.util.GQLFetcher
import idlab.obelisk.services.pub.catalog.types.util.GQLType
import idlab.obelisk.services.pub.catalog.types.util.Operations
import io.reactivex.Maybe
import io.reactivex.Single
import java.util.concurrent.CompletionStage
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
@GQLType("UsagePlan")
class GQLUsagePlan @Inject constructor(accessManager: AccessManager, private val metaStore: MetaStore) :
    Operations(accessManager) {

    @GQLFetcher
    fun userUsageLimitAssigned(env: DataFetchingEnvironment): CompletionStage<Boolean> {
        return withAccess(env) {
            Single.just(env.getSource<UsagePlan>().userUsageLimitId != null)
        }
    }

    @GQLFetcher
    fun userUsageLimit(env: DataFetchingEnvironment): CompletionStage<UsageLimit> {
        return withAccess(env) {
            metaStore.getUsageLimitOrDefault(env.getSource<UsagePlan>().userUsageLimitId)
        }
    }

    @GQLFetcher
    fun clientUsageLimitAssigned(env: DataFetchingEnvironment): CompletionStage<Boolean> {
        return withAccess(env) {
            Single.just(env.getSource<UsagePlan>().clientUsageLimitId != null)
        }
    }

    @GQLFetcher
    fun clientUsageLimit(env: DataFetchingEnvironment): CompletionStage<UsageLimit> {
        return withAccess(env) {
            metaStore.getUsageLimitOrDefault(env.getSource<UsagePlan>().clientUsageLimitId)
        }
    }

}