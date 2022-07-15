package idlab.obelisk.services.pub.catalog.types

import graphql.schema.DataFetchingEnvironment
import idlab.obelisk.definitions.And
import idlab.obelisk.definitions.Eq
import idlab.obelisk.definitions.Ordering
import idlab.obelisk.definitions.PagedResult
import idlab.obelisk.definitions.catalog.*
import idlab.obelisk.definitions.catalog.codegen.AnnouncementField
import idlab.obelisk.services.pub.catalog.impl.GraphQLPage
import idlab.obelisk.services.pub.catalog.impl.getFilter
import idlab.obelisk.services.pub.catalog.types.util.GQLFetcher
import idlab.obelisk.services.pub.catalog.types.util.GQLType
import idlab.obelisk.services.pub.catalog.types.util.Operations
import io.reactivex.Single
import org.redisson.api.RedissonClient
import java.util.concurrent.CompletionStage
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
@GQLType("Query")
class GQLQueryRoot @Inject constructor(
    private val metaStore: MetaStore,
    accessManager: AccessManager,
    private val admin: GQLAdmin
) : Operations(accessManager) {

    @GQLFetcher
    fun me(env: DataFetchingEnvironment): CompletionStage<User> {
        return withAccessMaybe(env) {
            metaStore.getUser(it.user.id!!)
        }
    }

    @GQLFetcher
    fun publishedDatasets(env: DataFetchingEnvironment): CompletionStage<PagedResult<Dataset>> {
        return withOptionalAccess(env) {
            metaStore.queryDatasets(
                And(Eq("published", true), env.getFilter()),
                limitFrom(env),
                cursorFrom(env)
            )
        }
    }

    @GQLFetcher
    fun dataset(env: DataFetchingEnvironment): CompletionStage<Dataset> {
        return withAccessMaybe(env) {
            metaStore.getDataset(env.getArgument("id"))
        }
    }

    @GQLFetcher
    fun team(env: DataFetchingEnvironment): CompletionStage<Team> {
        return withAccessMaybe(env) {
            metaStore.getTeam(env.getArgument("id"))
        }
    }

    @GQLFetcher
    fun admin(env: DataFetchingEnvironment): CompletionStage<GQLAdmin> {
        return withAccess(env) {
            Single.just(admin)
        }
    }

    // Tricking the framework here...
    @GQLFetcher
    fun globalStats(env:DataFetchingEnvironment): CompletionStage<Boolean> {
        return withOptionalAccess(env) { Single.just(true) }
    }

    @GQLFetcher
    fun announcement(env: DataFetchingEnvironment): CompletionStage<Announcement> {
        return withOptionalAccessMaybe(env) {
            metaStore.getAnnouncement(env.getArgument("id"))
        }
    }

    @GQLFetcher
    fun announcements(env: DataFetchingEnvironment): CompletionStage<GraphQLPage<Announcement>> {
        return withOptionalAccess(env) {
            val filter = env.getFilter()
            metaStore.queryAnnouncements(
                filter = filter,
                limit = limitFrom(env),
                cursor = cursorFrom(env),
                sort = mapOf(AnnouncementField.TIMESTAMP to Ordering.desc)
            ).map { GraphQLPage(it.items, it.cursor) { metaStore.countAnnouncements(filter) } }
        }
    }

}
