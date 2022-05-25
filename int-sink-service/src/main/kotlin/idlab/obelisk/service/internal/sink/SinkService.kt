package idlab.obelisk.service.internal.sink

import idlab.obelisk.definitions.data.DataStore
import idlab.obelisk.definitions.data.MetricEvent
import idlab.obelisk.definitions.framework.OblxConfig
import idlab.obelisk.definitions.framework.OblxService
import idlab.obelisk.plugins.datastore.clickhouse.ClickhouseDataStoreModule
import idlab.obelisk.pulsar.utils.PulsarModule
import idlab.obelisk.pulsar.utils.rxBatchAcknowledge
import idlab.obelisk.pulsar.utils.rxSend
import idlab.obelisk.pulsar.utils.rxSubscribeAsFlowable
import idlab.obelisk.utils.service.OblxBaseModule
import idlab.obelisk.utils.service.OblxLauncher
import idlab.obelisk.utils.service.reactive.flatMap
import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.DistributionSummary
import io.micrometer.core.instrument.Timer
import io.reactivex.BackpressureOverflowStrategy
import io.reactivex.Completable
import io.reactivex.rxkotlin.subscribeBy
import io.reactivex.rxkotlin.toFlowable
import io.vertx.micrometer.backends.BackendRegistries
import mu.KotlinLogging
import org.apache.pulsar.client.api.PulsarClient
import org.apache.pulsar.client.api.Schema
import org.apache.pulsar.client.api.SubscriptionInitialPosition
import org.apache.pulsar.client.api.SubscriptionType
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.system.exitProcess

const val ENV_BATCHING_FLUSH_INTERVAL_MS = "BATCHING_FLUSH_INTERVAL"
const val DEFAULT_BATCHING_FLUSH_INTERVAL_MS = 2000L
const val ENV_BATCHING_MAX_BUFFER_SIZE = "BATCHING_MAX_BUFFER_SIZE"
const val DEFAULT_BATCHING_MAX_BUFFER_SIZE = 100000

const val ENV_PULSAR_DLQ_TOPIC = "PULSAR_DLQ_TOPIC"
const val DEFAULT_PULSAR_DLQ_TOPIC = "public/oblx-core/invalid_metric_events"
const val ENV_RX_BACKPRESSURE_BATCH_BUFFER = "RX_BACKPRESSURE_BATCH_BUFFER"
const val DEFAULT_RX_BACKPRESSURE_BATCH_BUFFER = 25
const val ENV_PULSAR_ACK_GROUP_TIME_MS = "PULSAR_ACK_GROUP_TIME_MS"
const val DEFAULT_PULSAR_ACK_GROUP_TIME_MS = 250L

fun main(args: Array<String>) {
    OblxLauncher.with(OblxBaseModule(), ClickhouseDataStoreModule(), PulsarModule())
        .bootstrap(SinkService::class.java)
}

@Singleton
class SinkService @Inject constructor(
    private val config: OblxConfig,
    private val datastore: DataStore,
    private val pulsarClient: PulsarClient
) : OblxService {

    private val microMeterRegistry = BackendRegistries.getDefaultNow()
    private val sinkWriteFailures = Counter
        .builder("oblx.sink.write.failures")
        .description("Counts number of times writing a batch to Clickhouse resulted in a failure.")
        .register(microMeterRegistry)
    val sinkAckFailures = Counter
        .builder("oblx.sink.ack.failures")
        .description("Counts number of times Pulsar acks resulted in a failure.")
        .register(microMeterRegistry)
    val sinkRxOverflows = Counter
        .builder("oblx.sink.rx.overflows")
        .description("Counts the number of times backpressure in the sink rx flow causes an overflow.")
        .register(microMeterRegistry)
    val sinkBatchEvents = DistributionSummary
        .builder("oblx.sink.batch.events")
        .description("Summary of the number of events per sink batch")
        .register(microMeterRegistry)
    val sinkBatchInsertTime = Timer
        .builder("oblx.sink.batch.insert.time")
        .description("Times the insert duration for a sink batch")
        .register(microMeterRegistry)

    private val logger = KotlinLogging.logger { }
    private val rxBackPressureBatchBuffer =
        config.getInteger(ENV_RX_BACKPRESSURE_BATCH_BUFFER, DEFAULT_RX_BACKPRESSURE_BATCH_BUFFER)
    private val dlqTopic = config.getString(ENV_PULSAR_DLQ_TOPIC, DEFAULT_PULSAR_DLQ_TOPIC)
    private val ackGroupTime =
        config.getLong(ENV_PULSAR_ACK_GROUP_TIME_MS, DEFAULT_PULSAR_ACK_GROUP_TIME_MS)
    private val dlqProducer = pulsarClient.newProducer(Schema.JSON(MetricEvent::class.java))
        .producerName(config.hostname())
        .topic(dlqTopic)
        .blockIfQueueFull(true)
        .create()

    override fun start(): Completable {
        val flushInterval =
            config.getLong(ENV_BATCHING_FLUSH_INTERVAL_MS, DEFAULT_BATCHING_FLUSH_INTERVAL_MS)
        val bufferSize = config.getInteger(ENV_BATCHING_MAX_BUFFER_SIZE, DEFAULT_BATCHING_MAX_BUFFER_SIZE)

        pulsarClient.newConsumer(Schema.JSON(MetricEvent::class.java))
            .subscriptionName(config.pulsarStorageSinkSubscriber)
            .subscriptionType(SubscriptionType.Shared)
            .subscriptionInitialPosition(SubscriptionInitialPosition.Earliest)
            .topics(listOf(config.pulsarMetricEventsTopic, config.storeOnlyMetricEventsTopic()))
            .enableRetry(false)
            .acknowledgmentGroupTime(ackGroupTime, TimeUnit.MILLISECONDS)
            .rxSubscribeAsFlowable()
            .onBackpressureBuffer(
                rxBackPressureBatchBuffer.toLong() * bufferSize,
                { sinkRxOverflows.increment() },
                BackpressureOverflowStrategy.ERROR
            )
            .buffer(flushInterval, TimeUnit.MILLISECONDS, bufferSize)
            .filter { it.isNotEmpty() }
            .onBackpressureBuffer(
                rxBackPressureBatchBuffer.toLong(),
                { sinkRxOverflows.increment() },
                BackpressureOverflowStrategy.ERROR
            )
            .concatMapCompletable { events ->
                val start = System.currentTimeMillis()
                datastore.ingest(events.map { it.second.value })
                    .flatMapCompletable { writeResults ->
                        val validWrites = writeResults.count { it.success }
                        logger.debug { "Stored $validWrites events in ${System.currentTimeMillis() - start} ms." }

                        sinkBatchEvents.record(events.size.toDouble())
                        sinkBatchInsertTime.record(System.currentTimeMillis() - start, TimeUnit.MILLISECONDS)

                        // Post events that could not be written (e.g. because of invalid data) to the DLQ
                        events.zip(writeResults).filterNot { it.second.success }
                            .toFlowable()
                            .flatMapCompletable {
                                // Log invalid event
                                val invalidEvent = it.first.second.value
                                logger.warn(it.second.exception) { "Detected invalid event for dataset ${invalidEvent.dataset}, metric ${invalidEvent.metric?.getFullyQualifiedId()}, posting to $dlqTopic for further inspection!" }
                                // Post to DLQ
                                dlqProducer.rxSend(invalidEvent).ignoreElement()
                            }
                    }
                    .doOnError { sinkWriteFailures.increment() }
                    .flatMap {
                        // Acknowledge successfully stored events (+ invalid events that were posted to the DLQ)
                        Completable.merge(events.groupBy { it.first }
                            .map { groupedByConsumer ->
                                groupedByConsumer.key.rxBatchAcknowledge(
                                    groupedByConsumer.value.map { it.second })
                                    .doOnEvent { sinkAckFailures.increment() }
                            })
                    }
            }
            .subscribeBy(
                onError = {
                    logger.error(it) { "Unrecoverable error in storage sink flow!" }
                    exitProcess(1)
                }
            )

        return Completable.complete()
    }
}
