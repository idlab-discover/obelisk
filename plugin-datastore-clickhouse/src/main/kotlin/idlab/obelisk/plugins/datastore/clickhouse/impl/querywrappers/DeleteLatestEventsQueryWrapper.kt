package idlab.obelisk.plugins.datastore.clickhouse.impl.querywrappers

import idlab.obelisk.definitions.FilterExpression
import idlab.obelisk.definitions.data.EventsQuery
import idlab.obelisk.definitions.framework.OblxConfig
import idlab.obelisk.plugins.datastore.clickhouse.impl.utils.CHField
import idlab.obelisk.plugins.datastore.clickhouse.impl.utils.tableName

internal class DeleteLatestEventsQueryWrapper(q: EventsQuery, config: OblxConfig) :
    DeleteEventsQueryWrapper(q, config) {

    override fun where(extraFilters: List<FilterExpression>): String {
        val where = super.where(extraFilters)
        val seriesId = CHField.timeSeriesIdFields().joinToString()
        val maxTsGroupedBySeriesId =
            "SELECT max(${CHField.timestamp}), $seriesId FROM $tableName GROUP BY ($seriesId)"
        return "WHERE (${CHField.timestamp}, $seriesId) IN ($maxTsGroupedBySeriesId) ${
            where.replaceFirst(
                "WHERE",
                "AND"
            )
        }"
    }

}