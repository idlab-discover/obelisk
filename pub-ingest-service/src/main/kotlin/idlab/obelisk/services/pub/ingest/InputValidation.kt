package idlab.obelisk.services.pub.ingest

import idlab.obelisk.definitions.MetricName
import idlab.obelisk.definitions.MetricType
import idlab.obelisk.definitions.data.Location
import idlab.obelisk.definitions.data.MetricEvent
import idlab.obelisk.definitions.data.Producer
import idlab.obelisk.utils.service.utils.toMus
import io.vertx.core.json.Json
import java.time.Instant
import java.util.concurrent.TimeUnit

private object Constraints {
    const val STRING_CHAR_LIMIT = 65_535
    const val NUMBER_ARRAY_MAX_SIZE = 128
    const val META_STRING_CHAR_LIMIT = 128
    const val MAX_TAGS = 32
    val MAX_DATE_TIME_MUS = Instant.parse("2262-04-11T23:47:16Z").toEpochMilli().toMus()
}

fun validatedTimestampMus(timestamp: Long): Long {
    if (timestamp > 0 && timestamp <= Constraints.MAX_DATE_TIME_MUS) {
        return timestamp
    } else {
        throw IllegalArgumentException("A timestamp must be greater than 0!")
    }
}

fun validatedMetricName(metric: MetricName): MetricName {
    return validatedMetaField("metric", metric.name).let { metric }
}

fun validatedMetaField(fieldName: String, metaField: String?): String? {
    if (metaField == null || metaField.length <= Constraints.META_STRING_CHAR_LIMIT) {
        return metaField
    } else {
        throw IllegalArgumentException("Value for meta field '$fieldName' must not exceed ${Constraints.META_STRING_CHAR_LIMIT} characters!")
    }
}

fun validatedTags(tags: List<String>?): List<String>? {
    if (tags == null || tags.isEmpty() || (tags.size <= Constraints.MAX_TAGS && tags.any { it.length <= Constraints.META_STRING_CHAR_LIMIT })) {
        return tags
    } else {
        throw IllegalArgumentException("Max number of tags for an event is ${Constraints.MAX_TAGS} and each tag should have no more than ${Constraints.META_STRING_CHAR_LIMIT} characters!")
    }
}

fun validatedValue(metricName: MetricName, value: Any?): Any {
    return when (metricName.type) {
        MetricType.NUMBER -> when (value) {
            is Int -> value.toDouble()
            is Long -> value.toDouble()
            is Double -> value
            else -> throw IllegalArgumentException("Value type mismatch, expected numerical value.")
        }

        MetricType.BOOL -> if (value is Boolean) value else throw IllegalArgumentException("Value type mismatch, expected boolean value.")
        MetricType.STRING -> {
            if (value !is String) {
                throw IllegalArgumentException("Value type mismatch, expected string value.")
            }
            if (value.length <= Constraints.STRING_CHAR_LIMIT) value else throw IllegalArgumentException("String value cannot have more than ${Constraints.STRING_CHAR_LIMIT} characters!")
        }

        MetricType.JSON -> {
            if (value !is Map<*, *> && value !is List<*>) {
                throw IllegalArgumentException("Value type mismatch, expected JSON array or object!")
            }
            if (Json.encode(value).length <= Constraints.STRING_CHAR_LIMIT) value else throw IllegalArgumentException("JSON value cannot have more than ${Constraints.STRING_CHAR_LIMIT} characters!")
        }

        MetricType.NUMBER_ARRAY -> {
            if (value !is List<*>) {
                throw IllegalArgumentException("Value type mismatch, expected array of numbers!")
            } else if (value.size > Constraints.NUMBER_ARRAY_MAX_SIZE) {
                throw IllegalArgumentException("Array values cannot have a length larger than ${Constraints.NUMBER_ARRAY_MAX_SIZE}!")
            } else {
                value.map { validatedValue(MetricName("", MetricType.NUMBER), it) as Double? }
            }
        }
    }
}

fun validatedLocation(location: Location): Location {
    if (location.lng !in -180.0..180.0 || location.lat !in -90.0..90.0) {
        throw IllegalArgumentException("Location does not represent a valid geographical location!")
    }
    return location
}

data class IngestMetricEvent(
    val timestamp: Long? = null,
    val metric: MetricName,
    val value: Any,
    val source: String? = null,
    val tags: List<String>? = null,
    val location: Location? = null,
    val elevation: Double? = null
) {
    fun convert(dataset: String, producer: Producer, precision: TimeUnit, index: Int): MetricEvent {
        try {
            return MetricEvent(
                timestamp = timestamp?.let { validatedTimestampMus(TimeUnit.MICROSECONDS.convert(it, precision)) }
                    ?: System.currentTimeMillis().toMus(),
                dataset = validatedMetaField("dataset", dataset),
                producer = producer,
                elevation = elevation,
                location = location?.let { validatedLocation(location) },
                metric = validatedMetricName(metric),
                source = validatedMetaField("source", source),
                tags = validatedTags(tags),
                value = validatedValue(metric, value),
                tsReceived = System.currentTimeMillis()
            )
        } catch (e: Exception) {
            throw IllegalArgumentException("Invalid event at index $index: ${e.message}")
        }
    }
}