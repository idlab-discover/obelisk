package idlab.obelisk.services.pub.ngsi

import idlab.obelisk.services.pub.ngsi.helpers.batchRawJson
import io.reactivex.rxkotlin.subscribeBy
import io.vertx.core.http.HttpHeaders
import io.vertx.core.json.JsonArray
import io.vertx.junit5.VertxExtension
import io.vertx.junit5.VertxTestContext
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(VertxExtension::class)
class BatchOperationsTest : AbstractNgsiTest() {

    companion object {

        @BeforeAll
        @JvmStatic
        fun init(context: VertxTestContext) {
            setup("batch-operations-test", "batch-operations-test")
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
    fun testInsertBatch(context: VertxTestContext) {
        oblxClient.customHttpPost("$basePath/entityOperations/create", JsonArray(batchRawJson)) {
            it.putHeader(HttpHeaders.CONTENT_TYPE.toString(), "application/json")
        }
            .subscribeBy(
                onSuccess = { resp ->
                    context.verify {
                        Assertions.assertEquals(200, resp.statusCode())
                    }.completeNow()
                },
                onError = context::failNow
            )
    }

}
