package idlab.obelisk.services.pub.catalog.types

import graphql.schema.DataFetchingEnvironment
import idlab.obelisk.definitions.DataRange
import idlab.obelisk.definitions.In
import idlab.obelisk.definitions.MetricName
import idlab.obelisk.definitions.PagedResult
import idlab.obelisk.definitions.catalog.AccessManager
import idlab.obelisk.definitions.catalog.Dataset
import idlab.obelisk.definitions.catalog.MetaStore
import idlab.obelisk.definitions.catalog.User
import idlab.obelisk.definitions.catalog.codegen.DatasetField
import idlab.obelisk.definitions.data.DataStore
import idlab.obelisk.definitions.data.MetaField
import idlab.obelisk.definitions.data.MetaQuery
import idlab.obelisk.services.pub.catalog.impl.GraphQLPage
import idlab.obelisk.services.pub.catalog.types.util.GQLFetcher
import idlab.obelisk.services.pub.catalog.types.util.GQLType
import idlab.obelisk.services.pub.catalog.types.util.Operations
import idlab.obelisk.utils.service.utils.unpage
import io.reactivex.Single
import java.util.concurrent.CompletionStage
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
@GQLType("MetricId")
class GQLMetricId @Inject constructor(
    private val dataStore: DataStore,
    private val metaStore: MetaStore,
    accessManager: AccessManager
) : Operations(accessManager) {

    @GQLFetcher
    fun id(env: DataFetchingEnvironment): CompletionStage<String> {
        return withAccess(env) {
            Single.just(env.getSource<MetricId>().id)
        }
    }

    @GQLFetcher
    fun datasets(env: DataFetchingEnvironment): CompletionStage<GraphQLPage<Dataset>> {
        return withAccess(env) {
            val metricId = env.getSource<MetricId>()
            val q = MetaQuery(
                dataRange = DataRange(
                    datasets = metricId.user.datasetMemberships.map { it.datasetId }, metrics = listOf(
                        MetricName(metricId.id)
                    )
                ),
                fields = listOf(MetaField.dataset),
            )

            unpage { cursor -> dataStore.getMetadata(q.copy(cursor = cursor)) }
                .toList()
                .flatMap { results ->
                    val filter = In(DatasetField.ID, results.mapNotNull { it.dataset }.toSet())
                    metaStore.queryDatasets(filter, limitFrom(env), cursorFrom(env))
                        .map { pagedResult ->
                            GraphQLPage(
                                pagedResult.items,
                                pagedResult.cursor
                            ) { metaStore.countDataExports(filter) }
                        }
                }
        }
    }

}

data class MetricId(val id: String, val user: User)