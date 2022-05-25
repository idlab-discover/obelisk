package idlab.obelisk.services.pub.catalog.types

import graphql.schema.DataFetchingEnvironment
import idlab.obelisk.definitions.*
import idlab.obelisk.definitions.catalog.AccessManager
import idlab.obelisk.definitions.catalog.MetaStore
import idlab.obelisk.definitions.catalog.Metric
import idlab.obelisk.definitions.catalog.Thing
import idlab.obelisk.definitions.catalog.codegen.ThingField
import idlab.obelisk.definitions.data.DataStore
import idlab.obelisk.definitions.data.IndexField
import idlab.obelisk.definitions.data.MetaField
import idlab.obelisk.definitions.data.MetaQuery
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
@GQLType("Metric")
class GQLMetric @Inject constructor(
    private val dataStore: DataStore,
    private val metaStore: MetaStore,
    accessManager: AccessManager
) : Operations(accessManager) {

    @GQLFetcher
    fun id(env: DataFetchingEnvironment): CompletionStage<String> {
        return withAccess(env) {
            val metric = env.getSource<Metric>()
            Single.just(metric.name.getFullyQualifiedId())
        }
    }

    @GQLFetcher
    fun things(env: DataFetchingEnvironment): CompletionStage<GraphQLPage<Thing>> {
        return withAccess(env) { token ->
            val metric = env.getSource<Metric>()
            val q = MetaQuery(
                dataRange = DataRange(listOf(metric.datasetId), listOf(metric.name)),
                fields = listOf(MetaField.source, MetaField.metric),
                filter = And(
                    token.readFilterForDataset(metric.datasetId),
                    env.getFilter()
                )
            )

            dataStore.getMetadata(q.copy(limit = limitFrom(env), cursor = cursorFrom(env)))
                .flatMap { result ->
                    unpage { cursor ->
                        metaStore.queryThings(
                            filter = And(
                                Eq(ThingField.DATASET_ID, metric.datasetId),
                                In(ThingField.SOURCE_ID, result.items.mapNotNull { it.source }.toSet())
                            ), cursor = cursor, limit = LOAD_PAGE_SIZE
                        )
                    }.toList()
                        .map { combineThingsInfo(metric.datasetId, result.items, it) }
                        .map { GraphQLPage(it, result.cursor) { dataStore.countMetadata(q) } }
                }
        }
    }

    @GQLFetcher
    fun lastUpdate(env: DataFetchingEnvironment): CompletionStage<Long> {
        return withAccessMaybe(env) {
            val metric = env.getSource<Metric>()
            val query = MetaQuery(
                dataRange = DataRange(datasets = listOf(metric.datasetId)),
                fields = listOf(MetaField.metric, MetaField.lastUpdate),
                filter = Eq(EventField.metric.toString(), metric.name.getFullyQualifiedId()),
                limit = 1
            )
            dataStore.getMetadata(query).flatMapMaybe { result ->
                result.items.firstOrNull()?.lastUpdate?.let { Maybe.just(it) } ?: Maybe.empty()
            }
        }
    }

    @GQLFetcher
    fun started(env: DataFetchingEnvironment): CompletionStage<Long> {
        return withAccessMaybe(env) {
            val metric = env.getSource<Metric>()
            val query = MetaQuery(
                dataRange = DataRange(datasets = listOf(metric.datasetId)),
                fields = listOf(MetaField.metric, MetaField.started),
                filter = Eq(EventField.metric.toString(), metric.name.getFullyQualifiedId()),
                limit = 1
            )
            dataStore.getMetadata(query).flatMapMaybe { result ->
                result.items.firstOrNull()?.started?.let { Maybe.just(it) } ?: Maybe.empty()
            }
        }
    }
}