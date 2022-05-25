package idlab.obelisk.services.pub.ngsi

import com.github.davidmoten.rx2.RetryWhen
import idlab.obelisk.utils.service.http.postWithBody
import idlab.obelisk.utils.service.reactive.flatMap
import idlab.obelisk.utils.service.reactive.retryWithExponentialBackoff
import io.reactivex.Completable
import io.reactivex.Flowable
import io.reactivex.Single
import io.reactivex.rxkotlin.subscribeBy
import io.vertx.core.http.HttpHeaders
import io.vertx.core.json.JsonObject
import io.vertx.junit5.VertxExtension
import io.vertx.junit5.VertxTestContext
import io.vertx.reactivex.core.Vertx
import io.vertx.reactivex.ext.web.Router
import org.junit.jupiter.api.*
import org.junit.jupiter.api.extension.ExtendWith
import java.util.*
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit

private val RUN_ID = UUID.randomUUID().toString()

/**
 * TODO: REWRITE THIS CLASS
 *
 * Running individual test will fail. The consecutive tests of this class are some sort of playbook and need to be executed in its entirety!
 */
@ExtendWith(VertxExtension::class)
@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class SubscriptionTest : AbstractNgsiTest() {
    private companion object {

        private lateinit var initialEntities: List<JsonObject>

        // Collection to hold the incoming NGSI-LD notifications
        private val notifications = CopyOnWriteArrayList<JsonObject>()

        private val subscription = JsonObject(
            """
            
            {
                "id": "urn:ngsi-ld:Subscription:mySubscription",
                "type": "Subscription",
                "entities": [
                    {
                    "type": "Vehicle"
                    }
                ],
                "watchedAttributes": ["speed"],
                "q": "speed>50",
                "notification": {
                    "endpoint": {
                        "uri": "http://127.0.0.1:8804/notify",
                        "accept": "application/json"
                    }
                },
                "@context": [
                    "https://uri.etsi.org/ngsi-ld/v1/ngsi-ld-core-context.jsonld"
                ]
            }
        """.trimIndent()
        )

        @BeforeAll
        @JvmStatic
        fun init(context: VertxTestContext, vertx: Vertx) {
            setup("subscription-test", "subscription-test")
                .flatMap {
                    // Setup HTTP server to receive the notifications
                    val router = Router.router(vertx)
                    router.postWithBody("/notify").handler { ctx ->
                        println(ctx.bodyAsJson)
                        ctx.bodyAsJson.getJsonArray("data").map { it as JsonObject }.forEach { notifications.add(it) }
                        ctx.response().setStatusCode(201).end()
                    }
                    vertx.createHttpServer().requestHandler(router).rxListen(8804).ignoreElement()
                }
                .flatMap {
                    initialEntities = generateVehicles(5)
                    Flowable.fromIterable(initialEntities)
                        .concatMapCompletable { postEntity(it) }
                }
                .subscribeBy(onComplete = context::completeNow, onError = context::failNow)
        }

        @AfterAll
        @JvmStatic
        fun cleanup(context: VertxTestContext) {
            teardown()
                .subscribeBy(
                    onComplete = context::completeNow,
                    onError = context::failNow
                )
        }

        private fun generateVehicles(amount: Int, prefix: String = "A"): List<JsonObject> {
            return (1..amount).map {
                // Generate test data, odd number vehicles get speed higher than 50
                val raw = """
                    {
                        "id": "urn:ngsi-ld:Vehicle:$RUN_ID:$prefix${it}",
                        "type": "Vehicle",
                        "speed": {
                            "type": "Property",
                            "value": ${if (it % 2 == 1) 75 else 30}
                        },
                        "@context": [
                            "https://uri.etsi.org/ngsi-ld/v1/ngsi-ld-core-context.jsonld"
                        ]
                    }
                """.trimIndent()
                JsonObject(raw)
            }
        }

        private fun postEntity(entityJson: JsonObject): Completable {
            return oblxClient.customHttpPost("${basePath}/entities/", entityJson) {
                it.putHeader(HttpHeaders.CONTENT_TYPE.toString(), "application/ld+json")
            }
                .map { response ->
                    if (response.statusCode() == 201) {
                        response.getHeader(HttpHeaders.LOCATION.toString())
                    } else {
                        logger.warn { "Entity creation failed (status: ${response.statusCode()}, body: ${response.bodyAsString()}) " }
                        throw RuntimeException("Entity creation failed!")
                    }
                }
                .ignoreElement()
        }
    }

    @Test
    @Order(1)
    fun testSubscriptionCreate(context: VertxTestContext) {


        oblxClient.customHttpPost("${basePath}/subscriptions/", subscription) {
            it.putHeader(HttpHeaders.CONTENT_TYPE.toString(), "application/ld+json")
        }
            .map { response ->
                if (response.statusCode() == 201) {
                    response.getHeader(HttpHeaders.LOCATION.toString())
                } else {
                    logger.warn { "Subscription creation failed (status: ${response.statusCode()}, body: ${response.bodyAsString()}) " }
                    throw RuntimeException("Subscription creation failed!")
                }
            }
            .flatMap { subscriptionPath ->
                oblxClient.customHttpGet(subscriptionPath) {
                    it.putHeader(HttpHeaders.ACCEPT.toString(), "application/ld+json")
                }
                    .map { resp ->
                        resp.bodyAsJsonObject()
                    }
            }
            .subscribeBy(
                onSuccess = {
                    context.verify {
                        it.remove("isActive")
                        it.getJsonObject("notification").remove("format")
                        Assertions.assertEquals(subscription.map, it.map)
                    }.completeNow()
                },
                onError = context::failNow
            )
    }

    @Test
    @Order(2)
    fun testInitialNotifications(context: VertxTestContext) {
        Single.fromCallable { Single.just(notifications) }
            .flatMap {
                val expectedEntities = initialEntities.toSet()
                    .filter { it.getJsonObject("speed").getInteger("value") > 50 }
                    .map {
                        it.remove("@context")
                        it.map
                    }
                    .toList()
                    .sortedBy { it["id"] as String }
                val receivedEntities = notifications.toSet()
                    //.filter { it.getString("id").contains(RUN_ID) }
                    .map {
                        it.getJsonObject("speed").remove("createdAt")
                        it.getJsonObject("speed").remove("instanceId")
                        it.getJsonObject("speed").remove("observedAt")
                        it.map
                    }
                    .toList()
                    .sortedBy { it["id"] as String }
                if (receivedEntities == expectedEntities) {
                    Single.just(receivedEntities)
                } else {
                    Single.error { MisMatchException() }
                }
            }
            .retryWhen(RetryWhen.retryIf { err -> err is MisMatchException }
                .exponentialBackoff(50L, TimeUnit.MILLISECONDS).build())
            .subscribeBy(
                onSuccess = { context.verify { it.isNotEmpty() }.completeNow() },
                onError = context::failNow
            )
    }

    @Test
    @Order(3)
    fun testStreamNewEntities(context: VertxTestContext) {
        val entity = generateVehicles(1, "B").first()
        postEntity(entity)
            .cache() // Upon retry, it should stop here...
            .flatMap {
                if (notifications.any { it.getString("id") == entity.getString("id") }) {
                    Completable.complete()
                } else {
                    Completable.error(MisMatchException())
                }
            }
            .retryWhen(RetryWhen.retryIf { err -> err is MisMatchException }
                .exponentialBackoff(50L, TimeUnit.MILLISECONDS).build())
            .subscribeBy(
                onComplete = context::completeNow,
                onError = context::failNow
            )
    }

}

class MisMatchException : RuntimeException("Received entities do not match!")
