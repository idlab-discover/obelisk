package idlab.obelisk.services.pub.ngsi

import io.reactivex.Completable
import io.reactivex.rxkotlin.subscribeBy
import io.vertx.core.http.HttpHeaders
import io.vertx.core.json.JsonObject
import io.vertx.junit5.VertxExtension
import io.vertx.junit5.VertxTestContext
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import java.net.URLEncoder
import java.util.*

@ExtendWith(VertxExtension::class)
class AQDataModelTest : AbstractNgsiTest() {

    companion object {

        private val entityJson = JsonObject(
            """
            {
                "id": "urn:ngsi-ld:idlab:AirQualityObserved:${UUID.randomUUID()}",
                "type": "AirQualityObserved",
                "dateObserved": {
                    "type": "Property",
                    "value": "2021-06-21T10:28:03Z"
                },
                "location": {
                    "type": "GeoProperty",
                    "value": {
                        "type": "Point",
                        "coordinates": [
                            51.03,
                            4.606,
                            8.1
                        ]
                    }
                },
                "temperature": {
                    "type": "Property",
                    "value": 19.5
                },
                "relativeHumidity": {
                    "type": "Property",
                    "value": 99.9
                },
                "@context": [
                    "https://smartdatamodels.org/context.jsonld",
                    "https://uri.etsi.org/ngsi-ld/v1/ngsi-ld-core-context.jsonld"
                ]
            }
        """.trimIndent()
        )

        @BeforeAll
        @JvmStatic
        fun init(context: VertxTestContext) {
            setup("aq-datamodel-test", "aq-datamodel-test")
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
    fun testWriteReadAQEntity(context: VertxTestContext) {
        oblxClient.customHttpPost(
            "${basePath}/entities/",
            entityJson
        ) {
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
            .flatMap {
                // Query using type
                oblxClient.customHttpGet(
                    "${basePath}/entities/?type=${
                        URLEncoder.encode(
                            "https://uri.fiware.org/ns/data-models#AirQualityObserved",
                            Charsets.UTF_8
                        )
                    }"
                )
                    .map { it.bodyAsJsonArray() }
            }
            .subscribeBy(
                onSuccess = { result ->
                    context.verify {
                        result.map { it as JsonObject }.any { it.getString("id") == entityJson.getString("id") }
                    }.completeNow()
                },
                onError = context::failNow
            )
    }


}
