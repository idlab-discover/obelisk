package idlab.obelisk.services.pub.catalog.types

import graphql.schema.DataFetchingEnvironment
import idlab.obelisk.definitions.*
import idlab.obelisk.definitions.catalog.AccessManager
import idlab.obelisk.definitions.catalog.MetaStore
import idlab.obelisk.definitions.catalog.Metric
import idlab.obelisk.definitions.catalog.Thing
import idlab.obelisk.definitions.catalog.codegen.MetricField
import idlab.obelisk.definitions.data.*
import idlab.obelisk.services.pub.catalog.impl.GraphQLPage
import idlab.obelisk.services.pub.catalog.impl.LOAD_PAGE_SIZE
import idlab.obelisk.services.pub.catalog.impl.getFilter
import idlab.obelisk.services.pub.catalog.types.util.GQLFetcher
import idlab.obelisk.services.pub.catalog.types.util.GQLType
import idlab.obelisk.services.pub.catalog.types.util.Operations
import idlab.obelisk.services.pub.catalog.types.util.readFilterForDataset
import idlab.obelisk.utils.service.utils.unpage
import io.reactivex.Maybe
import io.reactivex.Single
import java.util.concurrent.CompletionStage
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
@GQLType("Thing")
class GQLThing @Inject constructor(
    accessManager: AccessManager,
    private val dataStore: DataStore,
    private val metaStore: MetaStore
) : Operations(accessManager) {

    @GQLFetcher
    fun id(env: DataFetchingEnvironment): CompletionStage<String> {
        return withAccess(env) {
            val thing = env.getSource<Thing>()
            Single.just(thing.sourceId)
        }
    }

    @GQLFetcher
    fun metrics(env: DataFetchingEnvironment): CompletionStage<GraphQLPage<Metric>> {
        return withAccess(env) { token ->
            val thing = env.getSource<Thing>()
            val q = MetaQuery(
                dataRange = DataRange(listOf(thing.datasetId)),
                fields = listOf(MetaField.metric, MetaField.source),
                filter = And(
                    token.readFilterForDataset(thing.datasetId),
                    Eq(IndexField.source, thing.sourceId),
                    env.getFilter()
                )
            )

            dataStore.getMetadata(q.copy(limit = limitFrom(env), cursor = cursorFrom(env)))
                .flatMap { result ->
                    unpage { cursor ->
                        metaStore.queryMetrics(
                            filter = And(
                                Eq(MetricField.DATASET_ID, thing.datasetId),
                                In(
                                    "fqMetricId",
                                    result.items.mapNotNull { it.metricName() }.map { it.getFullyQualifiedId() }.toSet()
                                )
                            ),
                            cursor = cursor,
                            limit = LOAD_PAGE_SIZE
                        )
                    }.toList()
                        .map { combineMetricsInfo(thing.datasetId, result.items, it) }
                        .map { GraphQLPage(it, result.cursor) { dataStore.countMetadata(q) } }
                }
        }
    }

    @GQLFetcher
    fun started(env: DataFetchingEnvironment): CompletionStage<Long> {
        return withAccessMaybe(env) {
            val thing = env.getSource<Thing>()
            val query = MetaQuery(
                dataRange = DataRange(datasets = listOf(thing.datasetId)),
                fields = listOf(MetaField.source, MetaField.started),
                filter = Eq(EventField.source.toString(), thing.sourceId),
                limit = 1
            )
            dataStore.getMetadata(query).flatMapMaybe { result ->
                result.items.firstOrNull()?.started?.let { Maybe.just(it) } ?: Maybe.empty()
            }
        }
    }

    @GQLFetcher
    fun lastUpdate(env: DataFetchingEnvironment): CompletionStage<Long> {
        return withAccessMaybe(env) {
            val thing = env.getSource<Thing>()
            val query = MetaQuery(
                dataRange = DataRange(datasets = listOf(thing.datasetId)),
                fields = listOf(MetaField.source, MetaField.lastUpdate),
                filter = Eq(EventField.source.toString(), thing.sourceId),
                limit = 1
            )
            dataStore.getMetadata(query).flatMapMaybe { result ->
                result.items.firstOrNull()?.lastUpdate?.let { Maybe.just(it) } ?: Maybe.empty()
            }
        }
    }

}