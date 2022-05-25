package idlab.obelisk.services.pub.catalog.types

import graphql.schema.DataFetchingEnvironment
import idlab.obelisk.definitions.DataRange
import idlab.obelisk.definitions.Gte
import idlab.obelisk.definitions.MetricName
import idlab.obelisk.definitions.SELECT_ALL
import idlab.obelisk.definitions.catalog.AccessManager
import idlab.obelisk.definitions.catalog.MetaStore
import idlab.obelisk.definitions.data.DataStore
import idlab.obelisk.definitions.data.MetaField
import idlab.obelisk.definitions.data.MetaQuery
import idlab.obelisk.definitions.framework.MonitoringDataProvider
import idlab.obelisk.definitions.framework.MonitoringDuration
import idlab.obelisk.definitions.framework.OblxConfig
import idlab.obelisk.definitions.framework.sumMonitoringTimeSeries
import idlab.obelisk.services.pub.catalog.impl.TimeSeries
import idlab.obelisk.services.pub.catalog.impl.logger
import idlab.obelisk.services.pub.catalog.types.util.GQLFetcher
import idlab.obelisk.services.pub.catalog.types.util.GQLType
import idlab.obelisk.services.pub.catalog.types.util.Operations
import idlab.obelisk.utils.service.utils.unpage
import io.reactivex.Completable
import io.reactivex.Flowable
import io.reactivex.Single
import io.reactivex.rxkotlin.subscribeBy
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.concurrent.CompletionStage
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

const val ENV_GLOBAL_STATS_CALC_PERIOD_MS = "GLOBAL_STATS_CALC_PERIOD"
const val ENV_GLOBAL_IO_WINDOW_MS = "GLOBAL_IO_WINDOW_MS"
const val ENV_GLOBAL_IO_TICK_MS = "GLOBAL_IO_TICK_MS"
const val DEFAULT_GLOBAL_STATS_CALC_PERIOD_MS = 60 * 1000L // Every minute
const val DEFAULT_GLOBAL_IO_WINDOW_MS = 60 * 60 * 1000L // 1 Hour
const val DEFAULT_GLOBAL_IO_TICK_MS = 60 * 1000L // 1 minute

@Singleton
@GQLType("GlobalMetaStats")
class GQLGlobalStats @Inject constructor(
    accessManager: AccessManager,
    private val dataStore: DataStore,
    private val metaStore: MetaStore,
    private val monitoringDataProvider: MonitoringDataProvider,
    config: OblxConfig
) :
    Operations(accessManager) {

    private val ioWindowMs = config.getLong(ENV_GLOBAL_IO_WINDOW_MS, DEFAULT_GLOBAL_IO_WINDOW_MS)
    private val tickMs = config.getLong(ENV_GLOBAL_IO_TICK_MS, DEFAULT_GLOBAL_IO_TICK_MS)
    private val stats = GlobalStats()

    init {
        calculateStats(config.getLong(ENV_GLOBAL_STATS_CALC_PERIOD_MS, DEFAULT_GLOBAL_STATS_CALC_PERIOD_MS))
    }

    private fun calculateStats(periodMs: Long) {
        Flowable.interval(0, periodMs, TimeUnit.MILLISECONDS)
            .concatMapCompletable {
                val toMs = Instant.now()
                val fromMs = toMs.minusMillis(ioWindowMs)

                Completable
                    .mergeArrayDelayError(
                        metaStore.countDatasets(SELECT_ALL).doOnSuccess { stats.nrOfDatasets = it }.ignoreElement(),
                        countMetrics().doOnSuccess { stats.nrOfMetrics = it }.ignoreElement(),
                        metaStore.countUsers(SELECT_ALL).doOnSuccess { stats.nrOfUsers = it }.ignoreElement(),
                        metaStore.countClients(SELECT_ALL).doOnSuccess { stats.nrOfClients = it }.ignoreElement(),
                        dataStore.getStorageInfo().doOnSuccess {
                            stats.totalSizeBytes = it.totalBytes
                            stats.nrOfEvents = it.totalEvents
                        }.ignoreElement(),
                        calcProjections().doOnSuccess {
                            stats.nrOfEventsProjection = it.first.toLong()
                            stats.totalSizeBytesProjection = it.second.toLong()
                        }.ignoreElement(),
                        monitoringDataProvider.countIngestedEvents(
                            fromMs.toEpochMilli(),
                            toMs.toEpochMilli(),
                            MonitoringDuration.from(tickMs, TimeUnit.MILLISECONDS)
                        )
                            .doOnSuccess {
                                stats.ingestedEventsRate = it.values
                            }
                            .ignoreElement(),
                        monitoringDataProvider.countConsumedEvents(
                            fromMs.toEpochMilli(),
                            toMs.toEpochMilli(),
                            MonitoringDuration.from(tickMs, TimeUnit.MILLISECONDS)
                        )
                            .doOnSuccess { result ->
                                val summedResult = sumMonitoringTimeSeries(result)
                                stats.consumedEventsRate = summedResult.values
                            }
                            .ignoreElement()
                    )
                    .onErrorComplete { err ->
                        // Log and then ignore error
                        logger.warn(err) { "Error while pre-calculating global stats!" }
                        true
                    }
            }
            .doFinally {
                // Always reschedule the flow (if the loop would exit for some reason)
                calculateStats(periodMs)
            }
            .subscribeBy(
                onError = { err -> logger.warn(err) { "Uncaught exception in calculateStats Flow" } }
            )
    }

    private fun countMetrics(): Single<Long> {
        return dataStore.countMetadata(
            MetaQuery(
                dataRange = DataRange(emptyList(), MetricName.wildcard()),
                fields = listOf(MetaField.metric)
            )
        )
    }

    // Returns pair of projected number of events and projected total byte size
    private fun calcProjections(): Single<Pair<Double, Double>> {
        return dataStore.getStorageInfo()
            .zipWith(unpage { cursor ->
                dataStore.getMetadata(
                    MetaQuery(
                        dataRange = DataRange.allData,
                        fields = listOf(MetaField.metric_type, MetaField.avgIngestRate),
                        filter = Gte(MetaField.lastUpdate, Instant.now().minus(10, ChronoUnit.DAYS).toEpochMilli()),
                        cursor = cursor
                    )
                )
            }.toList()) { storageInfo, result ->
                Pair(
                    storageInfo.totalEvents + (365 * 24 * 60 * 60 * result.sumOf { it.avgIngestRate ?: 0.0 }),
                    storageInfo.totalBytes + (365 * 24 * 60 * 60 * (result.sumOf {
                        (storageInfo.averageSizeForMetricType(
                            it.metricType!!
                        ) + storageInfo.averageSizeForMetaFields()) * it.avgIngestRate!!
                    }))
                )
            }
    }

    @GQLFetcher
    fun nrOfDatasets(env: DataFetchingEnvironment): CompletionStage<Int> {
        return withOptionalAccess(env) {
            Single.just(stats.nrOfDatasets.toInt())
        }
    }

    @GQLFetcher
    fun nrOfMetrics(env: DataFetchingEnvironment): CompletionStage<Long> {
        return withOptionalAccess(env) {
            Single.just(stats.nrOfMetrics)
        }
    }

    @GQLFetcher
    fun nrOfUsers(env: DataFetchingEnvironment): CompletionStage<Int> {
        return withOptionalAccess(env) {
            Single.just(stats.nrOfUsers.toInt())
        }
    }

    @GQLFetcher
    fun nrOfClients(env: DataFetchingEnvironment): CompletionStage<Int> {
        return withOptionalAccess(env) {
            Single.just(stats.nrOfClients.toInt())
        }
    }

    @GQLFetcher
    fun nrOfEvents(env: DataFetchingEnvironment): CompletionStage<Long> {
        return withOptionalAccess(env) {
            Single.just(stats.nrOfEvents)
        }
    }

    @GQLFetcher
    fun nrOfEventsProjection(env: DataFetchingEnvironment): CompletionStage<Long> {
        return withOptionalAccess(env) {
            Single.just(stats.nrOfEventsProjection)
        }
    }

    @GQLFetcher
    fun totalSizeBytes(env: DataFetchingEnvironment): CompletionStage<Long> {
        return withOptionalAccess(env) {
            Single.just(stats.totalSizeBytes)
        }
    }

    @GQLFetcher
    fun totalSizeBytesProjection(env: DataFetchingEnvironment): CompletionStage<Long> {
        return withOptionalAccess(env) {
            Single.just(stats.totalSizeBytesProjection)
        }
    }

    @GQLFetcher
    fun ingestedEventsRate(env: DataFetchingEnvironment): CompletionStage<TimeSeries> {
        return withOptionalAccess(env) {
            Single.just(stats.ingestedEventsRate)
        }
    }

    @GQLFetcher
    fun consumedEventsRate(env: DataFetchingEnvironment): CompletionStage<TimeSeries> {
        return withOptionalAccess(env) {
            Single.just(stats.consumedEventsRate)
        }
    }
}

private data class GlobalStats(
    var nrOfDatasets: Long = 0,
    var nrOfMetrics: Long = 0,
    var nrOfUsers: Long = 0,
    var nrOfClients: Long = 0,
    var nrOfEvents: Long = 0,
    var nrOfEventsProjection: Long = 0,
    var totalSizeBytes: Long = 0,
    var totalSizeBytesProjection: Long = 0,
    var ingestedEventsRate: TimeSeries = listOf(),
    var consumedEventsRate: TimeSeries = listOf()
)