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
import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.Tag
import io.micrometer.core.instrument.Tags
import io.reactivex.BackpressureStrategy
import io.reactivex.Completable
import io.reactivex.Single
import io.reactivex.rxkotlin.subscribeBy
import io.reactivex.rxkotlin.toFlowable
import io.vertx.micrometer.backends.BackendRegistries
import mu.KotlinLogging
import org.apache.pulsar.client.api.*
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.system.exitProcess

const val EVENTS_MAX_AGE_MINUTES = "EVENTS_MAX_AGE_MINUTES"
const val EVENTS_MAX_AGE_MINUTES_DEFAULT = 24 * 60 // Events more than a day old will not be streamed!
const val MAX_CACHED_PRODUCERS = "MAX_CACHED_PRODUCERS"
const val MAX_CACHED_PRODUCERS_DEFAULT = 10000L
const val EXPIRE_PRODUCER_AFTER_MINUTES = "EXPIRE_PRODUCER_AFTER_MINUTES"
const val EXPIRE_PRODUCER_AFTER_MINUTES_DEFAULT = 5L

private const val STALE_EVENTS_METRIC = "oblx.streamer.stale.events"
private const val SEND_FAILURES_MERIC = "oblx.streamer.send.failures"
private const val ACK_FAILURES_METRIC = "oblx.streamer.ack.failures"
private const val INCOMING_EVENTS_METRIC = "oblx.streamer.incoming.events"
private const val OUTGOING_EVENTS_METRIC = "oblx.streamer.outgoing.events"

private const val BUFFER_PERIOD_MS = 1000L
private const val BUFFER_MAX_SIZE = 500
private const val MAX_PRODUCE_CONCURRENCY = 8

fun main(args: Array<String>) {
    OblxLauncher.with(OblxBaseModule(), PulsarModule(), MongoDBMetaStoreModule())
        .bootstrap(DatasetStreamerService::class.java)
}

@Singleton
class DatasetStreamerService @Inject constructor(
    private val config: OblxConfig,
    private val pulsarClient: PulsarClient,
    metaStore: MetaStore
) : OblxService {

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
        pulsarClient.newConsumer(Schema.JSON(MetricEvent::class.java))
            .subscriptionName("${config.pulsarDatasetStreamerSubscriber}-fo")
            .subscriptionType(SubscriptionType.Failover)
            .subscriptionInitialPosition(SubscriptionInitialPosition.Earliest)
            .topic(config.pulsarMetricEventsTopic)
            .rxSubscribe()
            .flatMapCompletable { consumer ->
                consumer.toFlowable(BackpressureStrategy.BUFFER)
                    .map { it.second }
                    .buffer(BUFFER_PERIOD_MS, TimeUnit.MILLISECONDS, BUFFER_MAX_SIZE)
                    .onBackpressureBuffer()
                    .filter { it.isNotEmpty() }
                    .concatMapCompletable {
                        processEvents(it)
                            .flatMapCompletable { lastMessageId ->
                                consumer.rxAcknowledgeCumulative(lastMessageId)
                                    .doOnError { streamerAckFailures.increment() }
                                    .onErrorComplete()
                            }
                    }
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

    // Processes a batch of events and returns the latest message id
    private fun processEvents(events: List<Message<MetricEvent>>): Single<MessageId> {
        return events.toFlowable()
            .flatMapCompletable({ event ->
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
            }, false, MAX_PRODUCE_CONCURRENCY)
            .toSingle { events.maxOf { it.messageId } }
    }
}