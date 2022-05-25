package idlab.obelisk.plugins.datastore.clickhouse.impl.querywrappers

import idlab.obelisk.definitions.data.EventsQuery
import idlab.obelisk.definitions.framework.OblxConfig
import idlab.obelisk.plugins.datastore.clickhouse.impl.utils.localTableName

internal open class DeleteEventsQueryWrapper(q: EventsQuery, config: OblxConfig) : EventsQueryWrapper(q, config) {

    private val clusterName = config.chClusterName

    override fun selectStatement(): String {
        val clusterExpr = if (clusterName.isNotEmpty()) " ON CLUSTER '$clusterName' " else ""
        val select = "ALTER TABLE $localTableName $clusterExpr DELETE ${where()}"
        logger.debug { select }
        return select
    }

    // No timeouts for deletes...
    override fun timeoutExpr(): String {
        return ""
    }

}