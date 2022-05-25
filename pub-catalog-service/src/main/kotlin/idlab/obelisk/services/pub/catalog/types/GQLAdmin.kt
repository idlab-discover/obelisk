package idlab.obelisk.services.pub.catalog.types

import graphql.schema.DataFetchingEnvironment
import idlab.obelisk.definitions.catalog.*
import idlab.obelisk.services.pub.catalog.impl.GraphQLPage
import idlab.obelisk.services.pub.catalog.impl.getFilter
import idlab.obelisk.services.pub.catalog.types.util.GQLFetcher
import idlab.obelisk.services.pub.catalog.types.util.GQLType
import idlab.obelisk.services.pub.catalog.types.util.Operations
import java.util.concurrent.CompletionStage
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
@GQLType("Admin")
class GQLAdmin @Inject constructor(
    private val metaStore: MetaStore,
    accessManager: AccessManager
) : Operations(accessManager) {

    @GQLFetcher
    fun dataset(env: DataFetchingEnvironment): CompletionStage<Dataset> {
        return withAccessMaybe(env) { metaStore.getDataset(env.getArgument("id")) }
    }

    @GQLFetcher
    fun datasets(env: DataFetchingEnvironment): CompletionStage<GraphQLPage<Dataset>> {
        return withAccess(env) {
            val filter = env.getFilter()
            metaStore.queryDatasets(filter, limitFrom(env), cursorFrom(env))
                .map { GraphQLPage(it.items, it.cursor) { metaStore.countDatasets(filter) } }
        }
    }

    @GQLFetcher
    fun user(env: DataFetchingEnvironment): CompletionStage<User> {
        return withAccessMaybe(env) { metaStore.getUser(env.getArgument("id")) }
    }

    @GQLFetcher
    fun users(env: DataFetchingEnvironment): CompletionStage<GraphQLPage<User>> {
        return withAccess(env) {
            val filter = env.getFilter()
            metaStore.queryUsers(
                filter = filter,
                limit = limitFrom(env),
                cursor = cursorFrom(env)
            )
                .map { GraphQLPage(it.items, it.cursor) { metaStore.countDatasets(filter) } }
        }
    }

    @GQLFetcher
    fun client(env: DataFetchingEnvironment): CompletionStage<Client> {
        return withAccessMaybe(env) {
            metaStore.getClient(env.getArgument("id"))
        }
    }

    @GQLFetcher
    fun clients(env: DataFetchingEnvironment): CompletionStage<GraphQLPage<Client>> {
        return withAccess(env) {
            val filter = env.getFilter()
            metaStore.queryClients(filter = filter, limit = limitFrom(env), cursor = cursorFrom(env))
                .map { GraphQLPage(it.items, it.cursor) { metaStore.countDatasets(filter) } }
        }
    }

    @GQLFetcher
    fun usagePlan(env: DataFetchingEnvironment): CompletionStage<UsagePlan> {
        return withAccessMaybe(env) {
            metaStore.getUsagePlan(env.getArgument("id"))
        }
    }

    @GQLFetcher
    fun usagePlans(env: DataFetchingEnvironment): CompletionStage<GraphQLPage<UsagePlan>> {
        return withAccess(env) {
            val filter = env.getFilter()
            metaStore.queryUsagePlans(filter = filter, limit = limitFrom(env), cursor = cursorFrom(env))
                .map { GraphQLPage(it.items, it.cursor) { metaStore.countDatasets(filter) } }
        }
    }

    @GQLFetcher
    fun usageLimit(env: DataFetchingEnvironment): CompletionStage<UsageLimit> {
        return withAccessMaybe(env) {
            metaStore.getUsageLimit(env.getArgument("id"))
        }
    }

    @GQLFetcher
    fun usageLimits(env: DataFetchingEnvironment): CompletionStage<GraphQLPage<UsageLimit>> {
        return withAccess(env) {
            val filter = env.getFilter()
            metaStore.queryUsageLimits(filter = filter, limit = limitFrom(env), cursor = cursorFrom(env))
                .map { GraphQLPage(it.items, it.cursor) { metaStore.countDatasets(filter) } }
        }
    }

    @GQLFetcher
    fun team(env: DataFetchingEnvironment): CompletionStage<Team> {
        return withAccessMaybe(env) {
            metaStore.getTeam(env.getArgument("id"))
        }
    }

    @GQLFetcher
    fun teams(env: DataFetchingEnvironment): CompletionStage<GraphQLPage<Team>> {
        return withAccess(env) {
            val filter = env.getFilter()
            metaStore.queryTeams(filter = filter, limit = limitFrom(env), cursor = cursorFrom(env))
                .map { GraphQLPage(it.items, it.cursor) { metaStore.countDatasets(filter) } }
        }
    }

}