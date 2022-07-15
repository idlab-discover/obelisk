package idlab.obelisk.services.pub.ingest

import idlab.obelisk.definitions.catalog.*
import idlab.obelisk.definitions.data.MetricEvent
import idlab.obelisk.definitions.framework.OblxConfig
import idlab.obelisk.definitions.framework.OblxModule
import idlab.obelisk.definitions.messaging.Message
import idlab.obelisk.definitions.messaging.MessageBroker
import idlab.obelisk.definitions.messaging.MessagingMode
import idlab.obelisk.plugins.messagebroker.pulsar.PulsarMessageBrokerModule
import idlab.obelisk.plugins.metastore.mongo.DEFAULT_USAGE_LIMITS
import idlab.obelisk.plugins.metastore.mongo.MongoDBMetaStoreModule
import idlab.obelisk.plugins.ratelimiter.gubernator.GubernatorRateLimiterModule
import idlab.obelisk.utils.service.OblxBaseModule
import idlab.obelisk.utils.service.OblxLauncher.Companion.with
import io.reactivex.Completable
import io.reactivex.Flowable
import io.reactivex.Single
import io.vertx.core.json.JsonArray
import io.vertx.core.json.JsonObject
import io.vertx.ext.web.client.WebClientOptions
import io.vertx.junit5.VertxExtension
import io.vertx.junit5.VertxTestContext
import io.vertx.reactivex.core.Vertx
import io.vertx.reactivex.core.buffer.Buffer
import io.vertx.reactivex.core.http.HttpServerRequest
import io.vertx.reactivex.ext.web.client.HttpResponse
import io.vertx.reactivex.ext.web.client.WebClient
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.rx2.asFlowable
import kotlinx.coroutines.rx2.await
import org.codejargon.feather.Provides
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import java.time.Instant
import java.util.*
import java.util.concurrent.TimeUnit
import java.util.stream.Collectors
import java.util.stream.IntStream
import javax.inject.Singleton

/**
 * TODO: rewrite (this is an autoconverted class generated from Java...)
 */
@ExtendWith(VertxExtension::class)
class IngestTest {

    companion object {
        private var httpClient: WebClient? = null
        private lateinit var messageConsumerFlow: Flowable<Message<MetricEvent>>

        @JvmStatic
        @BeforeAll
        @Throws(Exception::class)
        fun setup() = runBlocking {
            val usageLimits = DEFAULT_USAGE_LIMITS


            val launcher = with(
                OblxBaseModule(),
                PulsarMessageBrokerModule(initLocalPulsar = true),
                GubernatorRateLimiterModule(),
                MongoDBMetaStoreModule(),
                object : OblxModule {
                    @Provides
                    @Singleton
                    fun mockup(): AccessManager {
                        return object : AccessManager {
                            override fun getAccessInfo(
                                user: User,
                                clientId: String?
                            ): Single<Map<String, Grant>> {
                                TODO()
                            }

                            override fun getToken(request: HttpServerRequest): Single<Token> {
                                return Single.just(
                                    Token(
                                        User("0", "someUser", "Some", "User", true),
                                        null,
                                        emptyMap(),
                                        usageLimits,
                                        "sadfqewfqwefasdFASDF"
                                    )
                                )
                            }

                            override fun invalidateToken(token: Token): Completable {
                                TODO()
                            }

                            override fun invalidateSessions(userIds: Set<String>, clientIds: Set<String>): Completable {
                                TODO("Not yet implemented")
                            }
                        }
                    }
                })
            val config = launcher.getInstance(OblxConfig::class.java)
            val vertx = launcher.getInstance(Vertx::class.java)
            httpClient =
                WebClient.create(vertx, WebClientOptions().setDefaultHost("localhost").setDefaultPort(config.httpPort))
            val messageBroker = launcher.getInstance(MessageBroker::class.java)
            val messageConsumer = messageBroker.createConsumer(
                topicName = config.pulsarMetricEventsTopic,
                subscriptionName = "ingest_test_" + Instant.now().toString(),
                MetricEvent::class,
                mode = MessagingMode.STREAMING
            )
            messageConsumer.seekToLatest()
            messageConsumerFlow = messageConsumer.receive().asFlowable()
            launcher.rxBootstrap(IngestService::class.java).await()
        }
    }

    @Test
    fun testNormalOperation(context: VertxTestContext, vertx: Vertx) {
        val rg = Random()
        val events = IntStream.range(0, 25)
            .mapToObj { i: Int ->
                JsonObject()
                    .put("timestamp", System.currentTimeMillis() + i)
                    .put("metric", "power::number")
                    .put("value", rg.nextDouble() * 60)
            }
            .collect(Collectors.toList())
        httpClient!!.post("/data/ingest/cot.flooding")
            .rxSendJson(JsonArray(events))
            .doOnSuccess { resp: HttpResponse<Buffer?> ->
                context.verify {
                    Assertions.assertEquals(
                        204,
                        resp.statusCode(),
                        "Wrong response code!"
                    )
                }
            }
            .flatMapCompletable { resp: HttpResponse<Buffer?>? ->
                messageConsumerFlow
                    .take(events.size.toLong())
                    .map { it.content }
                    .toList()
                    .doOnSuccess { results: List<MetricEvent> ->
                        context.verify {
                            assertEventsEquals(
                                events,
                                results
                            )
                        }
                    }
                    .ignoreElement()
            }
            .subscribe({ context.completeNow() }) { t: Throwable? -> context.failNow(t) }
    }

    @Test
    fun testBadSuffixType(context: VertxTestContext) {
        val body = JsonArray().add(
            JsonObject()
                .put("metric", "power::number")
                .put("value", "33")
        )
        httpClient!!.post("/data/ingest/cot.flooding")
            .rxSendJson(body)
            .subscribe(
                { resp: HttpResponse<Buffer?> ->
                    context.verify {
                        Assertions.assertEquals(
                            400,
                            resp.statusCode(),
                            "Wrong response code!"
                        )
                    }.completeNow()
                }) { t: Throwable? -> context.failNow(t) }
    }

    private fun assertEventsEquals(originals: List<JsonObject>, results: List<MetricEvent>) {
        Assertions.assertEquals(
            originals.size,
            results.size,
            "Number of received events must be equal to the number of ingested events!"
        )
        val origMap: Map<Long, JsonObject> = originals.associateBy { it.getLong("timestamp") }.toSortedMap()
        val resultsMap: Map<Long, MetricEvent> =
            results.associateBy { TimeUnit.MILLISECONDS.convert(it.timestamp, TimeUnit.MICROSECONDS) }.toSortedMap()
        origMap.forEach { (key: Long, value: JsonObject) ->
            Assertions.assertTrue(resultsMap.containsKey(key), "Ingested event cannot be found in received events!")
            val result = resultsMap[key]
            Assertions.assertEquals(value.getString("metric"), result!!.metric!!.getFullyQualifiedId())
            Assertions.assertEquals(value.getDouble("value"), result.value)
        }
    }
}
