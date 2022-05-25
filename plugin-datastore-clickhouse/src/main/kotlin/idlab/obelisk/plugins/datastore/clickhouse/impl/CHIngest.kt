package idlab.obelisk.plugins.datastore.clickhouse.impl

import idlab.obelisk.definitions.MetricType
import idlab.obelisk.definitions.data.MetricEvent
import idlab.obelisk.plugins.datastore.clickhouse.impl.utils.CHField
import idlab.obelisk.plugins.datastore.clickhouse.impl.utils.tableName
import idlab.obelisk.plugins.datastore.clickhouse.impl.utils.toCHDateTimeMicroseconds
import idlab.obelisk.plugins.datastore.clickhouse.impl.utils.toCHDateTimeMilliseconds
import io.vertx.core.json.Json
import io.vertx.reactivex.sqlclient.Tuple

private val chTableFields = CHField.values().filterNot { it.generated }
private val valuePlaceHolders = chTableFields.joinToString(", ") { "?" }
internal val insertStatement =
    "INSERT INTO $tableName (${chTableFields.joinToString(", ")}) VALUES ($valuePlaceHolders)"

internal fun eventToTuple(event: MetricEvent): Tuple {
    val metric = event.metric!!
    val value = event.value
    return Tuple.tuple()
        .addString(toCHDateTimeMicroseconds(event.timestamp))
        .addString(toCHDateTimeMilliseconds(event.tsReceived ?: System.currentTimeMillis()))
        .addString(event.dataset!!)
        .addString(metric.type.typeSuffix)
        .addString(metric.name)
        .addString(
            when (metric.type) {
                MetricType.JSON -> Json.encode(event.value)
                MetricType.STRING -> value?.toString() ?: ""
                else -> ""
            }
        )
        .addDouble(value?.takeIf { metric.type == MetricType.NUMBER }?.let { it as Double? } ?: (0.toDouble()))
        .addArrayOfDouble(value?.takeIf { metric.type == MetricType.NUMBER_ARRAY }
            ?.let { (it as List<Double>).toTypedArray() } ?: emptyArray<Double>())
        .addInteger(value?.takeIf { metric.type == MetricType.BOOL }?.let { if (it as Boolean) 1 else 0 } ?: 0)
        .addString(event.producer?.userId ?: "")
        .addString(event.producer?.clientId ?: "")
        .addString(event.source ?: "")
        .addArrayOfString(event.tags?.toTypedArray() ?: emptyArray<String>())
        .addDouble(event.location?.lat ?: Double.NaN)
        .addDouble(event.location?.lng ?: Double.NaN)
        .addDouble(event.elevation ?: Double.NaN)
}
