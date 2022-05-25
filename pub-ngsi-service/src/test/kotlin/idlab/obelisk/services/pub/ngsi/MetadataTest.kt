package idlab.obelisk.services.pub.ngsi

import idlab.obelisk.services.pub.ngsi.impl.model.Attribute
import idlab.obelisk.services.pub.ngsi.impl.model.EntityTypeInfo
import idlab.obelisk.utils.service.reactive.flatMap
import io.reactivex.rxkotlin.subscribeBy
import io.vertx.core.http.HttpHeaders
import io.vertx.core.json.JsonObject
import io.vertx.junit5.VertxExtension
import io.vertx.junit5.VertxTestContext
import org.junit.jupiter.api.*
import org.junit.jupiter.api.extension.ExtendWith
import java.time.Instant
import java.time.ZoneOffset
import java.util.concurrent.TimeUnit

@ExtendWith(VertxExtension::class)
class MetadataTest : AbstractNgsiTest() {

    companion object {

        private lateinit var entities: List<JsonObject>
        private lateinit var types: List<String>
        private lateinit var attributes: List<String>
        private lateinit var attributesByType: Map<String, List<String>>
        private lateinit var typesByAttribute: Map<String, List<String>>

        @BeforeAll
        @JvmStatic
        fun init(context: VertxTestContext) {
            setup("metadata-test", "metadata-test")
                .flatMap {
                    // For 20 types, insert 5 different entities
                    val nrOfTypes = 20
                    val nrOfEntities = 5
                    val ts = System.currentTimeMillis()
                    entities = (1..nrOfTypes).flatMap { entityType ->
                        (0..nrOfEntities).map { entityId ->
                            JsonObject()
                                .put("id", "urn:obelisk-types-test:${ts}:type_${entityType}:$entityId")
                                .put("type", "type_${entityType}")
                                .put(
                                    "temperature", JsonObject()
                                        .put("type", "Property")
                                        .put(
                                            "observedAt",
                                            Instant.ofEpochMilli(ts).atOffset(ZoneOffset.UTC).format(dateTimeFormatter)
                                        )
                                        .put("value", (0..40).random())
                                )
                                .put(
                                    "humidity", JsonObject()
                                        .put("type", "Property")
                                        .put(
                                            "observedAt",
                                            Instant.ofEpochMilli(ts).atOffset(ZoneOffset.UTC).format(dateTimeFormatter)
                                        )
                                        .put("value", (200..500).random())
                                )
                                .put(
                                    "co2_ppm", JsonObject()
                                        .put("type", "Property")
                                        .put(
                                            "observedAt",
                                            Instant.ofEpochMilli(ts).atOffset(ZoneOffset.UTC).format(dateTimeFormatter)
                                        )
                                        .put("value", (40..90).random())
                                )

                        }

                    }

                    types = entities.map { it.getString("type") }.distinct().sorted()
                    attributes =
                        entities.flatMap { it.fieldNames().filterNot { fieldName -> fieldName in setOf("id", "type") } }
                            .distinct()
                            .sorted()
                    attributesByType = entities.groupBy { it.getString("type") }
                        .mapValues {
                            it.value.flatMap { entity ->
                                entity.fieldNames().filterNot { fieldName -> fieldName in setOf("id", "type") }
                            }
                                .distinct()
                                .sorted()
                        }

                    typesByAttribute = entities.flatMap { entity ->
                        entity.fieldNames().filterNot { fieldName ->
                            fieldName in setOf(
                                "id",
                                "type"
                            )
                        }.map { Pair(entity.getString("type"), it) }
                    }
                        .groupBy { it.second }
                        .mapValues { entry ->
                            entry.value.map { it.first }
                                .distinct()
                                .sorted()
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
                        .ignoreElement()
                        .delay(5, TimeUnit.SECONDS)
                }
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
    fun testGetTypes(context: VertxTestContext) {
        pageEntities("$basePath/types?details=true")
            .toList()
            .subscribeBy(
                onSuccess = { results ->
                    context.verify {
                        Assertions.assertEquals(
                            types.map { "${Constants.DEFAULT_LD_NAMESPACE}/$it" },
                            results.map { it.getString("id") })
                        results.forEach { type ->
                            val typeShortId =
                                type.getString("id").substringAfter("${Constants.DEFAULT_LD_NAMESPACE}/")
                            Assertions.assertEquals(
                                attributesByType[typeShortId]?.map { "${Constants.DEFAULT_LD_NAMESPACE}/$it" },
                                type.getJsonArray("attributeNames").list
                            )
                        }
                    }.completeNow()
                },
                onError = context::failNow
            )
    }

    @Test
    fun testGetType(context: VertxTestContext) {
        val type = types.random()
        oblxClient.customHttpGet("/$basePath/types/$type")
            .map { it.bodyAsJson(EntityTypeInfo::class.java) }
            .subscribeBy(
                onSuccess = { entityTypeInfo ->
                    context.verify {
                        Assertions.assertEquals("${Constants.DEFAULT_LD_NAMESPACE}/$type", entityTypeInfo.id)
                        Assertions.assertEquals(
                            entities.count { it.getString("type") == type }.toLong(),
                            entityTypeInfo.entityCount
                        )
                        Assertions.assertEquals(
                            attributesByType[type]!!.map { "${Constants.DEFAULT_LD_NAMESPACE}/$it" }.sorted(),
                            entityTypeInfo.attributeDetails.map { it.attributeName }.sorted()
                        )
                    }.completeNow()
                },
                onError = context::failNow
            )
    }

    @Test
    fun testGetAttributes(context: VertxTestContext) {
        pageEntities("$basePath/attributes?details=true")
            .toList()
            .subscribeBy(
                onSuccess = { results ->
                    context.verify {
                        Assertions.assertEquals(
                            attributes.map { "${Constants.DEFAULT_LD_NAMESPACE}/$it" },
                            results.map { it.getString("id") })
                        results.forEach { attr ->
                            val attrShortId =
                                attr.getString("id").substringAfter("${Constants.DEFAULT_LD_NAMESPACE}/")
                            Assertions.assertEquals(
                                typesByAttribute[attrShortId]?.map { "${Constants.DEFAULT_LD_NAMESPACE}/$it" },
                                attr.getJsonArray("typeNames").list,
                                "Invalid typeNames list for ${attr.getString("id")}"
                            )
                        }
                    }.completeNow()
                },
                onError = context::failNow
            )
    }

    @Test
    fun testGetAttribute(context: VertxTestContext) {
        val attrId = attributes.random()
        oblxClient.customHttpGet("/$basePath/attributes/$attrId")
            .map { it.bodyAsJson(Attribute::class.java) }
            .subscribeBy(
                onSuccess = { attr ->
                    context.verify {
                        Assertions.assertEquals("${Constants.DEFAULT_LD_NAMESPACE}/$attrId", attr.id)
                        Assertions.assertEquals(
                            entities.count { it.fieldNames().contains(attrId) }.toLong(),
                            attr.attributeCount
                        )
                        Assertions.assertEquals(typesByAttribute[attrId]!!.map { "${Constants.DEFAULT_LD_NAMESPACE}/$it" }
                            .sorted(), attr.typeNames?.sorted())
                    }.completeNow()
                },
                onError = context::failNow
            )
    }
}
