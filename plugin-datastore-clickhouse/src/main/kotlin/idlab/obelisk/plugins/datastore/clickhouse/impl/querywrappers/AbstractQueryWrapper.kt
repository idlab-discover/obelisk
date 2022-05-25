package idlab.obelisk.plugins.datastore.clickhouse.impl.querywrappers

import idlab.obelisk.definitions.*
import idlab.obelisk.definitions.data.MetaField
import idlab.obelisk.definitions.data.Query
import idlab.obelisk.plugins.datastore.clickhouse.impl.utils.CHField
import idlab.obelisk.plugins.datastore.clickhouse.impl.utils.CHMetaField
import idlab.obelisk.plugins.datastore.clickhouse.impl.utils.Cursor
import mu.KotlinLogging

internal abstract class AbstractQueryWrapper<Q : Query, T>(val q: Q) : QueryWrapper<T> {

    protected val logger = KotlinLogging.logger { }

    override fun toPagedResult(results: List<T>): PagedResult<T> {
        val cursor = if (results.size > q.limit) {
            val cursor = Cursor(q.cursor)
            cursor.increment(q.limit)
            cursor.encode()
        } else {
            null
        }
        return PagedResult(items = (if (cursor != null) results.dropLast(1) else results), cursor = cursor)
    }

    open fun limit(): String {
        val cursor = q.cursor?.let { Cursor(it) }
        return q.limit.let { "LIMIT ${cursor?.offset?.let { offset -> "$offset," } ?: ""}${it + 1}" }
    }

    open fun where(extraFilters: List<FilterExpression> = emptyList()): String {
        val filters = mutableListOf<FilterExpression>()
        if (q.dataRange.datasets.isNotEmpty()) {
            filters.add(In(CHField.dataset.toString(), q.dataRange.datasets.toSet()))
        }
        filters.add(metricFilter())
        filters.addAll(extraFilters)
        filters.add(q.filter)

        val queryStr = convertFilter(And(filters.filter { it != SELECT_ALL }))
        return if (queryStr.isNotBlank()) "WHERE ${queryStr}" else ""
    }

    protected fun metricFilter(): FilterExpression {
        if (q.dataRange.metrics.map { it.type }.toSet() == MetricType.values().toSet()) {
            // All metric types are included, so just filter by name
            val names = q.dataRange.metrics.filterNot { it.isWildcard() }
                .map { if (it.isDerived()) it.getParent()!!.name else it.name }.toSet()
            return if (names.isNotEmpty()) In(CHField.metric_name, names) else SELECT_ALL
        } else {
            val groupedByType = q.dataRange.metrics.groupBy { if (it.isDerived()) it.getParent()!!.type else it.type }
            return Or(
                groupedByType.entries.map { e ->
                    if (e.value.any { it.isWildcard() }) {
                        Eq(CHField.metric_type.toString(), e.key.typeSuffix)
                    } else {
                        And(
                            Eq(CHField.metric_type.toString(), e.key.typeSuffix),
                            In(
                                CHField.metric_name.toString(),
                                e.value.map { if (it.isDerived()) it.getParent()!!.name else it.name }.toSet()
                            )
                        )
                    }
                }
            )
        }
    }

    protected fun convertFilter(filter: FilterExpression): String {
        return when (filter) {
            SELECT_ALL -> ""
            is And -> {
                val nonEmptyFilters = filter.operands.map { convertFilter(it) }.filter { it.isNotEmpty() }
                if (nonEmptyFilters.isNotEmpty()) nonEmptyFilters
                    .joinToString(
                        separator = " AND ",
                        prefix = "(",
                        postfix = ")"
                    ) else ""
            }
            is Or -> {
                val nonEmptyFilters = filter.operands.map { convertFilter(it) }.filter { it.isNotEmpty() }
                if (nonEmptyFilters.isNotEmpty()) nonEmptyFilters
                    .joinToString(
                        separator = " OR ",
                        prefix = "(",
                        postfix = ")"
                    ) else ""
            }
            is Not -> {
                val operand = convertFilter(filter.operand)
                if (operand.isNotEmpty()) "NOT $operand" else ""
            }
            is Eq -> "${validatedField(filter)} = ${sqlVal(filter.value)}"
            is Neq -> "${validatedField(filter)} != ${sqlVal(filter.value)}"
            is Lt -> "${validatedField(filter)} < ${sqlVal(filter.value)}"
            is Lte -> "${validatedField(filter)} <= ${sqlVal(filter.value)}"
            is Gt -> "${validatedField(filter)} > ${sqlVal(filter.value)}"
            is Gte -> "${validatedField(filter)} >= ${sqlVal(filter.value)}"
            is In -> "${validatedField(filter)} IN(${filter.values.joinToString(transform = ::sqlVal)})"
            is RegexMatches -> {
                val regex = if (filter.options?.contains('i', true) == true) {
                    "(?i)${filter.regex}"
                } else {
                    filter.regex
                }
                "match(${validatedField(filter)}, '${regex}')"
            }
            is StartsWith -> "startsWith(${validatedField(filter)}, '${filter.prefix}')"
            is HasField -> "isNotNull(${validatedField(filter.field)})"
            is HasTag -> "has(tags, '${filter.tag}')"
            is HasOneOfTags -> "hasAny(tags, ${
                filter.tags.joinToString(
                    separator = ", ",
                    prefix = "[",
                    postfix = "]",
                    transform = { "'$it'" }
                )
            })"
            is LocationInCircle -> "greatCircleDistance(${CHField.lng}, ${CHField.lat}, ${filter.center.lng}, ${filter.center.lat}) <= ${filter.radius}"
            is LocationInPolygon -> "pointInPolygon((${CHField.lng}, ${CHField.lat}), ${
                filter.vertices.joinToString(",") { "(${it.y},${it.x})" }
            })"
            else -> throw IllegalArgumentException("Unsupported Filter Expression (${filter::class.java.name})!")

        }
    }

    private fun validatedField(field: Field): String {
        return when (field.path) {
            listOf(EventField.producer.toString(), "userId") -> "user"
            listOf(EventField.producer.toString(), "clientId") -> "client"
            listOf(EventField.location.toString(), "lat") -> "lat"
            listOf(EventField.location.toString(), "lng") -> "lng"
            listOf(EventField.metric.toString()) -> "concat(${CHField.metric_name}, ${CHField.metric_type})"
            else -> {
                try {
                    CHField.valueOf(field.path.first()).toString()
                } catch (err: IllegalArgumentException) {
                    CHMetaField.valueOf(field.path.first()).toExpr()
                }
            }
        }
    }

    private fun validatedField(filter: FieldValueFilterExpression): String {
        return when (filter.field.path) {
            listOf(EventField.producer.toString(), "userId") -> "user"
            listOf(EventField.producer.toString(), "clientId") -> "client"
            listOf(EventField.location.toString(), "lat") -> "lat"
            listOf(EventField.location.toString(), "lng") -> "lng"
            listOf(EventField.metric.toString()) -> "concat(${CHField.metric_name}, ${CHField.metric_type})"
            // TODO: find a better way of doing this
            listOf(MetaField.started.toString()) -> "toUnixTimestamp64Milli(started)"
            listOf(MetaField.lastUpdate.toString()) -> "toUnixTimestamp64Milli(lastUpdate)"
            else -> {
                if (filter.field.path.size > 1 && filter.field.path.first() == EventField.value.toString()) {
                    /**
                     * If the field path contains more than one entry and the first entry refers to the value field,
                     * we are dealing with a lookup expression in the value of a Metric of type ::json
                     */
                    CHField.jsonLookup(filter.field.path.drop(1), filter.getValueDataType())
                } else {
                    // Else we just parse the first entry of the path as an instance of CHField
                    try {
                        CHField.valueOf(filter.field.path.first()).toString()
                    } catch (err: IllegalArgumentException) {
                        CHMetaField.valueOf(filter.field.path.first()).toString()
                    }
                }
            }
        }
    }

    protected fun sqlVal(value: Any): String {
        return if (value is String) "'$value'" else "$value"
    }

}