import idlab.obelisk.definitions.DataRange
import idlab.obelisk.definitions.Eq
import idlab.obelisk.definitions.EventField
import idlab.obelisk.definitions.MetricName
import idlab.obelisk.definitions.data.DataStore
import idlab.obelisk.definitions.data.EventsQuery
import idlab.obelisk.definitions.data.MetricEvent
import idlab.obelisk.definitions.framework.OblxConfig
import idlab.obelisk.definitions.messaging.MessageBroker
import idlab.obelisk.definitions.messaging.MessageProducer
import idlab.obelisk.plugins.datastore.clickhouse.ClickhouseDataStoreModule
import idlab.obelisk.plugins.messagebroker.pulsar.PulsarMessageBrokerModule
import idlab.obelisk.service.internal.sink.SinkService
import idlab.obelisk.utils.service.OblxBaseModule
import idlab.obelisk.utils.service.OblxLauncher
import idlab.obelisk.utils.service.reactive.flatMap
import idlab.obelisk.utils.service.reactive.retryWithExponentialBackoff
import io.reactivex.Completable
import io.reactivex.rxkotlin.toFlowable
import io.vertx.core.json.JsonObject
import io.vertx.junit5.Timeout
import io.vertx.junit5.VertxExtension
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.rx2.await
import kotlinx.coroutines.rx2.rxCompletable
import mu.KotlinLogging
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import kotlin.random.Random

private const val datasetName = "test-ingest-sink-ds"
private val metricName = MetricName("test-doubles::number")
private const val nrOfTestEvents = 5000

@ExtendWith(VertxExtension::class)
class SinkServiceTest {

    companion object {

        private val logger = KotlinLogging.logger { }
        private lateinit var config: OblxConfig
        private lateinit var eventProducer: MessageProducer<MetricEvent>
        private lateinit var datastore: DataStore

        @JvmStatic
        @BeforeAll
        fun init() = runBlocking {
            val launcher =
                OblxLauncher.with(OblxBaseModule(), ClickhouseDataStoreModule(), PulsarMessageBrokerModule(initLocalPulsar = true))
            config = launcher.getInstance(OblxConfig::class.java)
            val messageBroker = launcher.getInstance(MessageBroker::class.java)
            datastore = launcher.getInstance(DataStore::class.java)

            eventProducer = messageBroker.createProducer(
                topicName = config.pulsarMetricEventsTopic,
                senderName = "sinkservice-test-producer",
                contentType = MetricEvent::class
            )

            launcher.rxBootstrap(SinkService::class.java).await()
        }

        @JvmStatic
        @AfterAll
        fun cleanup() = runBlocking {
            datastore.delete(
                EventsQuery(
                    dataRange = DataRange.fromDatasetId(datasetName)
                )
            ).await()
        }

    }

    @Test
    @Timeout(value = 60, timeUnit = TimeUnit.SECONDS)
    fun testNormalOperation() = runBlocking {
        val seriesId = UUID.randomUUID().toString()
        // Generate events
        val (events, _) = generateEvents(seriesId, nrOfTestEvents)
        // Post to metric_events topic (spread out over time in order to test sink buffering)
        events.toFlowable()
            .flatMapCompletable({ event ->
                rxCompletable {
                    eventProducer.send(event)
                }
            }, false, 2)
            .delay(2, TimeUnit.SECONDS)
            .flatMap {
                // Fetch db results and check if all the events were stored
                datastore.getEvents(
                    EventsQuery(
                        dataRange = DataRange(datasets = listOf(datasetName), metrics = listOf(metricName)),
                        filter = Eq(EventField.source.toString(), seriesId),
                        limit = nrOfTestEvents
                    )
                )
                    .flatMapCompletable { result ->
                        if (result.items.size == events.size && eventsMatch(events, result.items)) {
                            Completable.complete()
                        } else {
                            Completable.error(RuntimeException("Events are not matching!"))
                        }
                    }
                    .retryWithExponentialBackoff(1000, 5, "All events not yet accounted for, retrying...")
            }.await()
        logger.info { "All events accounted for!" }
    }

    @Test
    @Timeout(value = 60, timeUnit = TimeUnit.SECONDS)
    fun testFlowIncludesInvalidEvents() = runBlocking {
        val seriesId = UUID.randomUUID().toString()
        // Generate events
        val (events, errorCount) = generateEvents(seriesId, nrOfTestEvents, 0.01)
        // Post to metric_events topic (spread out over time in order to test sink buffering)
        events.toFlowable()
            .flatMapCompletable({ event ->
                rxCompletable {
                    eventProducer.send(event)
                }
            }, false, 2)
            .delay(2, TimeUnit.SECONDS)
            .flatMap {
                // Fetch db results and check if all the events were stored
                datastore.getEvents(
                    EventsQuery(
                        dataRange = DataRange(datasets = listOf(datasetName), metrics = listOf(metricName)),
                        filter = Eq(EventField.source.toString(), seriesId),
                        limit = nrOfTestEvents
                    )
                )
                    .flatMapCompletable { result ->
                        if (result.items.size == (events.size - errorCount) && eventsMatch(
                                events.filterNot { it.value is JsonObject },
                                result.items
                            )
                        ) {
                            Completable.complete()
                        } else {
                            Completable.error(RuntimeException("Events are not matching!"))
                        }
                    }
                    .retryWithExponentialBackoff(1000, 5, "All valid events not yet accounted for, retrying...")
            }.await()
        logger.info { "All valid events accounted for!" }
    }

    private fun generateEvents(
        seriesId: String,
        nrOfEvents: Int,
        invalidPercentage: Double = 0.0
    ): Pair<List<MetricEvent>, Int> {
        val timestamp = System.currentTimeMillis()
        var errors = 0
        return Pair((0 until nrOfEvents).map {
            MetricEvent(
                timestamp = timestamp + it,
                dataset = datasetName,
                metric = metricName,
                value = if (Random.nextDouble() < invalidPercentage) {
                    errors++
                    JsonObject().put(
                        "testDouble",
                        Random.nextDouble()
                    )
                } else {
                    Random.nextDouble()
                },
                source = seriesId,
                producer = idlab.obelisk.definitions.data.Producer("test")
            )
        }, errors)
    }

    private fun eventsMatch(expected: List<MetricEvent>, actual: List<MetricEvent>): Boolean {
        return expected.sortedBy { it.timestamp }
            .zip(actual.sortedBy { it.timestamp })
            .all { (e1, e2) -> abs(e1.value as Double - e2.value as Double) < 0.0001 }
    }

}
