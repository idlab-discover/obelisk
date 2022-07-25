package idlab.obelisk.services.internal.streamer

import idlab.obelisk.definitions.catalog.MetaStore
import idlab.obelisk.definitions.data.MetricEvent
import idlab.obelisk.definitions.framework.OblxConfig
import idlab.obelisk.definitions.framework.OblxService
import idlab.obelisk.plugins.metastore.mongo.MongoDBMetaStoreModule
import idlab.obelisk.pulsar.utils.*
import idlab.obelisk.utils.service.OblxBaseModule
import idlab.obelisk.utils.service.OblxLauncher
import idlab.obelisk.utils.service.instrumentation.IdToNameMap
import idlab.obelisk.utils.service.instrumentation.TargetType
import idlab.obelisk.utils.service.reactive.flatMap
import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.Tag
import io.micrometer.core.instrument.Tags
import io.reactivex.BackpressureStrategy
import io.reactivex.Completable
import io.reactivex.rxkotlin.subscribeBy
import io.vertx.micrometer.backends.BackendRegistries
import io.vertx.reactivex.core.Vertx
import mu.KotlinLogging
import org.apache.pulsar.client.api.*
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import kotlin.system.exitProcess

const val EVENTS_MAX_AGE_MINUTES = "EVENTS_MAX_AGE_MINUTES"
const val EVENTS_MAX_AGE_MINUTES_DEFAULT = 24 * 60 // Events more than a day old will not be streamed!
const val MAX_CACHED_PRODUCERS = "MAX_CACHED_PRODUCERS"
const val MAX_CACHED_PRODUCERS_DEFAULT = 10000L
const val EXPIRE_PRODUCER_AFTER_MINUTES = "EXPIRE_PRODUCER_AFTER_MINUTES"
const val EXPIRE_PRODUCER_AFTER_MINUTES_DEFAULT = 5L
const val MAX_PRODUCE_CONCURRENCY = "MAX_PRODUCE_CONCURRENCY"
const val MAX_PRODUCE_CONCURRENCY_DEFAULT = 128

const val STALE_EVENTS_METRIC = "oblx.streamer.stale.events"
const val SEND_FAILURES_MERIC = "oblx.streamer.send.failures"
const val ACK_FAILURES_METRIC = "oblx.streamer.ack.failures"
const val INCOMING_EVENTS_METRIC = "oblx.streamer.incoming.events"
const val OUTGOING_EVENTS_METRIC = "oblx.streamer.outgoing.events"

fun main(args: Array<String>) {
    OblxLauncher.with(OblxBaseModule(), PulsarModule(), MongoDBMetaStoreModule())
        .bootstrap(DatasetStreamerService::class.java)
}

class DatasetStreamerService @Inject constructor(
    private val config: OblxConfig,
    private val pulsarClient: PulsarClient,
    metaStore: MetaStore,
    private val vertx: Vertx
) : OblxService {

    private var debugEventCount = 0
    private val debugDatasets = mutableSetOf<String>()

    private val microMeterRegistry = BackendRegistries.getDefaultNow()
    private val streamerSendFailures = Counter
        .builder(SEND_FAILURES_MERIC)
        .description("Counts number of times sending an event to Pulsar resulted in a failure.")
        .register(microMeterRegistry)
    private val streamerAckFailures = Counter
        .builder(ACK_FAILURES_METRIC)
        .description("Counts number of times Pulsar acks resulted in a failure.")
        .register(microMeterRegistry)

    private val logger = KotlinLogging.logger { }
    private val liveTimestampThresholdMinutes =
        config.getInteger(EVENTS_MAX_AGE_MINUTES, EVENTS_MAX_AGE_MINUTES_DEFAULT)
    private val maxProduceConcurrency = config.getInteger(MAX_PRODUCE_CONCURRENCY, MAX_PRODUCE_CONCURRENCY_DEFAULT)
    private val datasetIdToNameMap = IdToNameMap(metaStore, TargetType.DATASET)

    private val producerCache = RxPulsarProducerCache<MetricEvent>(
        pulsarClient,
        config.hostname(),
        config.getLong(MAX_CACHED_PRODUCERS, MAX_CACHED_PRODUCERS_DEFAULT),
        config.getLong(EXPIRE_PRODUCER_AFTER_MINUTES, EXPIRE_PRODUCER_AFTER_MINUTES_DEFAULT),
        TimeUnit.MINUTES
    ) { it.configureForHighThroughput() }
    private val producerSchema = Schema.JSON(MetricEvent::class.java)

    override fun start(): Completable {
        vertx.setPeriodic(5000) {
            logger.debug { "Streamed $debugEventCount events for datasets: $debugDatasets" }
            debugEventCount = 0
            debugDatasets.clear()
        }

        pulsarClient.newConsumer(Schema.JSON(MetricEvent::class.java))
            .subscriptionName("${config.pulsarDatasetStreamerSubscriber}-fo")
            .subscriptionType(SubscriptionType.Failover)
            .subscriptionInitialPosition(SubscriptionInitialPosition.Earliest)
            .topic(config.pulsarMetricEventsTopic)
            .rxSubscribe()
            .flatMapCompletable { consumer ->
                consumer.toFlowable(BackpressureStrategy.BUFFER)
                    .flatMapCompletable({ (_, event) ->
                        (if (event.value.timestamp > currentLowerTimestampBoundMus()) {
                            countEvent(INCOMING_EVENTS_METRIC, event)
                            val targetTopic = config.pulsarDatasetTopic(event.value.dataset!!)
                            producerCache.rxGet(producerSchema, targetTopic)
                                .flatMap { producer ->
                                    producer.rxSend(event.value).doOnError { streamerSendFailures.increment() }
                                }
                                .doOnSuccess { countEvent(OUTGOING_EVENTS_METRIC, event) }
                                .ignoreElement()
                        } else {
                            countEvent(STALE_EVENTS_METRIC, event)
                            Completable.complete()
                        })
                            .flatMap {
                                // Try to ack
                                consumer.rxAcknowledge(event)
                                    .doOnError { streamerAckFailures.increment() }
                                    .onErrorComplete()
                            }
                    }, false, maxProduceConcurrency)
            }
            .subscribeBy(
                onComplete = {
                    logger.warn { "Consuming flow completed (unexpectedly)..." }
                    exitProcess(1)
                },
                onError = {
                    logger.error(it) { "Unrecoverable error in dataset-streamer flow!" }
                    exitProcess(1)
                })
        return datasetIdToNameMap.init()
    }

    private fun currentLowerTimestampBoundMus(): Long {
        return TimeUnit.MICROSECONDS.convert(
            System.currentTimeMillis() - TimeUnit.MILLISECONDS.convert(
                liveTimestampThresholdMinutes.toLong(),
                TimeUnit.MINUTES
            ), TimeUnit.MILLISECONDS
        )
    }

    private fun countEvent(metricName: String, event: Message<MetricEvent>) {
        debugEventCount++
        debugDatasets.add(event.value.dataset ?: "")
        microMeterRegistry.counter(
            metricName,
            Tags.of(
                mutableListOf(
                    Tag.of("datasetId", event.value.dataset!!),
                    Tag.of("datasetName", event.value.dataset?.let { datasetIdToNameMap.getName(it) } ?: "")
                )
            )
        ).increment()
    }

}
