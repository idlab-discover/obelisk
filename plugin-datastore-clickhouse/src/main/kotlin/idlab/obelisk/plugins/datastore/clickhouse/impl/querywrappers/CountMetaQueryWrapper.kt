package idlab.obelisk.plugins.datastore.clickhouse.impl.querywrappers

import idlab.obelisk.definitions.And
import idlab.obelisk.definitions.Neq
import idlab.obelisk.definitions.data.MetaQuery
import idlab.obelisk.plugins.datastore.clickhouse.impl.utils.CHMetaField
import idlab.obelisk.plugins.datastore.clickhouse.impl.utils.metaDataTableName
import io.vertx.reactivex.sqlclient.Row

internal class CountMetaQueryWrapper(q: MetaQuery) :
    AbstractQueryWrapper<MetaQuery, Long>(q.copy(filter = ignoreUnkownFilter(q.filter))) {

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
            "SELECT count(DISTINCT ${fields.joinToString { it.toExpr() }}) FROM $metaDataTableName ${
                where(
                    listOf(
                        metaFieldsNotEmpty
                    )
                )
            }"
        logger.debug { select }
        return select
    }

    override fun processResult(record: Row): Long {
        return record.getLong(0)
    }

}
