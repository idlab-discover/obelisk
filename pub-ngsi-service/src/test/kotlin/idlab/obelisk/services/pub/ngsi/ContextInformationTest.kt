package idlab.obelisk.services.pub.ngsi

import io.reactivex.rxkotlin.subscribeBy
import io.vertx.core.http.HttpHeaders
import io.vertx.core.json.JsonObject
import io.vertx.junit5.VertxExtension
import io.vertx.junit5.VertxTestContext
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.extension.ExtendWith
import java.time.Instant
import java.time.ZoneOffset

/**
 * TODO: REWRITE THIS CLASS
 *
 * In its current state, it requires in order execution, a lot of assumptions are made...
 */
@ExtendWith(VertxExtension::class)
@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class ContextInformationTest : AbstractNgsiTest() {

    private companion object {

        // Test run timestamp, to make sure only data for this run is used!
        private val timestamp = System.currentTimeMillis()

        private val entityId = "urn:ngsi-ld:${timestamp}:OffStreetParking:Downtown1"
        private val entityJson = JsonObject(
            """
        {
            "id": "$entityId",
            "type": "${timestamp}_OffStreetParking",
            "name": {
                "type": "Property",
                "value": "Downtown One"
            },
            "availableSpotNumber": {
                "type": "Property",
                "value": 121,
                "observedAt": "2018-12-29T12:05:02Z",
                "reliability": {
                "type": "Property",
                "value": 0.7
                },
                "providedBy": {
                    "type": "Relationship",
                    "object": "urn:ngsi-ld:Camera:C1"
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
        fun init(context: VertxTestContext) {
            setup("context-information-test", "context-information-test")
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

    /**
     * Test if an entity can be created and retrieved using the Context Information API
     */
    @Test
    @Order(1)
    fun testCreateEntity(context: VertxTestContext) {
        oblxClient.customHttpPost("$basePath/entities/", entityJson) {
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
            .flatMap { entityPath ->
                oblxClient.customHttpGet(entityPath) {
                    it.putHeader(HttpHeaders.ACCEPT.toString(), "application/ld+json")
                }
                    .map { resp ->
                        resp.bodyAsJsonObject()
                    }
            }
            .subscribeBy(
                onSuccess = {
                    context.verify {
                        it.getJsonObject("name").remove("observedAt")
                        assertEquals(entityJson.map, it.map)
                    }.completeNow()
                },
                onError = context::failNow
            )
    }

    /**
     * Test if a set of Entities of the same type can be created using the batch API.
     * Validation is done using the Context Information query API (querying by type)
     */
    @Test
    @Order(2)
    fun batchCreateTest(context: VertxTestContext) {
        val ts = System.currentTimeMillis()
        val nrOfBatchEntities = 50
        val entities = (1..nrOfBatchEntities).map {
            JsonObject()
                .put("id", "urn:obelisk-test:${timestamp}:t:$it")
                .put("type", "${timestamp}_T")
                .put(
                    "temperature", JsonObject()
                        .put("type", "Property")
                        .put("observedAt", Instant.ofEpochMilli(ts).atOffset(ZoneOffset.UTC).format(dateTimeFormatter))
                        .put("value", (0..40).random())
                )
        }
            .sortedBy { it.getString("id") }

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
            .flatMap {
                oblxClient.customHttpGet("$basePath/entities/?type=${timestamp}_T&count=true")
                    .map { resp ->
                        println("Count ${resp.getHeader(Constants.HTTP_HEADER_RESULT_COUNT)}")
                        context.verify {
                            assertEquals(nrOfBatchEntities, resp.getHeader(Constants.HTTP_HEADER_RESULT_COUNT).toInt())
                        }
                        resp.bodyAsJsonArray()
                    }
            }
            .subscribeBy(
                onSuccess = { result ->
                    context.verify {
                        assertEquals(entities.size, result.size())
                        result.map { it as JsonObject }
                            .sortedBy { it.getString("id") }
                            .zip(entities)
                            .forEach { (e1, e2) ->
                                e1.remove(Constants.LD_CONTEXT)
                                assertEquals(e2, e1)
                            }
                    }.completeNow()
                },
                onError = context::failNow
            )
    }

    // Test if we can append an attribute. While validating the update using the get entity API, we check if the system attributes are correctly generated.
    @Test
    @Order(3)
    fun testAppendAttribute(context: VertxTestContext) {
        val attribute = JsonObject().put(
            "speed", JsonObject()
                .put("type", "Property")
                .put("observedAt", Instant.now().atOffset(ZoneOffset.UTC).format(dateTimeFormatter))
                .put("value", (15..120).random())
        )

        oblxClient.customHttpPost("$basePath/entities/$entityId/attrs/", attribute) {
            it.putHeader(HttpHeaders.CONTENT_TYPE.toString(), "application/json")
        }
            .map { resp ->
                if (resp.statusCode() == 204) {
                    resp
                } else {
                    logger.warn { "Post attribute failed (status: ${resp.statusCode()}, body: ${resp.bodyAsString()}) " }
                    throw RuntimeException("Post attribute failed!")
                }
            }
            .flatMap {
                oblxClient.customHttpGet("$basePath/entities/$entityId?options=sysAttrs")
                    .map { it.bodyAsJsonObject() }
            }
            .subscribeBy(
                onSuccess = { result ->
                    context.verify {
                        assertTrue(result.containsKey("speed"))
                        assertEquals(
                            attribute.getJsonObject("speed").getValue("value"),
                            result.getJsonObject("speed").getValue("value")
                        )
                        assertTrue(result.getJsonObject("speed").containsKey("createdAt"))
                    }.completeNow()
                },
                onError = context::failNow
            )
    }

    @Test
    @Order(4)
    fun testPatchAttribute(context: VertxTestContext) {
        val attribute = JsonObject().put(
            "speed", JsonObject()
                .put("type", "Property")
                .put("value", (15..120).random())
        )

        oblxClient.customHttpPatch("$basePath/entities/$entityId/attrs/", attribute) {
            it.putHeader(HttpHeaders.CONTENT_TYPE.toString(), "application/json")
        }
            .map { resp ->
                if (resp.statusCode() == 204) {
                    resp
                } else {
                    logger.warn { "Patch attribute failed (status: ${resp.statusCode()}, body: ${resp.bodyAsString()}) " }
                    throw RuntimeException("Patch attribute failed!")
                }
            }
            .flatMap {
                oblxClient.customHttpGet("$basePath/entities/$entityId?options=sysAttrs")
                    .map { it.bodyAsJsonObject() }
            }
            .subscribeBy(
                onSuccess = { result ->
                    context.verify {
                        assertTrue(result.containsKey("speed"))
                        assertEquals(
                            attribute.getJsonObject("speed").getValue("value"),
                            result.getJsonObject("speed").getValue("value")
                        )
                        assertTrue(result.getJsonObject("speed").containsKey("modifiedAt"))
                    }.completeNow()
                },
                onError = context::failNow
            )
    }

    @Test
    @Order(5)
    fun testCreateMultiAttributeEntity(context: VertxTestContext) {
        val ts = Instant.ofEpochMilli(System.currentTimeMillis()).atOffset(ZoneOffset.UTC).format(dateTimeFormatter)
        val multiAttrEntityJson = JsonObject(
            """
            {
                "id":"urn:ngsi-ld:${timestamp}:Vehicle:A4567",
                "type":"${timestamp}_Vehicle",
                "speed#1":{
                    "type":"Property",
                    "value":55,
                    "http://example.org/hasSource":{
                        "type":"Property",
                        "value":"Speedometer"
                    },
                    "datasetId":"urn:ngsi-ld:Property:speedometerA4567-speed",
                    "observedAt": "$ts"
                },
                "speed#2":{
                    "type":"Property",
                    "value":54.5,
                    "http://example.org/hasSource":{
                        "type":"Property",
                        "value":"GPS"
                    },
                    "datasetId":"urn:ngsi-ld:Property:gpsBxyz123-speed",
                    "observedAt": "$ts"
                },
                "@context":[
                    {
                        "speed#1":"http://example.org/speed",
                        "speed#2":"http://example.org/speed"
                    },
                    "https://uri.etsi.org/ngsi-ld/v1/ngsi-ld-core-context.jsonld"
                ]
            }
        """.trimIndent()
        )

        oblxClient.customHttpPost("$basePath/entities/", multiAttrEntityJson) {
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
            .flatMap { entityPath ->
                oblxClient.customHttpGet(entityPath) {
                    it.putHeader(HttpHeaders.ACCEPT.toString(), "application/ld+json")
                }
                    .map { resp ->
                        resp.bodyAsJsonObject()
                    }
            }
            .subscribeBy(
                onSuccess = { result ->
                    context.verify {
                        assertEquals(
                            listOf(
                                multiAttrEntityJson.getJsonObject("speed#1").map,
                                multiAttrEntityJson.getJsonObject("speed#2").map
                            ).sortedBy { it.get("datasetId") as String },
                            result.getJsonArray("http://example.org/speed").list.sortedBy {
                                (it as Map<String, Any>).get(
                                    "datasetId"
                                ) as String
                            }
                        )
                    }.completeNow()
                },
                onError = context::failNow
            )
    }

    @Test
    @Order(6)
    fun testpatchMultiAttributeEntity(context: VertxTestContext) {
        val multiAttrEntityId = "urn:ngsi-ld:${timestamp}:Vehicle:A4567"
        val patchJson = JsonObject(
            """
            {
                "speed":{
                    "type":"Property",
                    "value":60,
                    "http://example.org/hasSource":{
                        "type":"Property",
                        "value":"GPS"
                    },
                    "datasetId":"urn:ngsi-ld:Property:gpsBxyz123-speed"
                },
                "@context":[
                    {
                        "speed":"http://example.org/speed"
                    },
                    "https://uri.etsi.org/ngsi-ld/v1/ngsi-ld-core-context.jsonld"
                ]
            }
        """.trimIndent()
        )


        oblxClient.customHttpPatch("$basePath/entities/$multiAttrEntityId/attrs/", patchJson) {
            it.putHeader(HttpHeaders.CONTENT_TYPE.toString(), "application/ld+json")
        }
            .map { resp ->
                if (resp.statusCode() == 204) {
                    resp
                } else {
                    logger.warn { "Patch attribute failed (status: ${resp.statusCode()}, body: ${resp.bodyAsString()}) " }
                    throw RuntimeException("Patch attribute failed!")
                }
            }
            .flatMap {
                oblxClient.customHttpGet("$basePath/entities/$multiAttrEntityId")
                    .map { it.bodyAsJsonObject() }
            }
            .subscribeBy(
                onSuccess = { result ->
                    context.verify {
                        assertEquals(
                            patchJson.getJsonObject("speed").getValue("value"),
                            result.getJsonArray("http://example.org/speed").map { it as JsonObject }
                                .filter { it.getString("datasetId") == "urn:ngsi-ld:Property:gpsBxyz123-speed" }.first()
                                .getValue("value")
                        )
                    }.completeNow()
                },
                onError = context::failNow
            )
    }

    @Test
    @Order(7)
    fun pagingTest(context: VertxTestContext) {
        // Insert 18 entities and see if we can page through them in pages of size 5
        val ts = System.currentTimeMillis()
        val entities = (1..18).map {
            JsonObject()
                .put("id", "urn:obelisk-paging-test:${timestamp}:pt:$it")
                .put("type", "${timestamp}_PT")
                .put(
                    "temperature", JsonObject()
                        .put("type", "Property")
                        .put("observedAt", Instant.ofEpochMilli(ts).atOffset(ZoneOffset.UTC).format(dateTimeFormatter))
                        .put("value", (0..40).random())
                )
        }
            .sortedBy { it.getString("id") }

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
            .flatMapPublisher {
                pageEntities("$basePath/entities/?type=${timestamp}_PT&limit=5")
            }
            .toList()
            .subscribeBy(
                onSuccess = { result ->
                    context.verify {
                        assertEquals(entities.size, result.size)
                        result.map { it as JsonObject }
                            .sortedBy { it.getString("id") }
                            .zip(entities)
                            .forEach { (e1, e2) ->
                                e1.remove(Constants.LD_CONTEXT)
                                assertEquals(e2, e1)
                            }
                    }.completeNow()
                },
                onError = context::failNow
            )
    }
}
