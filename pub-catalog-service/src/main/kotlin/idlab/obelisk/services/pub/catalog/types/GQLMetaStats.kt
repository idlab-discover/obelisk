package idlab.obelisk.services.pub.catalog.types

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import graphql.schema.DataFetchingEnvironment
import idlab.obelisk.definitions.DataRange
import idlab.obelisk.definitions.Eq
import idlab.obelisk.definitions.Field
import idlab.obelisk.definitions.Gte
import idlab.obelisk.definitions.catalog.AccessManager
import idlab.obelisk.definitions.catalog.MetaStore
import idlab.obelisk.definitions.catalog.codegen.DataStreamField
import idlab.obelisk.definitions.codegen.DataRangeField
import idlab.obelisk.definitions.data.DataStore
import idlab.obelisk.definitions.data.MetaField
import idlab.obelisk.definitions.data.MetaQuery
import idlab.obelisk.definitions.framework.*
import idlab.obelisk.services.pub.catalog.impl.MetaStats
import idlab.obelisk.services.pub.catalog.impl.RateSeriesMode
import idlab.obelisk.services.pub.catalog.impl.TimeSeries
import idlab.obelisk.services.pub.catalog.types.util.GQLFetcher
import idlab.obelisk.services.pub.catalog.types.util.GQLType
import idlab.obelisk.services.pub.catalog.types.util.Operations
import idlab.obelisk.utils.service.utils.unpage
import io.reactivex.Single
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.concurrent.CompletionStage
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

const val ENV_DATASET_STATS_CACHE_TIME_MS = "DATASET_STATS_CACHE_TIME_MS"
const val DEFAULT_DATASET_STATS_CACHE_TIME_MS = 15 * 1000L // 15 seconds
const val ENV_DATASET_STATS_CACHE_MAX_ENTRIES = "DATASET_STATS_CACHE_MAX_ENTRIES"
const val DEFAULT_DATASET_STATS_CACHE_MAX_ENTRIES = 5000L

@Singleton
@GQLType("MetaStats")
class GQLMetaStats @Inject constructor(
    private val metaStore: MetaStore,
    private val dataStore: DataStore,
    private val monitoringDataProvider: MonitoringDataProvider,
    accessManager: AccessManager,
    config: OblxConfig
) : Operations(accessManager) {

    private val cache = Caffeine.newBuilder().expireAfterWrite(
        config.getLong(ENV_DATASET_STATS_CACHE_TIME_MS, DEFAULT_DATASET_STATS_CACHE_TIME_MS),
        TimeUnit.MILLISECONDS
    ).maximumSize(config.getLong(ENV_DATASET_STATS_CACHE_MAX_ENTRIES, DEFAULT_DATASET_STATS_CACHE_MAX_ENTRIES))
        .build<CacheKey, Any>()

    @GQLFetcher
    fun lastUpdate(env: DataFetchingEnvironment): CompletionStage<Long> {
        return withAccess(env) {
            val parent = env.getSource<MetaStats>()
            Single.just(if (parent.metricInfo.isNotEmpty()) parent.metricInfo.maxOf { it.lastUpdate ?: 0L } else 0L)
        }
    }

    @GQLFetcher
    fun nrOfEvents(env: DataFetchingEnvironment): CompletionStage<Long> {
        return withAccess(env) {
            val parent = env.getSource<MetaStats>()
            Single.just(parent.metricInfo.sumOf { it.count ?: 0 })
        }
    }

    @GQLFetcher
    fun nrOfEventsProjection(env: DataFetchingEnvironment): CompletionStage<Long> {
        return withAccess(env) {
            val parent = env.getSource<MetaStats>()
            unpage { cursor ->
                dataStore.getMetadata(
                    MetaQuery(
                        dataRange = DataRange.fromDatasetId(parent.datasetId),
                        fields = listOf(MetaField.metric_type, MetaField.avgIngestRate),
                        filter = Gte(MetaField.lastUpdate, Instant.now().minus(10, ChronoUnit.DAYS).toEpochMilli()),
                        cursor = cursor
                    )
                )
            }.toList().map { result ->
                (parent.metricInfo.sumOf {
                    it.count ?: 0L
                } + (365 * 24 * 60 * 60 * result.sumOf { it.avgIngestRate ?: 0.0 })).toLong()
            }
        }
    }

    @GQLFetcher
    fun nrOfMetrics(env: DataFetchingEnvironment): CompletionStage<Int> {
        return withAccess(env) {
            val parent = env.getSource<MetaStats>()
            Single.just(parent.metricInfo.map { it.metricName()!!.getFullyQualifiedId() }.distinct().count())
        }
    }

    @GQLFetcher
    fun nrOfStreams(env: DataFetchingEnvironment): CompletionStage<Int> {
        return withAccess(env) {
            val parent = env.getSource<MetaStats>()
            cache.getOrLoad(CacheKey(parent.datasetId, "nrOfStreams")) {
                metaStore.countDataStreams(
                    filter = Eq(
                        Field(DataStreamField.DATA_RANGE, DataRangeField.DATASETS),
                        parent.datasetId
                    )
                )
                    .map { it.toInt() }
            }
        }
    }

    @GQLFetcher
    fun approxSizeBytes(env: DataFetchingEnvironment): CompletionStage<Long> {
        return withAccess(env) {
            val parent = env.getSource<MetaStats>()
            cache.getOrLoad(CacheKey(parent.datasetId, "approxSizeBytes")) {
                dataStore.getStorageInfo()
                    .map { storageInfo ->
                        parent.metricInfo.groupBy { it.metricType!! }.map { result ->
                            result.value.sumOf { it.count!! * (storageInfo.averageSizeForMetricType(it.metricType!!) + storageInfo.averageSizeForMetaFields()) }
                        }.sum()
                    }
            }
        }
    }

    @GQLFetcher
    fun approxSizeBytesProjection(env: DataFetchingEnvironment): CompletionStage<Long> {
        return withAccess(env) {
            val parent = env.getSource<MetaStats>()
            cache.getOrLoad(CacheKey(parent.datasetId, "approxSizeBytesProjection")) {
                dataStore.getStorageInfo().flatMap { storageInfo ->
                    unpage { cursor ->
                        dataStore.getMetadata(
                            MetaQuery(
                                dataRange = DataRange.fromDatasetId(parent.datasetId),
                                fields = listOf(MetaField.metric_type, MetaField.avgIngestRate),
                                filter = Gte(
                                    MetaField.lastUpdate,
                                    Instant.now().minus(10, ChronoUnit.DAYS).toEpochMilli()
                                ),
                                cursor = cursor
                            )
                        )
                    }.toList()
                        .map { result ->
                            val output = result.groupBy { it.metricType!! }
                                .mapValues { value -> value.value.sumOf { it.avgIngestRate ?: 0.0 } }
                            output
                        }
                        .map { ratePerType ->
                            val projectedBytes = (365 * 24 * 60 * 60 * (parent.metricInfo.sumOf {
                                (storageInfo.averageSizeForMetricType(
                                    it.metricType!!
                                ) + storageInfo.averageSizeForMetaFields()) * ratePerType.getOrDefault(
                                    it.metricType!!,
                                    0.0
                                )
                            })).toLong()

                            parent.metricInfo.groupBy { it.metricType!! }.map { result ->
                                result.value.sumOf { it.count!! * (storageInfo.averageSizeForMetricType(it.metricType!!) + storageInfo.averageSizeForMetaFields()) }
                            }.sum() + projectedBytes
                        }
                }
            }
        }
    }

    @GQLFetcher
    fun ingestApiRequestRate(env: DataFetchingEnvironment): CompletionStage<TimeSeries> {
        return withAccess(env) {
            timeseriesHelper(
                operationId = "ingestApiRequestRate",
                env = env,
                requestor = { datasetId, from, to, duration ->
                    monitoringDataProvider.countIngestRequests(from, to, duration, datasetId).map { listOf(it) }
                }
            )
        }
    }

    @GQLFetcher
    fun ingestedEventsRate(env: DataFetchingEnvironment): CompletionStage<TimeSeries> {
        return withAccess(env) {
            timeseriesHelper(
                operationId = "ingestedEventsRate",
                env = env,
                requestor = { datasetId, from, to, duration ->
                    monitoringDataProvider.countIngestedEvents(from, to, duration, datasetId).map { listOf(it) }
                }
            )
        }
    }

    @GQLFetcher
    fun eventsQueryApiRequestRate(env: DataFetchingEnvironment): CompletionStage<TimeSeries> {
        return withAccess(env) {
            timeseriesHelper(
                operationId = "eventsQueryApiRequestRate",
                env = env,
                requestor = { datasetId, from, to, duration ->
                    monitoringDataProvider.countQueryRequests(from, to, duration, datasetId, QueryType.EVENTS)
                }
            )
        }
    }

    @GQLFetcher
    fun statsQueryApiRequestRate(env: DataFetchingEnvironment): CompletionStage<TimeSeries> {
        return withAccess(env) {
            timeseriesHelper(
                operationId = "statsQueryApiRequestRate",
                env = env,
                requestor = { datasetId, from, to, duration ->
                    monitoringDataProvider.countQueryRequests(from, to, duration, datasetId, QueryType.STATS)
                }
            )
        }
    }

    @GQLFetcher
    fun consumedEventsRate(env: DataFetchingEnvironment): CompletionStage<TimeSeries> {
        return withAccess(env) {
            timeseriesHelper(
                operationId = "consumedEventsRate",
                env = env,
                requestor = { datasetId, from, to, duration ->
                    monitoringDataProvider.countConsumedEvents(from, to, duration, datasetId)
                }
            )
        }
    }

    @GQLFetcher
    fun queriesConsumedEventsRate(env: DataFetchingEnvironment): CompletionStage<TimeSeries> {
        return withAccess(env) {
            timeseriesHelper(
                operationId = "queriesConsumedEventsRate",
                env = env,
                requestor = { datasetId, from, to, duration ->
                    monitoringDataProvider.countConsumedEvents(from, to, duration, datasetId, ConsumptionType.QUERIES)
                }
            )
        }
    }

    @GQLFetcher
    fun streamingConsumedEventsRate(env: DataFetchingEnvironment): CompletionStage<TimeSeries> {
        return withAccess(env) {
            timeseriesHelper(
                operationId = "streamingConsumedEventsRate",
                env = env,
                requestor = { datasetId, from, to, duration ->
                    monitoringDataProvider.countConsumedEvents(from, to, duration, datasetId, ConsumptionType.STREAMING)
                }
            )
        }
    }

    @GQLFetcher
    fun activeStreams(env: DataFetchingEnvironment): CompletionStage<TimeSeries> {
        return withAccess(env) {
            timeseriesHelper(
                operationId = "activeStreams",
                env = env,
                requestor = { datasetId, from, to, duration ->
                    monitoringDataProvider.countActiveDataStreams(from, to, duration, datasetId).map { listOf(it) }
                }
            )
        }
    }

    private fun timeseriesHelper(
        operationId: String,
        env: DataFetchingEnvironment,
        requestor: (String, Long, Long, MonitoringDuration) -> Single<List<MonitoringTimeSeries>>,
    ): Single<TimeSeries> {
        val parent = env.getSource<MetaStats>()
        val mode = RateSeriesMode.valueOf(env.getArgument("mode"))
        val to = Instant.now()
        val from = to.minusMillis(mode.offsetMs)
        return cache.getOrLoad(CacheKey(parent.datasetId, operationId, mode)) {
            requestor.invoke(parent.datasetId, from.toEpochMilli(), to.toEpochMilli(), mode.monitoringDuration)
        }.map { sumMonitoringTimeSeries(it).values }
    }

}

internal data class CacheKey(val datasetId: String, val attribute: String, val mode: RateSeriesMode? = null)

internal fun <T> Cache<CacheKey, Any>.getOrLoad(key: CacheKey, loader: () -> Single<T>): Single<T> {
    return this.getIfPresent(key)?.let { Single.just(it as T) } ?: loader.invoke().doOnSuccess { result ->
        this.put(key, result as Any)
    }
}