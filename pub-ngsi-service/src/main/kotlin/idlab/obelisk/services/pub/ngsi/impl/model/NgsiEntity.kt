package idlab.obelisk.services.pub.ngsi.impl.model

import idlab.obelisk.definitions.MetricName
import idlab.obelisk.definitions.MetricType
import idlab.obelisk.definitions.data.MetricEvent
import idlab.obelisk.definitions.data.Producer
import idlab.obelisk.plugins.datastore.clickhouse.impl.utils.toCHDateTimeMicroseconds
import idlab.obelisk.services.pub.ngsi.Constants
import idlab.obelisk.services.pub.ngsi.impl.endpoints.AbstractEndpointsHandler
import idlab.obelisk.services.pub.ngsi.impl.utils.*
import io.reactivex.Single
import io.vertx.core.json.Json
import io.vertx.core.json.JsonArray
import io.vertx.core.json.JsonObject
import io.vertx.reactivex.ext.web.RoutingContext

/**
 * Represents an NGSI Entity or partial Entity.
 *
 * When operating this class, all keys and values are assumed to be fully expanded.
 */
data class NgsiEntity(
    val datasetId: String?,
    val id: String?,
    val type: String?,
    val attributes: MutableMap<String, MutableList<NgsiAttribute>> = mutableMapOf()
) {

    companion object {

        private val sysAttrNames = setOf(Constants.FQ_INSTANCE_ID, Constants.FQ_MODIFIED_AT, Constants.FQ_CREATED_AT)

        /**
         * Parses a fully expanded NgsiEntity from a rawEntity using the provided LD Context.
         */
        fun fromJson(
            rawEntity: JsonObject,
            ldContext: JsonObject,
            datasetId: String,
            producer: Producer
        ): Single<NgsiEntity> {
            return JsonLdUtils.expandJsonLDEntity(rawEntity, ldContext).map { expandedJson ->
                NgsiEntity(
                    datasetId = datasetId,
                    id = expandedJson.getString(Constants.FQ_ENTITY_ID),
                    type = expandedJson.getJsonArray(Constants.FQ_ENTITY_TYPE)?.getString(0),
                    attributes = expandedJson.fieldNames()
                        .filterNot { attrName -> NON_ATTRIBUTES.contains(attrName) }
                        .associateWith { attrName ->
                            expandedJson.getJsonArray(attrName).map { NgsiAttribute(producer, it as JsonObject) }
                                .toMutableList()
                        }
                        .toMutableMap()
                )
            }
        }

        /**
         * Parses a fully expanded NgsiEntity from a rawEntity using the LD context that can be retrieved from the HTTP RoutingContext.
         */
        fun fromJson(rawEntity: JsonObject, ctx: RoutingContext, producer: Producer): Single<NgsiEntity> {
            return fromJson(
                rawEntity,
                JsonLdUtils.getLDContext(ctx),
                ctx.pathParam(AbstractEndpointsHandler.PATH_PARAM_DATASET_ID),
                producer
            )
        }

        fun fromEvents(events: List<MetricEvent>, temporal: Boolean = false): NgsiEntity {
            val type = events.firstOrNull()?.tags?.let { getTagValueByPrefix(it, Constants.TYPE_TAG_PREFIX) }
            val entity = NgsiEntity(events.firstOrNull()?.dataset, events.firstOrNull()?.source, type)
            events.forEach {
                val parsedMetric = ParsedMetric.from(it.metric!!)
                val propertyName = parsedMetric.getPropertyName()
                val value =
                    if (it.value is JsonObject && (it.value as JsonObject).containsKey(Constants.FQ_ENTITY_TYPE)) {
                        it.value as JsonObject
                    } else if (it.value is Map<*, *> && (it.value as Map<*, *>).containsKey(Constants.FQ_ENTITY_TYPE)) {
                        JsonObject(it.value as Map<String, *>)
                    } else {
                        JsonObject()
                            .put(Constants.FQ_ENTITY_TYPE, listOf(Constants.FQ_PROPERTY_TYPE))
                            .put(
                                Constants.FQ_HAS_VALUE,
                                listOf(
                                    mapOf(
                                        Constants.FQ_VALUE to if (it.value is Map<*, *> || it.value is JsonObject) Json.encode(
                                            it.value
                                        ) else it.value
                                    )
                                )
                            )
                    }
                type?.let { t -> value.put(Constants.FQ_INSTANCE_ID, InstanceId(it, t).toUri()) }
                value.put(Constants.FQ_OBSERVED_AT, ngsiDateTimeFrom(it.timestamp))
                value.put(Constants.FQ_CREATED_AT, ngsiDateTimeFrom(it.tsReceived!!))
                it.tags
                    ?.find { tag -> tag.startsWith(Constants.MODIFIED_AT_TAG_PREFIX) }
                    ?.substringAfter(Constants.MODIFIED_AT_TAG_PREFIX)?.toLong()
                    ?.let { ts -> value.put(Constants.FQ_MODIFIED_AT, ngsiDateTimeFrom(ts)) }
                if (!entity.attributes.containsKey(propertyName)) {
                    entity.attributes[propertyName] = mutableListOf()
                }

                val ngsiAttr = NgsiAttribute(it.producer!!, value)
                parsedMetric.ngsiDatasetId?.let { ngsiAttr.setNgsiDatasetId(it) }

                entity.attributes[propertyName]?.add(ngsiAttr)
            }
            return entity
        }

    }

    /**
     * Compacts the NgsiEntity using the provided LD Context
     *
     * @param includeAttributes List of attributes to include in the compacted result. If not specified (or empty list), all attributes are included.
     */
    fun compact(
        ldContext: JsonObject,
        includeSysAttr: Boolean,
        includeAttributes: List<String> = emptyList()
    ): Single<JsonObject> {
        val entityJson = JsonObject()
            .put(Constants.FQ_ENTITY_ID, id ?: "")
            .put(Constants.FQ_ENTITY_TYPE, type ?: Constants.OBLX_GENERIC_TYPE_URN)
        attributes.filter { if (includeAttributes.isNotEmpty()) includeAttributes.contains(it.key) else true }
            .forEach { (key, value) ->
                entityJson.put(key, JsonArray(value.map {
                    it.raw.put(
                        Constants.FQ_INSTANCE_ID,
                        InstanceId(it.getTimestampMs(), MetricName(key, MetricType.JSON), id!!, type!!)
                    )
                    it.raw.filter { if (includeSysAttr) true else !sysAttrNames.contains(it.key) }
                        .associate { e -> Pair(e.key, e.value) }
                }))
            }
        return JsonLdUtils.compactJsonLDEntity(entityJson, ldContext)
    }

    /**
     * Compacts the NgsiEntity based on the context that can be retrieved using the HTTP RoutingContext
     */
    fun compact(
        ctx: RoutingContext,
        includeSysAttr: Boolean,
        includeAttributes: List<String> = emptyList()
    ): Single<JsonObject> {
        return compact(JsonLdUtils.getLDContext(ctx), includeSysAttr, includeAttributes)
    }

    /**
     * Converts the Entity to Obelisk MetricEvents given the provided Producer and Dataset metadata.
     *
     * Producer is optional only for internal Entity representation. Trying to write events converted from an NgsiEntity to Obelisk, will result in an error!
     */
    fun toEvents(failOnMissingEntityInfo: Boolean = true): List<MetricEvent> {
        val entityId = id?.let(::checkValidUri)
            ?: (if (failOnMissingEntityInfo) throw BadRequestData("Entity id is required!") else null)
        val entityType =
            type ?: (if (failOnMissingEntityInfo) throw BadRequestData("Entity type is required!") else null)
        return attributes.entries.flatMap { (key, attrs) -> attrs.map { Pair(key, it) } }.map { (key, attribute) ->
            MetricEvent(
                timestamp = attribute.getTimestampMus(),
                source = entityId,
                producer = attribute.producer,
                metric = MetricName(
                    attribute.getNgsiDatasetId()?.let { "${key}_datasetId(${attribute.getNgsiDatasetId()})" }
                        ?: key, MetricType.JSON),
                value = attribute.raw.map,
                dataset = datasetId,
                location = attribute.getLocation(),
                tags = listOf("${Constants.TYPE_TAG_PREFIX}$entityType", Constants.NGSI_MARKER_TAG),
                tsReceived = System.currentTimeMillis()
            )
        }
    }

    /**
     * Update the state of the specified attribute.
     * attrVal is a list of NgsiAttributes to support NGSI multi-attributes
     *
     * We will match instances of the attributes using the provided NGSI datasetId property
     */
    fun setAttribute(attrName: String, attrVals: List<NgsiAttribute>) {
        if (attributes.containsKey(attrName)) {
            val currentVals = attributes[attrName]!!
            attrVals.forEach { attrVal ->
                currentVals.find { it.getNgsiDatasetId() == attrVal.getNgsiDatasetId() }?.let { oldAttrVal ->
                    oldAttrVal.getTimeField(NgsiAttribute.TimeField.createdAt)
                        ?.let { attrVal.setTimeField(NgsiAttribute.TimeField.createdAt, it) }
                    attrVal.setTimeFieldToNow(NgsiAttribute.TimeField.modifiedAt)
                    currentVals[currentVals.indexOf(oldAttrVal)] = attrVal
                }
            }
        } else {
            attributes[attrName] = attrVals.toMutableList()
        }
    }

    fun getLastUpdateTS(): String? {
        return attributes.flatMap { attrs -> attrs.value.map { it.getTimestampMus() } }
            .maxOrNull()?.let { toCHDateTimeMicroseconds(it) }
    }

    fun getOldestUpdateTS(): String? {
        return attributes.flatMap { attrs -> attrs.value.map { it.getTimestampMus() } }
            .minOrNull()?.let { toCHDateTimeMicroseconds(it) }
    }

}
