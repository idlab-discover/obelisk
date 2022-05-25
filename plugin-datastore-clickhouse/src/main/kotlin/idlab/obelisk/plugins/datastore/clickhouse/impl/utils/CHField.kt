package idlab.obelisk.plugins.datastore.clickhouse.impl.utils

import idlab.obelisk.definitions.EventField
import idlab.obelisk.definitions.Field
import idlab.obelisk.definitions.MetricName
import idlab.obelisk.definitions.MetricType
import idlab.obelisk.definitions.data.DataQuery
import idlab.obelisk.definitions.data.IndexField
import idlab.obelisk.definitions.data.StatsField
import idlab.obelisk.definitions.data.StatsQuery
import idlab.obelisk.utils.service.utils.toMus
import idlab.obelisk.definitions.data.DurationUnit

internal enum class CHField(
    val generated: Boolean = false,
    // We use an additional parameter CHField for the lambda, to avoid 'this' issues in this context
    private val expr: (CHField, DataQuery) -> String = { f, _ -> f.toString() }
) {
    timestamp, // The timestamp, represented in CH as a DateTime64 (used for timerange filters)
    timestampMus(generated = true, expr = { _, q -> // Processed timestamp (used for returning in the results)
        if (q is StatsQuery) {
            if (q.groupBy?.time != null) {
                val groupByTime = q.groupBy!!.time!!
                val intervalUnit = when (groupByTime.intervalUnit) {
                    DurationUnit.hours -> "hour"
                    DurationUnit.days -> "day"
                    DurationUnit.minutes -> "minute"
                    DurationUnit.seconds -> "second"
                }
                val offsetMus = groupByTime.offset.toLong().toMus(sourceUnit = groupByTime.offsetUnit.unit)
                "(toUnixTimestamp(toStartOfInterval(${timestamp}, INTERVAL ${groupByTime.interval} ${intervalUnit})) * 1000 * 1000) + $offsetMus as $timestampMus"
            } else {
                "NULL as $timestampMus"
            }
        } else {
            "toUnixTimestamp64Micro(${timestamp}) as $timestampMus"
        }
    }),
    tsReceived(expr = { _, _ -> "toUnixTimestamp64Milli(${tsReceived})" }),
    dataset,
    metric_type,
    metric_name,
    value_string(expr = ::valueSelector),
    value_number(expr = ::valueSelector),
    value_number_array(expr = ::valueSelector),
    value_bool(expr = ::valueSelector),
    user,
    client,
    source,
    tags,
    lat(expr = ::replaceNaNWithNullExpr),
    lng(expr = ::replaceNaNWithNullExpr),
    elevation(expr = ::replaceNaNWithNullExpr),
    geohash(generated = true, { _, q ->
        val precision = if (q is StatsQuery && q.groupBy?.fields?.contains(IndexField.geohash) == true) {
            if (q.groupBy!!.geohashPrecision in GROUP_BY_GEOHASH_RANGE) {
                q.groupBy!!.geohashPrecision
            } else {
                throw IllegalArgumentException("Geohash precision for groupBy must be in the following range: $GROUP_BY_GEOHASH_RANGE")
            }
        } else {
            12
        }
        "if(NOT isNaN(${lat}), geohashEncode($lng, $lat, $precision), NULL) as $geohash"
    }),
    min(generated = true, expr = { _, q -> "min(${value_number.expr(q)}) as $min" }),
    max(generated = true, expr = { _, q -> "max(${value_number.expr(q)}) as $max" }),
    mean(generated = true, expr = { _, q -> "avg(${value_number.expr(q)}) as $mean" }),
    stddev(generated = true, expr = { _, q -> "stddevPop(${value_number.expr(q)}) as $stddev" }),
    count(generated = true, expr = { _, _ -> "count(${value_number}) as $count" }),
    countSources(generated = true, expr = { _, _ -> "count(distinct(${source})) as $countSources" }),
    sum(generated = true, expr = { _, q -> "sum(${value_number.expr(q)}) as $sum" });

    fun expr(q: DataQuery): String {
        return expr.invoke(this, q)
    }

    companion object {

        // Fields that define the timeseries.
        fun timeSeriesIdFields(): List<CHField> {
            return listOf(dataset, metric_type, metric_name, user, client, source)
        }

        fun valueHolder(type: MetricType): CHField {
            return when (type) {
                MetricType.BOOL -> value_bool
                MetricType.STRING, MetricType.JSON -> value_string
                MetricType.NUMBER_ARRAY -> value_number_array
                MetricType.NUMBER -> value_number
            }
        }

        fun from(field: EventField, q: DataQuery): List<CHField> {
            return when (field) {
                EventField.timestamp -> listOf(timestampMus)
                EventField.metric -> listOf(metric_name, metric_type)
                EventField.location -> listOf(lat, lng)
                EventField.producer -> listOf(user, client)
                EventField.value -> q.dataRange.metrics.map { it.type }.distinct().map { valueHolder(it) }
                else -> listOf(valueOf(field.toString()))
            }
        }

        fun from(field: StatsField): List<CHField> {
            return when (field) {
                StatsField.metric -> listOf(metric_name, metric_type)
                StatsField.producer -> listOf(user, client)
                else -> listOf(valueOf(field.toString()))
            }
        }

        fun from(field: IndexField): List<CHField> {
            return when (field) {
                IndexField.timestamp -> listOf(timestampMus)
                IndexField.metric -> listOf(metric_name, metric_type)
                IndexField.producer -> listOf(user, client)
                else -> listOf(valueOf(field.toString()))
            }
        }

        fun jsonLookup(path: List<String>, type: MetricType): String {
            val operator = when (type) {
                MetricType.NUMBER -> "JSONExtractFloat"
                MetricType.STRING -> "JSONExtractString"
                MetricType.BOOL -> "JSONExtractBool"
                else -> throw IllegalArgumentException("Invalid datatype for FilterExpression field comparison operator!")
            }
            return "$operator($value_string, ${jsonLookupPathToCH(path)})"
        }
    }
}

private fun replaceNaNWithNullExpr(field: CHField, q: DataQuery): String =
    "if(NOT isNaN($field), $field, NULL) as $field"

private fun valueSelector(field: CHField, q: DataQuery): String = if (q.dataRange.metrics.any { it.isDerived() }) {
    // The query uses derived metrics, which require a different approach to value extraction
    q.dataRange.metrics.filter { it.isDerived() }.distinct()
        .joinToString(prefix = "multiIf(", postfix = ", $field) as $field") {
            "${CHField.metric_type} = '${MetricType.JSON.typeSuffix}' AND ${CHField.metric_name} = '${it.getParent()!!.name}', ${
                extractJson(
                    it.getDerived()!!
                )
            }"
        }
} else {
    // No special treatment required
    field.toString()
}

private fun jsonLookupPathToCH(path: List<String>): String {
    return path.joinToString(",") {
        if (Field.isArrayIndex(it)) {
            "${Field.decodeArrayIndex(it) + 1}"
        } else {
            "'$it'"
        }
    }
}

private fun extractJson(
    derivedMetric: MetricName
): String {
    val jsonPath = jsonLookupPathToCH(derivedMetric.name.split("->"))
    return when (derivedMetric.type) {
        MetricType.JSON -> "JSONExtractRaw(${CHField.value_string},$jsonPath)"
        MetricType.STRING -> "JSONExtractString(${CHField.value_string}, $jsonPath)"
        MetricType.BOOL -> "JSONExtractUInt(${CHField.value_string}, $jsonPath)"
        MetricType.NUMBER -> "JSONExtractFloat(${CHField.value_string}, $jsonPath)"
        MetricType.NUMBER_ARRAY -> "JSONExtract(${CHField.value_string}, $jsonPath, 'Array(Float64)')"
    }
}
