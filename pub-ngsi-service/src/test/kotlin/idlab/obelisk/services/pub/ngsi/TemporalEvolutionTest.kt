package idlab.obelisk.services.pub.ngsi

import io.reactivex.Completable
import io.reactivex.rxkotlin.subscribeBy
import io.vertx.core.http.HttpHeaders
import io.vertx.core.json.JsonArray
import io.vertx.core.json.JsonObject
import io.vertx.junit5.VertxExtension
import io.vertx.junit5.VertxTestContext
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import java.time.Instant
import java.time.ZoneOffset
import java.util.concurrent.TimeUnit

@ExtendWith(VertxExtension::class)
class TemporalEvolutionTest : AbstractNgsiTest() {


    companion object {

        @BeforeAll
        @JvmStatic
        fun init(context: VertxTestContext) {
            setup("temporal-evolution-test", "temporal-evolution-test")
                .subscribeBy(
                    onComplete = context::completeNow,
                    onError = context::failNow
                )
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
    }


    @Test
    fun testPaging(context: VertxTestContext) {
        // Insert 20 instances for 5 entities (and try paging through this temporal evolution)s
        val nrOfEntities = 5
        val ts = System.currentTimeMillis()
        val entities = (1..nrOfEntities).flatMap { entityId ->
            (0..19).map { tsIndex ->
                JsonObject()
                    .put("id", "urn:obelisk-paging-test:${ts}:pt:$entityId")
                    .put("type", "${ts}_PT")
                    .put(
                        "temperature", JsonObject()
                            .put("type", "Property")
                            .put(
                                "observedAt",
                                Instant.ofEpochMilli(ts + tsIndex).atOffset(ZoneOffset.UTC).format(dateTimeFormatter)
                            )
                            .put("value", (0..40).random())
                    )
            }

        }

        val query = "?type=${ts}_PT&limit=25&recordLimit=25&timerel=after&time=${
            Instant.ofEpochMilli(ts - 1).atOffset(ZoneOffset.UTC).format(dateTimeFormatter)
        }"

        val expectedResults = entities
            .groupBy { it.getString("id") }
            .map { groupBySource ->
                JsonObject()
                    .put("id", groupBySource.key)
                    .put("type", "${ts}_PT")
                    .put(
                        "temperature",
                        JsonArray(groupBySource.value.sortedBy {
                            it.getJsonObject("temperature").getString("observedAt")
                        }.map { instance ->
                            instance.getJsonObject("temperature").getInteger("value")
                        })
                    )
            }

        oblxClient.customHttpPost("$basePath/entityOperations/create", entities) {
            it.putHeader(HttpHeaders.CONTENT_TYPE.toString(), "application/json")
        }
            .map { response ->
                if (response.statusCode() == 200) {
                    response.body()
                } else {
                    logger.warn { "Batch entity creation failed (status: ${response.statusCode()}, body: ${response.bodyAsString()}) " }
                    throw RuntimeException("Batch entity creation failed!")
                }
            }
            .delay(3, TimeUnit.SECONDS)
            .flatMap {
                // Check if count matches
                oblxClient.customHttpGet("$basePath/temporal/entities/$query&count=true")
                    .map { resp ->
                        val entityCount = resp.getHeader(Constants.HTTP_HEADER_RESULT_COUNT).toInt()
                        val attrInstanceCount = resp.getHeader(Constants.HTTP_HEADER_ATTRIBUTES_COUNT).toInt()
                        println("Entity Count: $entityCount, Attribute Instance Count: $attrInstanceCount")
                        context.verify {
                            Assertions.assertEquals(nrOfEntities, entityCount)
                            Assertions.assertEquals(entities.size, attrInstanceCount)
                        }
                    }
            }
            .flatMapPublisher {
                pageEntities("$basePath/temporal/entities/$query")
            }
            .toList()
            .subscribeBy(
                onSuccess = { result ->
                    context.verify {
                        val parsedResults = result
                            .groupBy { it.getString("id") }
                            .map { groupBySource ->
                                JsonObject()
                                    .put("id", groupBySource.key)
                                    .put("type", "${ts}_PT")
                                    .put(
                                        "temperature",
                                        JsonArray(groupBySource.value
                                            .flatMap {
                                                it.getJsonArray("temperature").map { it as JsonObject }
                                            }
                                            .sortedBy {
                                                it.getString("observedAt")
                                            }.map {
                                                it.getInteger("value")
                                            })
                                    )
                            }
                        Assertions.assertEquals(expectedResults, parsedResults)
                    }.completeNow()
                },
                onError = context::failNow
            )
    }

}
