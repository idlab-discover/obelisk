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

@ExtendWith(VertxExtension::class)
class CustomContextTest : AbstractNgsiTest() {

    companion object {
        private val entityJson = JsonObject(
            """
                {
                   "id":"urn:ngsi-ld:idlab:AirQualityObserved:5811524651",
                   "type":"AirQualityObserved",
                   "dateObserved":{
                      "type":"Property",
                      "value":"2021-06-29T12:50:29Z"
                   },
                   "location":{
                      "type":"GeoProperty",
                      "value":{
                         "type":"Point",
                         "coordinates":[
                            51.216,
                            3.22,
                            5.6
                         ]
                      }
                   },
                   "temperature":{
                      "type":"Property",
                      "value":20.1
                   },
                   "relativeHumidity":{
                      "type":"Property",
                      "value":99.9
                   },
                   "@context":[
                      "https://smartdatamodels.org/context.jsonld",
                      "https://uri.etsi.org/ngsi-ld/v1/ngsi-ld-core-context.jsonld"
                   ]
                }
            """.trimIndent()
        )

        @BeforeAll
        @JvmStatic
        fun init(context: VertxTestContext) {
            setup("custom-context-test", "custom-context-test")
                .subscribeBy(
                    onComplete = context::completeNow,
                    onError = context::failNow
                )
        }

        @AfterAll
        @JvmStatic
        fun cleanup(context: VertxTestContext) {
            //teardown()
            Completable.complete()
                .subscribeBy(
                    onComplete = context::completeNow,
                    onError = context::failNow
                )
        }
    }

    @Test
    fun testWriteReadCustomContextEntity(context: VertxTestContext) {
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
                        println(result)
                    }.completeNow()
                },
                onError = context::failNow
            )
    }

}