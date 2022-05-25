package idlab.obelisk.plugins.datastore.clickhouse.impl.querywrappers

import idlab.obelisk.definitions.*
import idlab.obelisk.definitions.data.MetaData
import idlab.obelisk.definitions.data.MetaField
import idlab.obelisk.definitions.data.MetaQuery
import idlab.obelisk.definitions.data.Producer
import idlab.obelisk.plugins.datastore.clickhouse.impl.utils.CHMetaField
import idlab.obelisk.plugins.datastore.clickhouse.impl.utils.indexOfOrNull
import idlab.obelisk.plugins.datastore.clickhouse.impl.utils.metaDataTableName
import io.vertx.reactivex.sqlclient.Row

internal open class MetaQueryWrapper(q: MetaQuery) :
    AbstractQueryWrapper<MetaQuery, MetaData>(q.copy(filter = ignoreUnkownFilter(q.filter))) {

    private val fields =
        q.fields.flatMap { CHMetaField.from(it) }
            .distinct()

    override fun selectStatement(): String {
        val metaFieldsNotEmpty = And(fields.filterNot {
            setOf(
                CHMetaField.metric_type,
                CHMetaField.lastUpdate,
                CHMetaField.avgIngestRate,
                CHMetaField.count,
                CHMetaField.started
            ).contains(it)
        }
            .map { Neq(it.toString(), "") })
        val select =
            "SELECT ${fields.joinToString { it.toExpr() }} FROM $metaDataTableName ${
                where(
                    listOf(
                        metaFieldsNotEmpty
                    )
                )
            } ${groupBy()} ${orderBy()} ${limit()}"
        logger.debug { select }
        return select
    }

    fun orderBy(): String {
        val targetFields = q.orderBy.fields
        return if (targetFields.isNotEmpty()) {
            val orderFields = targetFields
                .flatMap { CHMetaField.from(it) }
                .distinct().joinToString()
            "ORDER BY $orderFields ${q.orderBy.ordering.toString().toUpperCase()}"
        } else {
            ""
        }
    }

    fun groupBy(): String {
        val targetFields = fields.filterNot {
            setOf(
                CHMetaField.started,
                CHMetaField.lastUpdate,
                CHMetaField.count,
                CHMetaField.avgIngestRate
            ).contains(it)
        }
        return if (targetFields.isNotEmpty()) {
            "GROUP BY ${targetFields.joinToString()}"
        } else {
            ""
        }
    }

    override fun processResult(record: Row): MetaData {
        return MetaData(
            dataset = fields.indexOfOrNull(CHMetaField.dataset)?.let { record.getString(it) },
            metricId = fields.indexOfOrNull(CHMetaField.metric_name)?.let { record.getString(it) },
            metricType = fields.indexOfOrNull(CHMetaField.metric_type)
                ?.let { MetricType.fromSuffix(record.getString(it)) },
            source = fields.indexOfOrNull(CHMetaField.source)?.let { record.getString(it) },
            producer = fields.indexOfOrNull(CHMetaField.user)?.let { userIndex ->
                Producer(
                    record.getString(userIndex),
                    fields.indexOfOrNull(CHMetaField.client)?.let { record.getString(it) })
            },
            lastUpdate = fields.indexOfOrNull(CHMetaField.lastUpdate)?.let { record.getLong(it) },
            started = fields.indexOfOrNull(CHMetaField.started)?.let { record.getLong(it) },
            avgIngestRate = fields.indexOfOrNull(CHMetaField.avgIngestRate)?.let { record.getDouble(it) },
            count = fields.indexOfOrNull(CHMetaField.count)?.let { record.getLong(it) }
        )
    }
}

// If the filter is not compatible with the meta-data table, it is replaced with SELECT ALL
internal fun ignoreUnkownFilter(filter: FilterExpression): FilterExpression {
    return when (filter) {
        SELECT_ALL -> filter
        is And -> And(filter.operands.map { ignoreUnkownFilter(it) })
        is Or -> Or(filter.operands.map { ignoreUnkownFilter(it) })
        is Not -> Not(ignoreUnkownFilter(filter.operand))
        is FieldValueFilterExpression -> if (isSupportedField(filter.field)) filter else SELECT_ALL
        is HasField -> if (isSupportedField(filter.field)) filter else SELECT_ALL
        else -> SELECT_ALL
    }
}

private fun isSupportedField(field: Field): Boolean {
    return when (field.path) {
        listOf(EventField.producer.toString(), "userId"), listOf(EventField.producer.toString(), "clientId") -> true
        else -> field.path.size == 1 && MetaField.values().filterNot { MetaField.producer == it }.map { it.toString() }
            .contains(field.path.first())
    }
}
