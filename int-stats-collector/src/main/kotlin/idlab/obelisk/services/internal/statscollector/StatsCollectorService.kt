package idlab.obelisk.services.internal.statscollector

import idlab.obelisk.definitions.DataRange
import idlab.obelisk.definitions.catalog.MetaStore
import idlab.obelisk.definitions.data.DataStore
import idlab.obelisk.definitions.data.MetaData
import idlab.obelisk.definitions.data.MetaField
import idlab.obelisk.definitions.data.MetaQuery
import idlab.obelisk.definitions.framework.OblxConfig
import idlab.obelisk.definitions.framework.OblxService
import idlab.obelisk.plugins.datastore.clickhouse.ClickhouseDataStoreModule
import idlab.obelisk.plugins.metastore.mongo.MongoDBMetaStoreModule
import idlab.obelisk.utils.service.OblxBaseModule
import idlab.obelisk.utils.service.OblxLauncher
import idlab.obelisk.utils.service.instrumentation.IdToNameMap
import idlab.obelisk.utils.service.instrumentation.TargetType
import idlab.obelisk.utils.service.reactive.flatMapPublisher
import idlab.obelisk.utils.service.utils.unpage
import io.micrometer.core.instrument.MultiGauge
import io.micrometer.core.instrument.Tag
import io.micrometer.core.instrument.Tags
import io.reactivex.Completable
import io.reactivex.Flowable
import io.reactivex.rxkotlin.subscribeBy
import io.vertx.micrometer.backends.BackendRegistries
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import kotlin.system.exitProcess

const val ENV_STATS_COLLECTION_PERIOD_MS = "STATS_COLLECTION_PERIOD_MS"
const val DEFAULT_STATS_COLLECTION_PERIOD_MS = 15 * 1000 * 60L // 15 minutes

fun main(args: Array<String>) {
    OblxLauncher.with(
        OblxBaseModule(),
        ClickhouseDataStoreModule(),
        MongoDBMetaStoreModule()
    )
        .bootstrap(StatsCollectorService::class.java)
}

class StatsCollectorService @Inject constructor(
    private val config: OblxConfig,
    metaStore: MetaStore,
    private val dataStore: DataStore
) :
    OblxService {

    private val microMeterRegistry = BackendRegistries.getDefaultNow()
    private val nrOfEvents = MultiGauge
        .builder("oblx.ch.events")
        .description("Number of events in Clickhouse for Obelisk")
        .register(microMeterRegistry)
    private val compressedStorage = MultiGauge
        .builder("oblx.ch.compressed.storage")
        .baseUnit("bytes")
        .description("Compressed storage size of events in Clickhouse for Obelisk")
        .register(microMeterRegistry)
    private val uncompressedStorage = MultiGauge
        .builder("oblx.ch.uncompressed.storage")
        .baseUnit("bytes")
        .description("Uncompressed storage size of events in Clickhouse for Obelisk")
        .register(microMeterRegistry)

    private val datasetIdsToNames =
        IdToNameMap(metaStore, TargetType.DATASET)

    override fun start(): Completable {
        datasetIdsToNames.init()
            .flatMapPublisher {
                Flowable.interval(
                    0L,
                    config.getLong(
                        ENV_STATS_COLLECTION_PERIOD_MS,
                        DEFAULT_STATS_COLLECTION_PERIOD_MS
                    ), TimeUnit.MILLISECONDS
                )
            }
            .flatMapSingle {
                dataStore.getStorageInfo(compressed = false)
                    .zipWith(dataStore.getStorageInfo(compressed = true)) { uncompressedInfo, compressedInfo ->
                        Pair(uncompressedInfo, compressedInfo)
                    }
                    .flatMap { (uncompressedInfo, compressedInfo) ->
                        unpage { cursor ->
                            dataStore.getMetadata(
                                MetaQuery(
                                    dataRange = DataRange(datasets = emptyList()),
                                    fields = listOf(MetaField.dataset, MetaField.metric_type, MetaField.count),
                                    cursor = cursor,
                                    limit = 5000
                                )
                            )
                        }.toList()
                            .map { Triple(uncompressedInfo, compressedInfo, it) }
                    }
            }
            .subscribeBy(
                onNext = { (uncompressedInfo, compressedInfo, result) ->
                    nrOfEvents.register(result.map { record ->
                        MultiGauge.Row.of(tagsFor(record), record.count!!)
                    })
                    compressedStorage.register(result.map { record ->
                        MultiGauge.Row.of(
                            tagsFor(record),
                            (record.count!! * (compressedInfo.averageSizeForMetaFields() + compressedInfo.averageSizeForMetricType(
                                record.metricType!!
                            )))
                        )
                    })
                    uncompressedStorage.register(result.map { record ->
                        MultiGauge.Row.of(
                            tagsFor(record),
                            (record.count!! * (uncompressedInfo.averageSizeForMetaFields() + compressedInfo.averageSizeForMetricType(
                                record.metricType!!
                            )))
                        )
                    })
                },
                onError = {
                    it.printStackTrace()
                    exitProcess(1)
                }
            )

        return Completable.complete()
    }

    private fun tagsFor(record: MetaData): Tags {
        return Tags.of(
            mutableListOf(
                Tag.of("datasetId", record.dataset!!),
                Tag.of("datasetName", datasetIdsToNames.getName(record.dataset!!) ?: ""),
                Tag.of("metricType", record.metricType.toString())
            )
        )
    }
}