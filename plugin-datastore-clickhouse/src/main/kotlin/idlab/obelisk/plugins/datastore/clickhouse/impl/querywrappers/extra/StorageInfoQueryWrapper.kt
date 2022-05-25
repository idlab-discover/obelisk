package idlab.obelisk.plugins.datastore.clickhouse.impl.querywrappers.extra

import idlab.obelisk.definitions.DataRange
import idlab.obelisk.definitions.data.MetaField
import idlab.obelisk.definitions.data.MetaQuery
import idlab.obelisk.plugins.datastore.clickhouse.impl.querywrappers.AbstractQueryWrapper
import idlab.obelisk.plugins.datastore.clickhouse.impl.utils.localTableName
import io.vertx.reactivex.sqlclient.Row

internal val BOGUS_QUERY = MetaQuery(
    dataRange = DataRange(datasets = listOf()),
    fields = listOf(MetaField.metric)
)
private const val TABLE_SYSTEM_COLUMNS = "system.columns"

internal class StorageInfoQueryWrapper(private val compressed: Boolean, private val clusterName: String) :
    AbstractQueryWrapper<MetaQuery, Pair<String, Long>>(
        // Pass bogus meta-data query (as the base class expects this)
        BOGUS_QUERY
    ) {
    override fun selectStatement(): String {
        val bytesField = if (compressed) "data_compressed_bytes" else "data_uncompressed_bytes"
        val tableStmt =
            if (clusterName.isNotEmpty()) "cluster('$clusterName', $TABLE_SYSTEM_COLUMNS)" else TABLE_SYSTEM_COLUMNS
        val select =
            "SELECT name, $bytesField FROM $tableStmt WHERE table = '${localTableName.removePrefix("default.")}'"
        logger.debug { select }
        return select
    }

    override fun processResult(record: Row): Pair<String, Long> {
        return Pair(record.getString(0), record.getLong(1))
    }


}
