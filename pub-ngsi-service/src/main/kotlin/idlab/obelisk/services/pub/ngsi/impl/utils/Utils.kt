package idlab.obelisk.services.pub.ngsi.impl.utils

import idlab.obelisk.definitions.*
import idlab.obelisk.definitions.catalog.Metric
import idlab.obelisk.definitions.catalog.Token
import idlab.obelisk.definitions.data.*
import idlab.obelisk.services.pub.ngsi.Constants
import idlab.obelisk.services.pub.ngsi.impl.model.*
import idlab.obelisk.utils.service.utils.Base64.decodeFromBase64
import io.vertx.core.json.Json
import io.vertx.core.json.JsonArray
import io.vertx.core.json.JsonObject
import io.vertx.reactivex.ext.web.RoutingContext
import java.lang.Integer.min
import java.net.URI
import java.net.URL
import java.time.Instant
import java.time.ZoneOffset
import java.util.*
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

val NON_ATTRIBUTES = setOf(Constants.FQ_ENTITY_ID, Constants.FQ_ENTITY_TYPE, Constants.LD_CONTEXT)
private val METRIC_PARSE_PATTERN = Pattern.compile("(?<metricId>.*)_datasetId\\((?<datasetId>.*)\\)")

data class InstanceId(val timestamp: Long, val metric: MetricName, val source: String, val ngsiType: String) {
    constructor(event: MetricEvent, type: String) : this(event.timestamp, event.metric!!, event.source!!, type)

    companion object {
        fun fromUri(uri: String): InstanceId {
            return Json.decodeValue(
                String(
                    Base64.getDecoder().decode(uri.substringAfter(Constants.OBLX_INSTANCE_BASE_URI))
                ), InstanceId::class.java
            )
        }
    }

    fun toUri(): String {
        return "${Constants.OBLX_INSTANCE_BASE_URI}${
            Base64.getEncoder().encodeToString(Json.encode(this).toByteArray())
        }"
    }
}

fun isValidUriLink(uri: String): Boolean {
    return try {
        URL(uri)
        true
    } catch (err: Throwable) {
        false
    }
}

fun checkValidUri(uri: String): String {
    return try {
        URI(uri)
        uri
    } catch (err: Throwable) {
        throw BadRequestData("Invalid URI", "$uri is not a valid URI", err)
    }
}

fun ngsiDateTimeFrom(timestampMus: Long): JsonArray {
    val dateTime = Instant.ofEpochMilli(TimeUnit.MILLISECONDS.convert(timestampMus, TimeUnit.MICROSECONDS))
        .atOffset(ZoneOffset.UTC).format(dateTimeFormatter)
    return JsonArray().add(
        JsonObject().put(Constants.FQ_VALUE, dateTime).put(Constants.FQ_ENTITY_TYPE, Constants.FQ_DATE_TIME)
    )
}

fun ngsiDateTimeFrom(dateTimeString: String): JsonArray {
    return JsonArray().add(
        JsonObject().put(Constants.FQ_VALUE, dateTimeString).put(Constants.FQ_ENTITY_TYPE, Constants.FQ_DATE_TIME)
    )
}

class ParsedMetric private constructor(val metric: MetricName, val ngsiDatasetId: String?) {

    companion object {

        fun from(metric: MetricName): ParsedMetric {
            val matcher = METRIC_PARSE_PATTERN.matcher(metric.name)
            return if (matcher.matches()) {
                ParsedMetric(MetricName(matcher.group("metricId"), MetricType.JSON), matcher.group("datasetId"))
            } else {
                ParsedMetric(metric, null)
            }
        }

    }

    fun getPropertyName(): String {
        return if (isValidUriLink(metric.name)) metric.name!! else "${Constants.DEFAULT_LD_NAMESPACE}/${metric.getFullyQualifiedId()}"
    }

}

fun ngsiTypeFilter(types: List<String>, ldContext: JsonObject): FilterExpression {
    return HasOneOfTags(types.map { "${Constants.TYPE_TAG_PREFIX}${JsonLdUtils.getFQType(it, ldContext)}" })
}

fun getTagValueByPrefix(tags: List<String>, tagPrefix: String): String? {
    return tags.find { it.startsWith(tagPrefix) }?.substringAfter(tagPrefix)
}

fun metaQuery(ctx: RoutingContext): EventsQuery {
    return EventsQuery(
        dataRange = DataRange(
            datasets = listOf(ctx.pathParam("datasetId")),
            metrics = MetricName.wildcard(MetricType.JSON)
        ),
        limit = getLimit(ctx),
        cursor = ctx.request().getParam("cursor") ?: null
    )
}

/**
 * Converts a /entities request context to an Obelisk EventsQuery
 */
fun entitiesQuery(
    ctx: RoutingContext,
    temporal: Boolean = false,
    ignoreQ: Boolean = false,
    overrideEntityIds: List<String> = emptyList()
): EventsQuery {
    var specifiers = 0
    val filter = And(
        listOfNotNull(
            if (overrideEntityIds.isNotEmpty()) In(
                EventField.source.toString(),
                overrideEntityIds.toSet()
            ) else ctx.request().getParam("id")?.split(',')?.map { checkValidUri(it) }
                ?.let { In(EventField.source.toString(), it.toSet()) },
            ctx.request().getParam("idPattern")?.let { RegexMatches(EventField.source.toString(), it) },
            ctx.request().getParam("type")?.split(',')?.let { types ->
                specifiers++
                ngsiTypeFilter(types, JsonLdUtils.getLDContext(ctx))
            },
            ctx.request().getParam("cursor")?.takeIf { !temporal }
                ?.let { Gt(EventField.source.toString(), it.decodeFromBase64()) },
            ctx.request().getParam("attrs")?.split(',')?.let { attrs ->
                specifiers++
                Or(attrs.map { StartsWith(EventField.metric.toString(), JsonLdUtils.getFQAttribute(it, ctx)) })
            },
            ctx.request().getParam("q")?.takeIf { !ignoreQ }
                ?.let { QueryParser.parse(it, JsonLdUtils.getLDContext(ctx)) },
            if (temporal) parseTemporalQuery(ctx) else null,
            parseGeoQuery(ctx)
        )
    )
    if (specifiers == 0) {
        throw BadRequestData(
            "Bad Request Data",
            "You must specify at least on of the following query parameters: type, attrs"
        )
    }
    return EventsQuery(
        dataRange = DataRange(
            datasets = listOf(ctx.pathParam("datasetId")),
            metrics = if (ctx.request().params()
                    .contains("q")
            ) MetricName.wildcard(setOf(MetricType.JSON)) else MetricName.wildcard()
        ),
        fields = listOf(
            EventField.dataset,
            EventField.metric,
            EventField.value,
            EventField.source,
            EventField.tags,
            EventField.tsReceived,
            EventField.location,
            EventField.producer
        ),
        timestampPrecision = TimestampPrecision.microseconds,
        orderBy = if (temporal) {
            if (getLastN(ctx) != null) {
                OrderBy(listOf(IndexField.source, IndexField.timestamp), Ordering.desc)
            } else {
                OrderBy(listOf(IndexField.timestamp, IndexField.source))
            }
        } else {
            OrderBy(listOf(IndexField.source, IndexField.timestamp))
        },
        filter = if (filter.operands.isNotEmpty()) filter else SELECT_ALL,
        limitBy = getLastN(ctx)?.takeIf { temporal }?.let { LimitBy(listOf(IndexField.source), it) }
    )
}

private fun parseGeoQuery(ctx: RoutingContext): FilterExpression? {
    return if (ctx.request().params().contains("georel")) {
        return QueryParser.parse(
            GeoQuery(
                georel = Json.decodeValue(ctx.request().getParam("georel"), GeoRel::class.java),
                geometry = ctx.request().getParam("geometry").let { Geometry.valueOf(it) },
                coordinates = ctx.request().getParam("coordinates"),
                geoproperty = ctx.request().getParam("geoproperty") ?: "location"
            )
        )
    } else {
        null
    }
}

private fun parseTemporalQuery(ctx: RoutingContext): FilterExpression {
    return QueryParser.parse(
        TemporalQuery(
            timerel = ctx.request().getParam("timerel")?.let { TimeRel.valueOf(it) }
                ?: throw IllegalArgumentException("'timerel' query parameter must be present!"),
            time = ctx.request().getParam("time")
                ?: throw IllegalArgumentException("'time' query parameter must be present!"),
            endTime = ctx.request().getParam("endTime"),
            timeProperty = ctx.request().getParam("timeproperty") ?: "observedAt"
        )
    )
}

fun entityQuery(ctx: RoutingContext): EventsQuery {
    val attrId: String? = ctx.pathParam("attrId")?.let { JsonLdUtils.getFQAttribute(it, ctx) }
    val filter = listOfNotNull(
        attrId?.let {
            RegexMatches(
                EventField.metric.toString(),
                "$it(?:_datasetId\\(.*\\))?::(json|number|bool|string)\$"
            )
        },
        Eq(EventField.source.toString(), checkValidUri(ctx.pathParam("entityId"))),
        *ctx.pathParam("instanceId")?.let { InstanceId.fromUri(it) }?.let {
            arrayOf(
                Eq(EventField.timestamp.toString(), it.timestamp),
                HasTag("${Constants.TYPE_TAG_PREFIX}${it.ngsiType}")
            )
        } ?: emptyArray<FilterExpression>()
    )

    return EventsQuery(
        dataRange = DataRange(
            datasets = listOf(ctx.pathParam("datasetId")),
            metrics = if (attrId != null) MetricName.wildcard(setOf(MetricType.JSON)) else MetricName.wildcard()
        ),
        fields = listOf(
            EventField.dataset,
            EventField.metric,
            EventField.value,
            EventField.source,
            EventField.tags,
            EventField.tsReceived,
            EventField.location,
            EventField.producer
        ),
        timestampPrecision = TimestampPrecision.microseconds,
        orderBy = OrderBy(listOf(IndexField.source, IndexField.timestamp)),
        filter = if (filter.size == 1) filter.first() else And(filter)
    )
}

fun batchDeleteQuery(ctx: RoutingContext, entityIds: List<String>): EventsQuery {
    return EventsQuery(
        dataRange = DataRange(
            datasets = listOf(ctx.pathParam("datasetId")),
            metrics = MetricName.wildcard(setOf(MetricType.JSON))
        ),
        filter = In(EventField.source.toString(), entityIds.toSet())
    )
}

fun convertToKeyValues(entity: JsonObject): JsonObject {
    val output = JsonObject()
    entity.forEach {
        val value = it.value
        if (value is JsonObject && value.containsKey("value")) {
            output.put(it.key, value.getValue("value"))
        } else {
            output.put(it.key, value)
        }
    }
    return output
}

fun getLimit(ctx: RoutingContext): Int {
    return min(Constants.MAX_LIMIT, ctx.request().getParam("limit")?.toInt() ?: Constants.DEFAULT_LIMIT)
}

fun getTemporalEntityLimit(ctx: RoutingContext): Int? {
    return ctx.request().getParam("limit")?.toInt()
}

fun getLastN(ctx: RoutingContext): Int? {
    return ctx.request().getParam("lastN")?.toInt()?.let { min(Constants.LAST_N_LIMIT, it) }
}

internal fun EventsQuery.restrictForDelete(token: Token): EventsQuery {
    /**
     * Make sure only entities that were created through NGSI can be deleted (JSON type, entityType tag)
     * If the user created these his/herself!!
     */
    return this.copy(
        dataRange = DataRange(this.dataRange.datasets, listOf(MetricName("*", MetricType.JSON))),
        filter = And(
            this.filter,
            Eq("${EventField.producer}.userId", token.user.id!!),
            HasTag(Constants.NGSI_MARKER_TAG)
        )
    )
}
