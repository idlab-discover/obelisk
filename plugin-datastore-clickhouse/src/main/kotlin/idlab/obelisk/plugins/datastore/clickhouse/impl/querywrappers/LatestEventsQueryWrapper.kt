package idlab.obelisk.plugins.datastore.clickhouse.impl.querywrappers

import idlab.obelisk.definitions.data.EventsQuery
import idlab.obelisk.definitions.framework.OblxConfig
import idlab.obelisk.plugins.datastore.clickhouse.impl.utils.CHField
import idlab.obelisk.plugins.datastore.clickhouse.impl.utils.tableName

internal open class LatestEventsQueryWrapper(q: EventsQuery, config: OblxConfig) : EventsQueryWrapper(q, config) {

    override fun selectStatement(): String {
        val select =
            "SELECT ${
                fields.joinToString {
                    val f = it.expr(q)
                    if (it == CHField.timestampMus) "toUnixTimestamp64Micro(max(${CHField.timestamp})) as ${CHField.timestampMus}" else "argMax($f, ${CHField.timestamp})"
                }
            } FROM $tableName ${where()} GROUP BY (${
                CHField.timeSeriesIdFields().joinToString()
            }) ${orderBy()} ${limitBy()} ${limit()} ${timeoutExpr()}"
        logger.debug { select }
        return select
    }

}
