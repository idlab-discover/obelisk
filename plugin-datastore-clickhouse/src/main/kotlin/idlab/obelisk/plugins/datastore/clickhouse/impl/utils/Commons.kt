package idlab.obelisk.plugins.datastore.clickhouse.impl.utils

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty
import idlab.obelisk.definitions.*
import idlab.obelisk.definitions.data.*
import idlab.obelisk.utils.service.utils.Base64.decodeFromBase64
import idlab.obelisk.utils.service.utils.Base64.encodeAsBase64
import io.vertx.core.json.Json
import io.vertx.core.json.JsonArray
import io.vertx.reactivex.sqlclient.Row
import java.time.Instant
import java.util.concurrent.TimeUnit

internal val GROUP_BY_GEOHASH_RANGE = (4..8)
internal const val tableName = "default.metric_events"
internal const val localTableName = "${tableName}_local"
internal const val metaDataTableName = "default.metric_metadata"

internal fun <E> List<E>.indexOfOrNull(element: E): Int? = this.indexOf(element).takeIf { it >= 0 }

internal fun MetricType.convertCHValue(index: Int, row: Row): Any? {
    return when (this) {
        MetricType.JSON -> Json.decodeValue(row.getString(index))
        MetricType.BOOL -> row.getInteger(index) != 0
        MetricType.NUMBER_ARRAY -> (row.getValue(index) as DoubleArray).toList()
        MetricType.NUMBER -> row.getDouble(index)
        MetricType.STRING -> row.getString(index)
    }
}

internal data class Cursor(var offset: Long = 0) {
    constructor(encodedCursor: String?) : this(
        encodedCursor?.decodeFromBase64()?.toLong()
            ?: 0
    )

    fun increment(pagesize: Int) {
        offset += pagesize
    }

    fun encode(): String {
        return "$offset".encodeAsBase64()
    }
}

// Optimized cursor to use for Event/Statqueries (uses sort key boundaries as extra filters to avoid relying on inefficient offsets)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
data class OptimizedCursor(
    @JsonProperty("n")
    val nextSortTuple: List<Any?>,
    @JsonProperty("o")
    val offset: Int = 0,
) {

    companion object {

        private fun extractIndex(q: DataQuery, record: MetricRecord, field: IndexField): Any? {
            return when (field) {
                IndexField.timestamp -> toCHDateTimeMicroseconds(
                    TimeUnit.MICROSECONDS.convert(
                        record.timestamp!!,
                        q.timestampPrecision.unit
                    )
                )
                IndexField.geohash -> record.geohash
                IndexField.dataset -> record.dataset
                IndexField.metric -> record.metric
                IndexField.source -> record.source
                IndexField.producer -> record.producer
                IndexField.tags -> if (record is MetricEvent) record.tags else throw IllegalArgumentException("Cannot use 'tags' as index field for ${q::class.simpleName} (expected EventsQuery)!")
            }
        }

        fun generate(q: DataQuery, results: List<MetricRecord>): OptimizedCursor {
            val orderByFields = when (q) {
                is StatsQuery -> q.orderBy
                is EventsQuery -> q.orderBy
                else -> throw IllegalArgumentException("Invalid query type")
            }.fields
            val lastRecord = results.last()
            val offset = results.count { record ->
                orderByFields.all {
                    extractIndex(
                        q,
                        lastRecord,
                        it
                    ) == extractIndex(q, record, it)
                }
            } - 1
            return OptimizedCursor(orderByFields.map { extractIndex(q, lastRecord, it) }, offset)
        }

        fun from(encodedCursor: String): OptimizedCursor {
            return Json.decodeValue(encodedCursor.decodeFromBase64(), OptimizedCursor::class.java)
        }

    }

    @JsonIgnore
    fun encode(): String {
        return Json.encode(this).encodeAsBase64()
    }

    fun asFilters(q: DataQuery): FilterExpression {
        val filter = Or((0 until q.orderBy.fields.size)
            .map {
                val sublist = q.orderBy.fields.subList(0, q.orderBy.fields.size - it)
                And(sublist.mapIndexed { index, indexField ->
                    val lastValue = nextSortTuple[index]!!
                    if (index != (sublist.size - 1)) {
                        Eq(indexField, lastValue)
                    } else {
                        when (q.orderBy.ordering) {
                            Ordering.asc -> if (it == 0) Gte(indexField, lastValue) else Gt(indexField, lastValue)
                            Ordering.desc -> if (it == 0) Lte(indexField, lastValue) else Lt(indexField, lastValue)
                        }
                    }
                })
            })
        return filter
    }

}

fun toCHDateTimeMicroseconds(musUTCTimestamp: Long): String {
    return ofEpochMicro(musUTCTimestamp).toCH()
}

fun toCHDateTimeMilliseconds(msUTCTimestamp: Long): String {
    return Instant.ofEpochMilli(msUTCTimestamp).toCH()
}

internal fun ofEpochMicro(musUTCTimestamp: Long): Instant {
    val wholeSeconds = TimeUnit.MICROSECONDS.toSeconds(musUTCTimestamp)
    val instant = Instant.ofEpochSecond(
        wholeSeconds,
        TimeUnit.MICROSECONDS.toNanos(musUTCTimestamp) - TimeUnit.SECONDS.toNanos(wholeSeconds)
    )
    return instant
}

internal fun Instant.toCH(): String {
    return this.toString().replace("T", " ").replace("Z", "")
}
