package idlab.obelisk.definitions

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import com.fasterxml.jackson.databind.annotation.JsonSerialize
import idlab.obelisk.annotations.api.OblxType
import idlab.obelisk.definitions.data.Location
import java.util.concurrent.TimeUnit

enum class MetricType(val typeSuffix: String) {
    NUMBER("::number"), BOOL("::bool"), STRING("::string"), JSON("::json"), NUMBER_ARRAY("::number[]");

    fun isComplex(): Boolean {
        return when (this) {
            NUMBER, NUMBER_ARRAY, BOOL -> false
            JSON, STRING -> true
        }
    }

    companion object {
        fun fromSuffix(typeSuffix: String): MetricType {
            return when (typeSuffix) {
                "::number" -> NUMBER
                "::bool" -> BOOL
                "::string" -> STRING
                "::json" -> JSON
                "::number[]" -> NUMBER_ARRAY
                else -> throw IllegalArgumentException("'$typeSuffix' is not a valid Metric type suffix!")
            }
        }
    }
}

private fun splitFQMetricId(fqMetricId: String): Pair<String, MetricType> {
    val splitCharIndex = fqMetricId.lastIndexOf("::")
    if (splitCharIndex > 0) {
        return Pair(
            fqMetricId.substring(0, splitCharIndex),
            MetricType.fromSuffix(fqMetricId.substring(splitCharIndex))
        )
    } else {
        throw IllegalArgumentException("Malformed Metric ID '$fqMetricId', expected a String metricName::metricType")
    }
}

@JsonSerialize(using = MetricIdSer::class)
@JsonDeserialize(using = MetricIdDes::class)
data class MetricName(val name: String, val type: MetricType) {
    private constructor() : this("_::number") // Hack to enable Pulsar Jackson shaded instance to work...
    constructor(fullyQualifiedId: String) : this(
        splitFQMetricId(fullyQualifiedId).first,
        splitFQMetricId(fullyQualifiedId).second
    )

    companion object {
        fun wildcard(types: Set<MetricType> = MetricType.values().toSet()): List<MetricName> {
            return types.map { MetricName("*", it) }
        }

        fun wildcard(vararg types: MetricType = MetricType.values()): List<MetricName> {
            return wildcard(types.toSet())
        }
    }

    @JsonIgnore
    fun getFullyQualifiedId(): String {
        return "$name${type.typeSuffix}"
    }

    @JsonIgnore
    fun isDerived(): Boolean {
        return name.lastIndexOf("::json") > 0
    }

    @JsonIgnore
    fun isWildcard(): Boolean {
        return name == "*"
    }

    @JsonIgnore
    fun getParent(): MetricName? {
        val splitCharIndex = name.lastIndexOf("::json/")
        return if (splitCharIndex > 0) {
            MetricName(name.substring(0, splitCharIndex), MetricType.JSON)
        } else {
            null
        }
    }

    @JsonIgnore
    fun getDerived(): MetricName? {
        val splitCharIndex = name.lastIndexOf("::json/")
        return if (splitCharIndex > 0) {
            MetricName(name.substring(splitCharIndex + "::json/".length), type)
        } else {
            null
        }
    }
}

data class PagedResult<T>(val items: List<T>, val cursor: String? = null)

/**
 * @param metrics a list of metrics, wildcard for all types if unspecified
 * @param datasets list of datasetIds
 */
@OblxType(rootType = false)
data class DataRange(
    val datasets: List<String>,
    val metrics: List<MetricName> = MetricName.wildcard()
) {

    constructor(datasets: List<String>) : this(datasets, MetricName.wildcard())

    companion object {

        val allData = DataRange(listOf())

        fun fromDatasetId(datasetId: String): DataRange {
            return DataRange(datasets = listOf(datasetId))
        }

    }
}

enum class TimestampPrecision(val unit: TimeUnit) {
    seconds(TimeUnit.SECONDS), milliseconds(TimeUnit.MILLISECONDS), microseconds(TimeUnit.MICROSECONDS);

    companion object {
        fun from(unit: TimeUnit): TimestampPrecision {
            return when (unit) {
                TimeUnit.SECONDS -> seconds
                TimeUnit.MILLISECONDS -> milliseconds
                TimeUnit.MICROSECONDS -> microseconds
                else -> throw IllegalArgumentException("$unit is not supported as TimestampPrecision!")
            }
        }
    }
}

enum class EventField {
    timestamp, dataset, metric, producer, source, value, tags, location, geohash, elevation, tsReceived
}

@JvmField
val SELECT_ALL = object : FilterExpression {
    override fun toString(): String {
        return "SelectAll()"
    }
}

@JsonSerialize(using = FilterExpressionSer::class)
@JsonDeserialize(using = FilterExpressionDes::class)
interface FilterExpression

data class Field(val path: List<String>) {
    constructor(vararg path: String) : this(path.toList())
    constructor(vararg path: Enum<*>) : this(path.map { it.toString() }.toList())

    companion object {
        private val pattern = "\\[(\\d+)]".toRegex()

        fun encodeArrayIndex(index: Int): String {
            return "[$index]"
        }

        fun isArrayIndex(pathStr: String): Boolean {
            return pathStr.matches(pattern)
        }

        fun decodeArrayIndex(pathStr: String): Int {
            return pattern.find(pathStr)?.groupValues?.getOrNull(1)?.toInt()
                ?: throw IllegalArgumentException(
                    "Invalid array index: $pathStr! ${
                        pathStr.matches("^\\d+$".toRegex()).let { if (it) "(Did you mean [$pathStr]?)\"" else "" }
                    }"
                )
        }
    }
}

// Logical operators
data class And(val operands: List<FilterExpression>) : FilterExpression {
    constructor(vararg exprs: FilterExpression) : this(exprs.asList())

    override fun hashCode(): Int {
        return operands.toSet().hashCode()
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as And

        if (operands.toSet() != other.operands.toSet()) return false

        return true
    }
}

data class Or(val operands: List<FilterExpression>) : FilterExpression {
    constructor(vararg exprs: FilterExpression) : this(exprs.asList())

    override fun hashCode(): Int {
        return operands.toSet().hashCode()
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Or

        if (operands.toSet() != other.operands.toSet()) return false

        return true
    }
}

data class Not(val operand: FilterExpression) : FilterExpression

// Filter that operates on the value of the targeted field.
interface FieldValueFilterExpression : FilterExpression {
    val field: Field

    fun getValueDataType(): MetricType
}

// Comparison operators
data class Eq(override val field: Field, val value: Any) : FieldValueFilterExpression {
    constructor(field: String, value: Any) : this(Field(field), value)
    constructor(field: Enum<*>, value: Any) : this(Field(field), value)

    override fun getValueDataType(): MetricType {
        return typeFor(value)
    }
}

data class Neq(override val field: Field, val value: Any) : FieldValueFilterExpression {
    constructor(field: String, value: Any) : this(Field(field), value)
    constructor(field: Enum<*>, value: Any) : this(Field(field), value)

    override fun getValueDataType(): MetricType {
        return typeFor(value)
    }
}

interface CompareFilter : FieldValueFilterExpression {
    val value: Any

    override fun getValueDataType(): MetricType {
        return typeFor(value)
    }
}

data class Gt(override val field: Field, override val value: Any) : CompareFilter {
    constructor(field: String, value: Any) : this(Field(field), value)
    constructor(field: Enum<*>, value: Any) : this(Field(field), value)
}

data class Gte(override val field: Field, override val value: Any) : CompareFilter {
    constructor(field: String, value: Any) : this(Field(field), value)
    constructor(field: Enum<*>, value: Any) : this(Field(field), value)
}

data class Lt(override val field: Field, override val value: Any) : CompareFilter {
    constructor(field: String, value: Any) : this(Field(field), value)
    constructor(field: Enum<*>, value: Any) : this(Field(field), value)
}

data class Lte(override val field: Field, override val value: Any) : CompareFilter {
    constructor(field: String, value: Any) : this(Field(field), value)
    constructor(field: Enum<*>, value: Any) : this(Field(field), value)
}

data class In(override val field: Field, val values: Set<Any>) : FieldValueFilterExpression {
    constructor(field: String, values: Set<Any>) : this(Field(field), values)
    constructor(field: Enum<*>, values: Set<Any>) : this(Field(field), values)

    override fun getValueDataType(): MetricType {
        return typeFor(values.first())
    }
}

// Options field -> currently only support for 'i' => ignore case
data class RegexMatches(override val field: Field, val regex: String, val options: String? = null) :
    FieldValueFilterExpression {
    constructor(field: String, regex: String, options: String? = null) : this(Field(field), regex, options)
    constructor(field: Enum<*>, regex: String, options: String? = null) : this(Field(field), regex, options)

    override fun getValueDataType(): MetricType {
        return MetricType.STRING
    }
}

// Obelisk specific operators
data class StartsWith(override val field: Field, val prefix: String) : FieldValueFilterExpression {
    constructor(field: String, prefix: String) : this(Field(field), prefix)
    constructor(field: Enum<*>, prefix: String) : this(Field(field), prefix)

    override fun getValueDataType(): MetricType {
        return MetricType.STRING
    }
}

data class HasField(val field: Field) : FilterExpression {
    constructor(field: String) : this(Field(field))
    constructor(field: Enum<*>) : this(Field(field))
}

data class HasTag(val tag: String) : FilterExpression
data class HasOneOfTags(val tags: List<String>) : FilterExpression {
    constructor(vararg tags: String) : this(tags.asList())
}

/**
 * This filter matches if the location of the to be evaluated data record is located within the circle specified by
 * the center point and the radius (in meters).
 */
data class LocationInCircle(val center: Location, val radius: Int) : FilterExpression

/**
 * This filter matches if the location of the to be evaluated data records is located within the polygon specified by the provided vertices
 *
 * Each vertex is represented by a pair of coordinates (a, b). Vertices should be specified in a clockwise or counterclockwise order. The minimum number of vertices is 3
 */
data class LocationInPolygon(val vertices: List<Coordinate2D>) : FilterExpression

@JsonSerialize(using = Coordinate2DSer::class)
@JsonDeserialize(using = Coordinate2DDes::class)
data class Coordinate2D(val x: Double, val y: Double) {
    enum class CoordinateOrder {
        X_Y, Y_X
    }

    companion object {

        fun fromDoubleArray(
            vararg coords: DoubleArray,
            coordinateOrder: CoordinateOrder = CoordinateOrder.X_Y
        ): List<Coordinate2D> {
            return coords.map {
                if (it.size == 2) {
                    Coordinate2D(it[0], it[1])
                } else {
                    throw IllegalArgumentException("A Coordinate2D array representation must have 2 elements, cannot parse $it!")
                }
            }
        }
    }
}

/**
 * Defines the part of a data query, streaming or export requests that is relevant for access control
 * Allows for a common way of processing access control for these requests
 */
interface DataRequest {
    val dataRange: DataRange
    var filter: FilterExpression
}

fun typeFor(value: Any): MetricType {
    return when (value) {
        is Number -> MetricType.NUMBER
        is String -> MetricType.STRING
        is Boolean -> MetricType.BOOL
        else -> throw IllegalArgumentException("Cannot determine Obelisk primitive type for value $value!")
    }
}

enum class Ordering {
    asc, desc
}
