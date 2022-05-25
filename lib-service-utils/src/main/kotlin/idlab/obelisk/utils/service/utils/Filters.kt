package idlab.obelisk.utils.service.utils

import com.github.benmanes.caffeine.cache.Caffeine
import com.github.benmanes.caffeine.cache.LoadingCache
import idlab.obelisk.definitions.*
import idlab.obelisk.definitions.data.MetricEvent
import io.vertx.core.json.JsonArray
import io.vertx.core.json.JsonObject
import org.locationtech.spatial4j.context.SpatialContext
import org.locationtech.spatial4j.context.jts.JtsSpatialContext
import org.locationtech.spatial4j.distance.DistanceUtils
import java.util.concurrent.TimeUnit

private val regexCache: LoadingCache<String, Regex> = Caffeine.newBuilder()
    .maximumSize(10000)
    .expireAfterAccess(5, TimeUnit.MINUTES)
    .build { Regex(it) }

fun Any.matches(filter: FilterExpression): Boolean {
    // JSON representation of the object the filter is being applied to.
    val json = JsonObject.mapFrom(this)
    return when (filter) {
        SELECT_ALL -> true
        is And -> filter.operands.all { matches(it) }
        is Or -> filter.operands.any { matches(it) }
        is Not -> !matches(filter.operand)
        is Eq -> {
            val fieldVal = extractFieldFromJson(json, filter.field)
            if (fieldVal is JsonArray && filter.value !is Iterable<*>) {
                fieldVal.contains(filter.value)
            } else {
                fieldVal == filter.value
            }
        }
        is Neq -> extractFieldFromJson(json, filter.field) == filter.value
        is CompareFilter -> evaluateOrder(extractFieldFromJson(json, filter.field), filter)
        is In -> extractFieldFromJson(json, filter.field)?.let { filter.values.contains(it) } ?: false
        is RegexMatches -> regexMatches(extractFieldFromJson(json, filter.field), filter.regex, filter.options)
        is StartsWith -> "${extractFieldFromJson(json, filter.field)}".startsWith(filter.prefix)
        else -> throw IllegalArgumentException("Unsupported Filter Expression (${filter::class.java.name})!")
    }
}

fun MetricEvent.matches(filter: FilterExpression): Boolean {
    return when (filter) {
        SELECT_ALL -> true
        is And -> filter.operands.all { matches(it) }
        is Or -> filter.operands.any { matches(it) }
        is Not -> !matches(filter.operand)
        is Eq -> extractField(filter.field) == filter.value
        is Neq -> extractField(filter.field) != filter.value
        is CompareFilter -> evaluateOrder(extractField(filter.field), filter)
        is In -> extractField(filter.field)?.let { filter.values.contains(it) } ?: false
        is RegexMatches -> regexMatches(extractField(filter.field), filter.regex, filter.options)
        is StartsWith -> "${extractField(filter.field)}".startsWith(filter.prefix)
        is HasField -> extractField(filter.field) != null
        is HasTag -> tags?.let { it.contains(filter.tag) } ?: false
        is HasOneOfTags -> tags?.let { it.toSet().intersect(filter.tags).isNotEmpty() } ?: false
        is LocationInCircle -> location?.let {
            val center = SpatialContext.GEO.shapeFactory.pointXY(filter.center.lng, filter.center.lat)
            val radiusDEG = (1.0 * filter.radius / (1000 * DistanceUtils.DEG_TO_KM))
            SpatialContext.GEO.distCalc.within(center, it.lng, it.lat, radiusDEG)
        } ?: false
        is LocationInPolygon -> location?.let { loc ->
            val metricPoint = JtsSpatialContext.GEO.shapeFactory.pointXY(loc.lng, loc.lat)
            val polygonBuilder = JtsSpatialContext.GEO.shapeFactory.polygon()
            filter.vertices.forEach { polygonBuilder.pointXY(it.y, it.x) }
            polygonBuilder.build().relate(metricPoint).intersects()
        } ?: false
        else -> throw IllegalArgumentException("Unsupported Filter Expression (${filter::class.java.name})!")
    }
}

private fun extractFieldFromJson(jsonInput: Any?, field: Field): Any? {
    // Extract field from JSON value
    try {
        var jsonTmp: Any? = jsonInput
        for (p in field.path) {
            jsonTmp = when (jsonTmp) {
                null -> null
                is JsonObject -> jsonTmp.getValue(p)
                is Map<*, *> -> jsonTmp[p]
                is JsonArray -> jsonTmp.getValue(Field.decodeArrayIndex(p) - 1)
                is List<*> -> jsonTmp[Field.decodeArrayIndex(p) - 1]
                else -> throw IllegalArgumentException("Cannot find $p in ${field.path} for the provided JSON value!")
            }
        }
        return jsonTmp
    } catch (t: Throwable) {
        throw IllegalArgumentException("Cannot extract specified field from JSON value: ${field.path}.", t)
    }
}

private fun MetricEvent.extractField(field: Field): Any? {
    return if (field.path == listOf(EventField.location.toString(), "lng")) {
        this.location?.lng
    } else if (field.path == listOf(EventField.location.toString(), "lat")) {
        this.location?.lat
    } else if (field.path == listOf(EventField.producer.toString(), "userId")) {
        this.producer?.userId
    } else if (field.path == listOf(EventField.producer.toString(), "clientId")) {
        this.producer?.clientId
    } else if (this.metric?.type == MetricType.JSON && field.path.size > 1 && field.path[0] == EventField.value.toString()) {
        // Extract field from JSON value
        extractFieldFromJson(value, Field(field.path.drop(1)))
    } else {
        val name = field.path.first()
        when (EventField.valueOf(name)) {
            EventField.metric -> this.metric?.getFullyQualifiedId()
            EventField.dataset -> this.dataset
            EventField.source -> this.source
            EventField.timestamp -> this.timestamp
            EventField.geohash -> ""
            EventField.tsReceived -> this.tsReceived
            EventField.elevation -> this.elevation
            else -> throw IllegalArgumentException("Unsupported field $name!")
        }
    }
}

private fun regexMatches(fieldVal: Any?, regex: String, options: String? = null): Boolean {
    return if (fieldVal != null) {
        if (options?.contains('i', true) == true) {
            Regex(regex, RegexOption.IGNORE_CASE).matches(if (fieldVal is String) fieldVal else fieldVal.toString())
        } else {
            regexCache[regex]!!.matches(if (fieldVal is String) fieldVal else fieldVal.toString())
        }
    } else {
        false
    }
}

private fun evaluateOrder(fieldVal: Any?, compareFilter: CompareFilter): Boolean {
    if (fieldVal != null) {
        return when (compareFilter) {
            is Lt -> compare(fieldVal, compareFilter.value) < 0
            is Lte -> compare(fieldVal, compareFilter.value) <= 0
            is Gt -> compare(fieldVal, compareFilter.value) > 0
            is Gte -> compare(fieldVal, compareFilter.value) <= 0
            else -> false // Will never happen...
        }
    }
    return false
}

private fun compare(val1: Any, val2: Any): Int {
    return if (val1 is Number && val2 is Number) {
        val1.toDouble().compareTo(val2.toDouble())
    } else if (val1 is Boolean && val2 is Boolean) {
        val1.compareTo(val2)
    } else if (val1 is String) {
        val1.compareTo(if (val2 is String) val2 else val2.toString())
    } else {
        throw IllegalArgumentException("Cannot compare values of type ${val1::class.java.name}")
    }
}