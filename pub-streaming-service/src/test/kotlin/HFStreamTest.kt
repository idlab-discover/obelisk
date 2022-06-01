import idlab.obelisk.client.OblxClient
import idlab.obelisk.client.OblxClientOptions
import idlab.obelisk.definitions.DataRange
import idlab.obelisk.definitions.EventField
import idlab.obelisk.definitions.MetricName
import idlab.obelisk.definitions.catalog.*
import idlab.obelisk.definitions.data.MetricEvent
import idlab.obelisk.definitions.framework.ENV_GOOGLE_IDP_CLIENT_ID
import idlab.obelisk.definitions.framework.ENV_GOOGLE_IDP_CLIENT_SECRET
import idlab.obelisk.definitions.framework.OblxConfig
import idlab.obelisk.plugins.accessmanager.basic.BasicAccessManagerModule
import idlab.obelisk.plugins.accessmanager.basic.utils.SecureSecret
import idlab.obelisk.plugins.datastore.clickhouse.ClickhouseDataStoreModule
import idlab.obelisk.plugins.metastore.mongo.MongoDBMetaStoreModule
import idlab.obelisk.plugins.monitoring.prometheus.PrometheusMonitoringModule
import idlab.obelisk.plugins.ratelimiter.gubernator.GubernatorRateLimiterModule
import idlab.obelisk.pulsar.utils.*
import idlab.obelisk.services.pub.auth.AuthService
import idlab.obelisk.services.pub.auth.AuthServiceModule
import idlab.obelisk.services.pub.catalog.CatalogService
import idlab.obelisk.services.pub.streaming.StreamingService
import idlab.obelisk.utils.service.OblxBaseModule
import idlab.obelisk.utils.service.OblxLauncher
import idlab.obelisk.utils.service.reactive.flatMap
import idlab.obelisk.utils.service.utils.toMus
import io.reactivex.Completable
import io.reactivex.rxkotlin.subscribeBy
import io.reactivex.rxkotlin.toFlowable
import io.vertx.junit5.VertxExtension
import io.vertx.junit5.VertxTestContext
import io.vertx.reactivex.core.Vertx
import mu.KotlinLogging
import org.apache.pulsar.client.api.*
import org.junit.jupiter.api.*
import org.junit.jupiter.api.extension.ExtendWith
import uk.org.webcompere.systemstubs.environment.EnvironmentVariables
import uk.org.webcompere.systemstubs.jupiter.SystemStubsExtension
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.random.Random

private const val CLIENT_ID = "test-sse-hf"
private const val CLIENT_SECRET = "blargh"
private const val DATASET_ID = "test-sse-hf"
private const val STREAM_ID = "test-sse-hf"
private const val METRIC_ID = "test.randomDouble::number"

// Events to stream per phase
private const val EVENTS_TO_STREAM = 10000L
private const val REPORT_BATCH_SIZE = 1000

@ExtendWith(VertxExtension::class)
@ExtendWith(SystemStubsExtension::class)
class HFStreamTest {

    companion object {

        val logger = KotlinLogging.logger { }

        lateinit var config: OblxConfig
        lateinit var metaStore: MetaStore
        lateinit var pulsarClient: PulsarClient
        lateinit var pulsarProducer: Producer<MetricEvent>
        lateinit var client: OblxClient

        @BeforeAll
        @JvmStatic
        fun init(context: VertxTestContext, env: EnvironmentVariables) {
            env.set(ENV_GOOGLE_IDP_CLIENT_ID, "test-client-id")
            env.set(ENV_GOOGLE_IDP_CLIENT_SECRET, "test-client-secret")
            val launcher = OblxLauncher.with(
                OblxBaseModule(),
                PulsarModule(initLocalPulsar = true),
                BasicAccessManagerModule(),
                MongoDBMetaStoreModule(),
                AuthServiceModule(),
                ClickhouseDataStoreModule(),
                GubernatorRateLimiterModule(),
                PrometheusMonitoringModule()
            )
            launcher.rxBootstrap(AuthService::class.java, CatalogService::class.java, StreamingService::class.java)
                .doOnComplete {
                    config = launcher.getInstance(OblxConfig::class.java)
                    metaStore = launcher.getInstance(MetaStore::class.java)
                    pulsarClient = launcher.getInstance(PulsarClient::class.java)
                    // Create pulsar producer
                    pulsarProducer = pulsarClient.newProducer(Schema.JSON(MetricEvent::class.java))
                        .topic(config.pulsarDatasetTopic(DATASET_ID))
                        .blockIfQueueFull(true)
                        .configureForHighThroughput()
                        .create()

                    client = OblxClient.create(
                        launcher.getInstance(Vertx::class.java), OblxClientOptions(
                            apiUrl = config.authPublicUri,
                            clientId = CLIENT_ID,
                            secret = CLIENT_SECRET
                        )
                    )
                }
                .flatMap {
                    Completable.mergeArrayDelayError(
                        metaStore.createDataset(Dataset(DATASET_ID, DATASET_ID)).ignoreElement(),
                        metaStore.createDataStream(
                            DataStream(
                                id = STREAM_ID,
                                name = STREAM_ID,
                                userId = "0",
                                dataRange = DataRange(datasets = listOf(DATASET_ID)),
                                fields = listOf(
                                    EventField.timestamp,
                                    EventField.dataset,
                                    EventField.metric,
                                    EventField.source,
                                    EventField.tags,
                                    EventField.value
                                )
                            )
                        ).ignoreElement(),
                        metaStore.createClient(
                            Client(
                                id = CLIENT_ID,
                                userId = "0",
                                name = CLIENT_ID,
                                onBehalfOfUser = false,
                                confidential = true,
                                secretHash = SecureSecret.hash(CLIENT_SECRET),
                                scope = setOf(Permission.WRITE, Permission.READ)
                            )
                        ).ignoreElement()
                    ).onErrorComplete()
                }
                .subscribeBy(
                    onComplete = context::completeNow,
                    onError = context::failNow
                )
        }

    }

    //@RepeatedTest(10)
    fun sendThenReceivePulsar(context: VertxTestContext) {
        val start = System.currentTimeMillis()
        val runId = UUID.randomUUID().toString()

        eventProducer("pulsar", runId = runId)
            .flatMap {
                pulsarClient.newConsumer(Schema.JSON(MetricEvent::class.java))
                    .topic(config.pulsarDatasetTopic(DATASET_ID))
                    .subscriptionName("test-sse-hf")
                    .subscriptionType(SubscriptionType.Shared)
                    .subscriptionInitialPosition(SubscriptionInitialPosition.Latest)
                    .acknowledgmentGroupTime(500, TimeUnit.MILLISECONDS)
                    .rxSubscribe()
                    .flatMapCompletable { consumer ->
                        consumer.toFlowable()
                            .map { it.second.value }
                            .filter { it.source == runId }
                            .take(EVENTS_TO_STREAM)
                            .buffer(5, TimeUnit.SECONDS, REPORT_BATCH_SIZE)
                            .doOnNext { printReport(it, "pulsar") }
                            .ignoreElements()
                            .doOnComplete { timeReport(EVENTS_TO_STREAM, start, "pulsar") }
                            .flatMap {
                                consumer.rxClose()
                            }
                    }
            }
            .subscribeBy(
                onComplete = {
                    println("Received $EVENTS_TO_STREAM directly from Pulsar.")
                    context.completeNow()
                },
                onError = context::failNow
            )
    }

    @io.vertx.junit5.Timeout(2, timeUnit = TimeUnit.MINUTES)
    @RepeatedTest(1)
    fun sendThenReceiveSSE(context: VertxTestContext) {
        val start = System.currentTimeMillis()
        val runId = UUID.randomUUID().toString()

        Completable.concatArray(
            client.closeStream(STREAM_ID),
            eventProducer("sse1", runId = runId)
        )
            .flatMap {
                client.openStream(STREAM_ID, true)
                    .filter { it.source == runId }
                    .take(EVENTS_TO_STREAM)
                    .buffer(5, TimeUnit.SECONDS, REPORT_BATCH_SIZE)
                    .doOnNext { printReport(it, "sse1") }
                    .ignoreElements()
                    .doOnComplete { timeReport(EVENTS_TO_STREAM, start, "sse1") }
                    .flatMap { client.closeStream(STREAM_ID) }
            }
            .subscribeBy(
                onComplete = {
                    println("Received $EVENTS_TO_STREAM via SSE.")
                    context.completeNow()
                },
                onError = context::failNow
            )
    }

    @RepeatedTest(1)
    @io.vertx.junit5.Timeout(2, timeUnit = TimeUnit.MINUTES)
    fun startListeningThenSendSSE(context: VertxTestContext) {
        val start = System.currentTimeMillis()
        val runId = UUID.randomUUID().toString()

        client.closeStream(STREAM_ID).flatMap {
            Completable.mergeArray(
                // Only start sending after 5 seconds (when the stream is already started)
                eventProducer("sse2", runId = runId).delaySubscription(5, TimeUnit.SECONDS),
                client.openStream(STREAM_ID)
                    .filter { it.source == runId }
                    .take(EVENTS_TO_STREAM)
                    .buffer(5, TimeUnit.SECONDS, REPORT_BATCH_SIZE)
                    .doOnNext { printReport(it, "sse2") }
                    .ignoreElements()
                    .doOnComplete { timeReport(EVENTS_TO_STREAM * 2, start, "sse2") }
                    .flatMap { client.closeStream(STREAM_ID) }
            )
        }
            .subscribeBy(
                onComplete = {
                    println("Received $EVENTS_TO_STREAM via SSE.")
                    context.completeNow()
                },
                onError = context::failNow
            )
    }

    private fun eventProducer(
        phaseId: String,
        eventsToGenerate: Long = EVENTS_TO_STREAM,
        runId: String
    ): Completable {
        val start = System.currentTimeMillis()
        return (0L until eventsToGenerate).toFlowable()
            .flatMapCompletable { index ->
                val event = MetricEvent(
                    timestamp = System.currentTimeMillis().toMus(),
                    dataset = DATASET_ID,
                    metric = MetricName(METRIC_ID),
                    value = Random.nextDouble(),
                    source = runId,
                    tags = listOf(phaseId, index.toString()),
                    producer = idlab.obelisk.definitions.data.Producer("0", CLIENT_ID)
                )
                pulsarProducer.rxSend(event)
                    //.doOnSuccess { println("Posted $event") }
                    .ignoreElement()
            }
            .doOnComplete {
                val duration = System.currentTimeMillis() - start
                val eventsPerSec = eventsToGenerate * 1000 / duration
                println("Posted $eventsToGenerate events in $duration ms ($eventsPerSec events/s)")
            }
    }

    private fun printReport(batch: List<MetricEvent>, label: String) {
        val indices = batch.groupBy { it.tags?.firstOrNull() }.filterKeys { it != null }.mapValues { groupedEvents ->
            Pair(
                groupedEvents.value.mapNotNull { it.tags?.get(1)?.toInt() }.minOrNull(),
                groupedEvents.value.mapNotNull { it.tags?.get(1)?.toInt() }.maxOrNull()
            )
        }
        println(
            "[$label] Received ${batch.size} events... $indices"
        )
    }

    private fun timeReport(expectedEvents: Long, startTime: Long, label: String) {
        val duration = System.currentTimeMillis() - startTime
        val eventsPerSec = expectedEvents * 1000 / duration
        println("[$label] Streamed $expectedEvents events in $duration ms ($eventsPerSec events/s)")
    }
}
