package idlab.obelisk.plugins.datastore.clickhouse.impl.querywrappers

import idlab.obelisk.definitions.MetricName
import idlab.obelisk.definitions.MetricType
import idlab.obelisk.definitions.data.IndexField
import idlab.obelisk.definitions.data.MetricStat
import idlab.obelisk.definitions.data.Producer
import idlab.obelisk.definitions.data.StatsQuery
import idlab.obelisk.definitions.framework.OblxConfig
import idlab.obelisk.plugins.datastore.clickhouse.impl.utils.CHField
import idlab.obelisk.plugins.datastore.clickhouse.impl.utils.indexOfOrNull
import idlab.obelisk.plugins.datastore.clickhouse.impl.utils.tableName
import idlab.obelisk.utils.service.utils.fromMus
import io.vertx.core.json.JsonArray
import io.vertx.reactivex.sqlclient.Row
import java.math.BigInteger

internal class StatsQueryWrapper(q: StatsQuery, config: OblxConfig) :
    AbstractDataQueryWrapper<StatsQuery, MetricStat>(q, config) {

    // Select fields
    private val fields: List<CHField> =
        // If the query uses groupByTime: add timestamp as first field
        listOfNotNull(q.groupBy?.time?.let { CHField.timestampMus })
            // Then add all the fields defined in the Query object
            .plus(q.fields.flatMap { CHField.from(it) })
            // All fields that are used in order by / limit by expressions must be included in the query!
            .plus(q.orderBy.fields.plus(q.groupBy?.fields ?: emptyList()).flatMap { CHField.from(it) })
            .distinct()

    override fun selectStatement(): String {
        // Additional filter when grouping on geohash (lat, lng must be present!)
        val geohashFilter = if (q.groupBy?.fields?.contains(IndexField.geohash) == true) {
            "AND NOT isNaN(${CHField.lat})"
        } else {
            ""
        }

        val select =
            "SELECT ${fields.joinToString { it.expr(q) }} FROM $tableName ${where()} $geohashFilter ${groupBy()} ${orderBy()} ${limit()} ${timeoutExpr()}"
        logger.debug { select }
        return select
    }

    override fun processResult(record: Row): MetricStat {
        return MetricStat(
            timestamp = fields.indexOfOrNull(CHField.timestampMus)
                ?.let { record.getLong(it)?.fromMus(q.timestampPrecision.unit) },
            dataset = fields.indexOfOrNull(CHField.dataset)?.let { record.getString(it) },
            metric = fields.indexOfOrNull(CHField.metric_name)?.let { metricNameIndex ->
                // If metric_name is present, metric_type must be as well!
                MetricName(
                    record.getString(metricNameIndex),
                    MetricType.fromSuffix(record.getString(fields.indexOfOrNull(CHField.metric_type)!!))
                )
            },
            source = fields.indexOfOrNull(CHField.source)?.let { record.getString(it) },
            producer = fields.indexOfOrNull(CHField.user)?.let { userIndex ->
                Producer(
                    record.getString(userIndex),
                    fields.indexOfOrNull(CHField.client)?.let { record.getString(it) })
            },
            count = fields.indexOfOrNull(CHField.count)?.let { record.getLong(it) },
            countSources = fields.indexOfOrNull(CHField.countSources)?.let { record.getLong(it) },
            max = fields.indexOfOrNull(CHField.max)?.let { record.getDouble(it) },
            mean = fields.indexOfOrNull(CHField.mean)?.let { record.getDouble(it) },
            min = fields.indexOfOrNull(CHField.min)?.let { record.getDouble(it) },
            stddev = fields.indexOfOrNull(CHField.stddev)?.let { record.getDouble(it) },
            sum = fields.indexOfOrNull(CHField.sum)?.let { record.getDouble(it) },
            geohash = fields.indexOfOrNull(CHField.geohash)?.let { record.getString(it) }
        )
    }

    private fun groupBy(): String {
        return if (q.groupBy == null) {
            ""
        } else {
            val groupBy = q.groupBy!!
            val fields = listOfNotNull(groupBy.time?.let { IndexField.timestamp }).plus(groupBy.fields)
                .flatMap { CHField.from(it) }.distinct()
                .joinToString()
            "GROUP BY $fields"
        }
    }
}
