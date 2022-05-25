package idlab.obelisk.plugins.datastore.clickhouse.impl.querywrappers

import idlab.obelisk.definitions.*
import idlab.obelisk.definitions.data.DataQuery
import idlab.obelisk.definitions.data.EventsQuery
import idlab.obelisk.definitions.data.MetricRecord
import idlab.obelisk.definitions.framework.OblxConfig
import idlab.obelisk.plugins.datastore.clickhouse.impl.utils.CHField
import idlab.obelisk.plugins.datastore.clickhouse.impl.utils.OptimizedCursor
import idlab.obelisk.plugins.datastore.clickhouse.impl.utils.toCHDateTimeMicroseconds
import idlab.obelisk.utils.service.utils.toMus

const val ENV_ENABLE_OPTIMIZED_CURSORS = "ENABLE_OPTIMIZED_CURSORS"
const val DEFAULT_ENABLE_OPTIMIZED_CURSORS = true
const val LARGE_EVENTS_QUERY_OFFSET = 10000

internal abstract class AbstractDataQueryWrapper<Q : DataQuery, T : MetricRecord>(
    q: Q,
    protected val config: OblxConfig
) :
    AbstractQueryWrapper<Q, T>(q) {

    private val enableOptimizedCursors =
        System.getenv(ENV_ENABLE_OPTIMIZED_CURSORS)?.toBoolean() ?: DEFAULT_ENABLE_OPTIMIZED_CURSORS
    private val optimizedCursor = if (enableOptimizedCursors) q.cursor?.let { OptimizedCursor.from(it) } else null

    protected val targetMetricTypes = q.dataRange.metrics.map { it.type }.distinct()
        .apply { if (isEmpty()) throw IllegalArgumentException("Invalid dataRange for query: metrics cannot be empty!") }

    override fun toPagedResult(results: List<T>): PagedResult<T> {
        return if (enableOptimizedCursors) {
            val cursor = if (results.size > q.limit) OptimizedCursor.generate(q, results).encode() else null
            PagedResult(items = (if (cursor != null) results.dropLast(1) else results), cursor = cursor)
        } else {
            super.toPagedResult(results)
        }
    }

    override fun limit(): String {
        return if (enableOptimizedCursors) {
            q.limit.let { "LIMIT ${optimizedCursor?.offset?.let { offset -> "$offset," } ?: ""}${it + 1}" }
        } else {
            super.limit()
        }
    }

    override fun where(extraFilters: List<FilterExpression>): String {
        val totalExtras =
            timeRange().plus(extraFilters).let {
                val cursorFilter = optimizedCursor?.asFilters(q)
                if (enableOptimizedCursors && cursorFilter != null) {
                    it.plus(cursorFilter)
                } else {
                    it
                }
            }
        return super.where(totalExtras)
    }

    open fun orderBy(): String {
        return if (q.orderBy.fields.isNotEmpty()) {
            val orderingStr = if (q.orderBy.ordering == Ordering.asc) "" else " DESC"
            val orderFields = q.orderBy.fields.flatMap { CHField.from(it) }.distinct()
                .joinToString(separator = "$orderingStr, ", postfix = orderingStr)
            "ORDER BY $orderFields"
        } else {
            ""
        }
    }

    protected fun timeRange(): List<FilterExpression> {
        return listOfNotNull(
            q.from?.let { Gte(CHField.timestamp.toString(), toCHDateTimeMicroseconds(it.toMus())) },
            q.to?.let { Lt(CHField.timestamp.toString(), toCHDateTimeMicroseconds(it.toMus())) }
        )
    }

    // Generates a timeout expression for the query based on the requested parameters
    protected open fun timeoutExpr(): String {
        // Apply longer timeouts for event queries that request large pages
        return "SETTINGS max_execution_time=${if (q is EventsQuery && q.limit >= LARGE_EVENTS_QUERY_OFFSET) config.chMaxTimeoutSeconds else config.chMinTimeoutSeconds}"
    }

}
