package idlab.obelisk.plugins.datastore.clickhouse.impl.querywrappers

import idlab.obelisk.definitions.EventField
import idlab.obelisk.definitions.MetricName
import idlab.obelisk.definitions.MetricType
import idlab.obelisk.definitions.data.EventsQuery
import idlab.obelisk.definitions.data.Location
import idlab.obelisk.definitions.data.MetricEvent
import idlab.obelisk.definitions.data.Producer
import idlab.obelisk.definitions.framework.OblxConfig
import idlab.obelisk.plugins.datastore.clickhouse.impl.utils.CHField
import idlab.obelisk.plugins.datastore.clickhouse.impl.utils.convertCHValue
import idlab.obelisk.plugins.datastore.clickhouse.impl.utils.indexOfOrNull
import idlab.obelisk.plugins.datastore.clickhouse.impl.utils.tableName
import idlab.obelisk.utils.service.utils.fromMus
import io.vertx.reactivex.sqlclient.Row

internal open class EventsQueryWrapper(q: EventsQuery, config: OblxConfig) :
    AbstractDataQueryWrapper<EventsQuery, MetricEvent>(q, config) {

    // Select fields
    protected val fields: List<CHField> =
        // Always add timestamp as first field
        listOf(CHField.timestampMus)
            // Then add all the fields defined in the Query object
            .plus(q.fields.flatMap { CHField.from(it, q) })
            // If the query is a cross-metric-type query: we need the metric_type field as well (for fetching the correct value when processing the output)
            .plus(if (targetMetricTypes.size > 1 && !q.fields.contains(EventField.metric)) listOf(CHField.metric_type) else emptyList())
            // All fields that are used in order by / limit by expressions must be included in the query!
            .plus(q.orderBy.fields.plus(q.limitBy?.fields ?: emptyList()).flatMap { CHField.from(it) })
            .distinct()

    override fun selectStatement(): String {
        val select =
            "SELECT ${fields.joinToString { it.expr(q) }} FROM $tableName ${where()} ${orderBy()} ${limitBy()} ${limit()} ${timeoutExpr()}"
        logger.debug { select }
        return select
    }

    override fun processResult(record: Row): MetricEvent {
        return MetricEvent(
            // Timestamp is always queried
            timestamp = fields.indexOfOrNull(CHField.timestampMus)!!
                .let { record.getLong(it).fromMus(q.timestampPrecision.unit) },
            tsReceived = fields.indexOfOrNull(CHField.tsReceived)?.let { record.getLong(it) },
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
            // If lat field is present and the value is not null (lng will be as well), create a Location instance
            location = fields.indexOfOrNull(CHField.lat)?.let { latIndex ->
                record.getDouble(latIndex)
            }?.let { lat ->
                Location(lat, fields.indexOfOrNull(CHField.lng)?.let { record.getDouble(it) }!!)
            },
            tags = fields.indexOfOrNull(CHField.tags)?.let {
                record.getArrayOfStrings(it).toList()
            },
            geohash = fields.indexOfOrNull(CHField.geohash)?.let { record.getString(it) },
            elevation = fields.indexOfOrNull(CHField.elevation)?.let { record.getDouble(it) },
            value = extractValue(record)
        )
    }

    private fun extractValue(record: Row): Any? {
        /*
         * When performing a cross-metric-type query, the type field is always queried from CH to allow us
         * to determine from which field the value should be fetched.
        */
        val targetType = if (targetMetricTypes.size > 1) fields.indexOfOrNull(CHField.metric_type)!!
            .let { MetricType.fromSuffix(record.getString(it)) } else targetMetricTypes.first()
        return fields.indexOfOrNull(CHField.valueHolder(targetType))
            ?.let { index -> targetType.convertCHValue(index, record) }
    }

    protected fun limitBy(): String {
        return q.limitBy?.let { limitBy ->
            val fields = limitBy.fields.joinToString(",") { CHField.from(it).toString() }
            "LIMIT ${limitBy.offset},${limitBy.limit} BY $fields"
        } ?: ""
    }

}
