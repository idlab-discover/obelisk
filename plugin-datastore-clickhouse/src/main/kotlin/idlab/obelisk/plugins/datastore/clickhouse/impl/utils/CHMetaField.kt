package idlab.obelisk.plugins.datastore.clickhouse.impl.utils

import idlab.obelisk.definitions.data.MetaField

internal enum class CHMetaField(
    private val expr: String? = null
) {
    dataset,
    metric_type,
    metric_name,
    user,
    client,
    source,
    started("min(toUnixTimestamp64Milli(started))"),
    lastUpdate("max(toUnixTimestamp64Milli(lastUpdate))"),
    count("sum(count)"),
    avgIngestRate("ifNotFinite(divide(sum(count), dateDiff('second', min(started), max(lastUpdate))), 0.0)");

    fun toExpr(): String {
        return expr ?: this.toString()
    }

    companion object {

        fun from(metaField: MetaField): List<CHMetaField> {
            return when (metaField) {
                MetaField.dataset -> listOf(dataset)
                MetaField.lastUpdate -> listOf(lastUpdate)
                MetaField.metric -> listOf(metric_name, metric_type)
                MetaField.producer -> listOf(user, client)
                MetaField.source -> listOf(source)
                MetaField.count -> listOf(count)
                MetaField.started -> listOf(started)
                MetaField.avgIngestRate -> listOf(avgIngestRate)
                MetaField.metric_id -> listOf(metric_name)
                MetaField.metric_type -> listOf(metric_type)
            }
        }
    }
}