package idlab.obelisk.services.internal.streamer

import idlab.obelisk.definitions.catalog.MetaStore
import idlab.obelisk.definitions.data.MetricEvent
import idlab.obelisk.definitions.framework.OblxConfig
import idlab.obelisk.definitions.framework.OblxService
import idlab.obelisk.definitions.messaging.*
import idlab.obelisk.plugins.messagebroker.pulsar.PulsarMessageBrokerModule
import idlab.obelisk.plugins.metastore.mongo.MongoDBMetaStoreModule
import idlab.obelisk.utils.service.OblxBaseModule
import idlab.obelisk.utils.service.OblxLauncher
import idlab.obelisk.utils.service.instrumentation.IdToNameMap
import idlab.obelisk.utils.service.instrumentation.TargetType
import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.Tag
import io.micrometer.core.instrument.Tags
import io.reactivex.Completable
import io.reactivex.rxkotlin.subscribeBy
import io.vertx.micrometer.backends.BackendRegistries
import kotlinx.coroutines.rx2.asFlowable
import kotlinx.coroutines.rx2.await
import kotlinx.coroutines.rx2.rxCompletable
import mu.KotlinLogging
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.system.exitProcess

const val EVENTS_MAX_AGE_MINUTES = "EVENTS_MAX_AGE_MINUTES"
const val EVENTS_MAX_AGE_MINUTES_DEFAULT = 24 * 60 // Events more than a day old will not be streamed!

private const val STALE_EVENTS_METRIC = "oblx.streamer.stale.events"
private const val SEND_FAILURES_MERIC = "oblx.streamer.send.failures"
private const val ACK_FAILURES_METRIC = "oblx.streamer.ack.failures"
private const val INCOMING_EVENTS_METRIC = "oblx.streamer.incoming.events"
private const val OUTGOING_EVENTS_METRIC = "oblx.streamer.outgoing.events"

private const val BUFFER_PERIOD_MS = 1000L
private const val BUFFER_MAX_SIZE = 500

fun main(args: Array<String>) {
    OblxLauncher.with(OblxBaseModule(), PulsarMessageBrokerModule(), MongoDBMetaStoreModule())
        .bootstrap(DatasetStreamerService::class.java)
}

@Singleton
class DatasetStreamerService @Inject constructor(
    private val config: OblxConfig,
    private val messageBroker: MessageBroker,
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

    override fun start(): Completable = rxCompletable {
        val consumer = messageBroker.createConsumer(
            topicName = config.pulsarMetricEventsTopic,
            subscriptionName = config.pulsarDatasetStreamerSubscriber,
            contentType = MetricEvent::class,
            mode = MessagingMode.STREAMING
        )

        consumer.receive().asFlowable()
            .buffer(BUFFER_PERIOD_MS, TimeUnit.MILLISECONDS, BUFFER_MAX_SIZE)
            .onBackpressureBuffer()
            .filter { it.isNotEmpty() }
            .concatMapCompletable { events ->
                rxCompletable {
                    val lastMessageId = processEvents(events)
                    try {
                        consumer.acknowledgeCumulative(lastMessageId)
                    } catch (err: Throwable) {
                        streamerAckFailures.increment()
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

        datasetIdToNameMap.init().await()
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
                    Tag.of("datasetId", event.content.dataset!!),
                    Tag.of("datasetName", event.content.dataset?.let { datasetIdToNameMap.getName(it) } ?: "")
                )
            )
        ).increment()
    }

    // Processes a batch of events and returns the latest message id
    private suspend fun processEvents(events: List<Message<MetricEvent>>): MessageId {
        events.forEach { event ->
            if (event.content.timestamp > currentLowerTimestampBoundMus()) {
                countEvent(INCOMING_EVENTS_METRIC, event)
                val targetTopic = config.pulsarDatasetTopic(event.content.dataset!!)
                val producer = messageBroker.getProducer(
                    topicName = targetTopic,
                    contentType = MetricEvent::class,
                    senderName = config.hostname(),
                    mode = ProducerMode.HIGH_THROUGHPUT
                )
                try {
                    producer.send(event.content)
                    countEvent(OUTGOING_EVENTS_METRIC, event)
                } catch (err: Throwable) {
                    streamerSendFailures.increment()
                }
            } else {
                countEvent(STALE_EVENTS_METRIC, event)
            }
        }
        return events.maxOf { it.messageId }
    }
}
