package idlab.obelisk.definitions

import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.core.JsonParseException
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.core.JsonToken
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.SerializerProvider
import com.fasterxml.jackson.databind.deser.std.StdDeserializer
import com.fasterxml.jackson.databind.ser.std.StdSerializer
import idlab.obelisk.definitions.data.Location
import io.vertx.core.json.Json
import io.vertx.core.json.JsonObject

/**
 * Custom serializers and deserializers required for Jackson
 */

class MetricIdSer : StdSerializer<MetricName>(MetricName::class.java) {

    override fun serialize(metricName: MetricName?, jsonGenerator: JsonGenerator?, provider: SerializerProvider?) {
        jsonGenerator?.writeString(metricName?.getFullyQualifiedId())
    }

}

class MetricIdDes : StdDeserializer<MetricName>(MetricName::class.java) {
    override fun deserialize(parser: JsonParser?, context: DeserializationContext?): MetricName {
        return MetricName(parser?.text.orEmpty())
    }

}

class Coordinate2DSer : StdSerializer<Coordinate2D>(Coordinate2D::class.java) {
    override fun serialize(coord: Coordinate2D?, jsonGenerator: JsonGenerator?, p2: SerializerProvider?) {
        if (jsonGenerator != null && coord != null) {
            jsonGenerator.writeStartArray(2)
            jsonGenerator.writeNumber(coord.x)
            jsonGenerator.writeNumber(coord.y)
            jsonGenerator.writeEndArray()
        }
    }

}

class Coordinate2DDes : StdDeserializer<Coordinate2D>(Coordinate2D::class.java) {
    override fun deserialize(parser: JsonParser?, p1: DeserializationContext?): Coordinate2D {
        parser!!.nextToken()
        val coords = parser!!.readValuesAs(Double::class.java).asSequence().toList()
        if (coords.size == 2) {
            return Coordinate2D(coords[0], coords[1])
        } else {
            throw IllegalArgumentException("A coordinate array must consist of exactly two number elements!")
        }
    }

}

object Operators {
    private const val PREFIX = "_"
    const val AND = "${PREFIX}and"
    const val OR = "${PREFIX}or"
    const val NOT = "${PREFIX}not"
    const val EQ = "${PREFIX}eq"
    const val NEQ = "${PREFIX}neq"
    const val GT = "${PREFIX}gt"
    const val GTE = "${PREFIX}gte"
    const val LT = "${PREFIX}lt"
    const val LTE = "${PREFIX}lte"
    const val IN = "${PREFIX}in"
    const val STARTSWITH = "${PREFIX}startsWith"
    const val REGEX = "${PREFIX}regex"
    const val REGEX_OPTIONS = "${PREFIX}options"
    const val EXISTS = "${PREFIX}exists"
    const val WITHTAG = "${PREFIX}withTag"
    const val WITHANYTAG = "${PREFIX}withAnyTag"
    const val LOCATIONINCIRCLE = "${PREFIX}locationInCircle"
    const val LOCATIONINPOLYGON = "${PREFIX}locationInPolygon"
}

internal fun Field.toJsonKey(): String {
    return this.path.joinToString(separator = "->")
}

internal fun fromJsonKey(jsonKey: String): Field {
    return Field(jsonKey.split("->"))
}

class FilterExpressionSer : StdSerializer<FilterExpression>(FilterExpression::class.java) {
    override fun serialize(filter: FilterExpression?, jsonGenerator: JsonGenerator?, provider: SerializerProvider?) {
        if (jsonGenerator != null) {
            when (filter) {
                SELECT_ALL -> {
                    jsonGenerator.writeStartObject()
                    jsonGenerator.writeEndObject()
                }
                is And -> writeNode(jsonGenerator, Operators.AND, *filter.operands.toTypedArray())
                is Or -> writeNode(jsonGenerator, Operators.OR, *filter.operands.toTypedArray())
                is Not -> writeNode(jsonGenerator, Operators.NOT, filter.operand, singleArg = true)
                is Eq -> writeLeaf(jsonGenerator, Operators.EQ, filter.field, filter.value)
                is Neq -> writeLeaf(jsonGenerator, Operators.NEQ, filter.field, filter.value)
                is Gt -> writeLeaf(jsonGenerator, Operators.GT, filter.field, filter.value)
                is Gte -> writeLeaf(jsonGenerator, Operators.GTE, filter.field, filter.value)
                is Lt -> writeLeaf(jsonGenerator, Operators.LT, filter.field, filter.value)
                is Lte -> writeLeaf(jsonGenerator, Operators.LTE, filter.field, filter.value)
                is In -> writeLeaf(jsonGenerator, Operators.IN, filter.field, filter.values)
                is StartsWith -> writeLeaf(jsonGenerator, Operators.STARTSWITH, filter.field, filter.prefix)
                is RegexMatches -> {
                    jsonGenerator.writeStartObject()
                    jsonGenerator.writeFieldName(filter.field.toJsonKey())
                    jsonGenerator.writeStartObject()
                    jsonGenerator.writeStringField(Operators.REGEX, filter.regex)
                    if (filter.options != null) {
                        jsonGenerator.writeStringField(Operators.REGEX_OPTIONS, filter.options)
                    }
                    jsonGenerator.writeEndObject()
                    jsonGenerator.writeEndObject()
                }
                is HasField -> {
                    jsonGenerator.writeStartObject()
                    jsonGenerator.writeStringField(Operators.EXISTS, filter.field.toJsonKey())
                    jsonGenerator.writeEndObject()
                }
                is HasTag -> {
                    jsonGenerator.writeStartObject()
                    jsonGenerator.writeStringField(Operators.WITHTAG, filter.tag)
                    jsonGenerator.writeEndObject()
                }
                is HasOneOfTags -> {
                    jsonGenerator.writeStartObject()
                    jsonGenerator.writeFieldName(Operators.WITHANYTAG)
                    jsonGenerator.writeStartArray(filter.tags.size)
                    filter.tags.forEach { jsonGenerator.writeString(it) }
                    jsonGenerator.writeEndArray()
                    jsonGenerator.writeEndObject()
                }
                is LocationInCircle -> {
                    jsonGenerator.writeStartObject()
                    jsonGenerator.writeFieldName(Operators.LOCATIONINCIRCLE)
                    jsonGenerator.writeStartObject()
                    jsonGenerator.writeObjectField("center", filter.center)
                    jsonGenerator.writeNumberField("radius", filter.radius)
                    jsonGenerator.writeEndObject()
                    jsonGenerator.writeEndObject()

                }
                is LocationInPolygon -> {
                    jsonGenerator.writeStartObject()
                    jsonGenerator.writeFieldName(Operators.LOCATIONINPOLYGON)
                    jsonGenerator.writeStartArray(filter.vertices.size)
                    filter.vertices.forEach { jsonGenerator.writeObject(it) }
                    jsonGenerator.writeEndArray()
                    jsonGenerator.writeEndObject()
                }
            }
        }
    }

    private fun writeNode(
        jsonGenerator: JsonGenerator,
        operator: String,
        vararg operands: FilterExpression,
        singleArg: Boolean = false
    ) {
        jsonGenerator.writeStartObject()
        jsonGenerator.writeFieldName(operator)
        if (!singleArg) {
            jsonGenerator.writeStartArray(operands.size)
        }
        operands.forEach { jsonGenerator.writeObject(it) }
        if (!singleArg) {
            jsonGenerator.writeEndArray()
        }
        jsonGenerator.writeEndObject()
    }

    private fun writeLeaf(jsonGenerator: JsonGenerator, operator: String, field: Field, value: Any) {
        jsonGenerator.writeStartObject()
        jsonGenerator.writeFieldName(field.toJsonKey())
        jsonGenerator.writeStartObject()
        when (value) {
            is Long -> jsonGenerator.writeNumberField(operator, value)
            is Int -> jsonGenerator.writeNumberField(operator, value)
            is Double -> jsonGenerator.writeNumberField(operator, value)
            is Float -> jsonGenerator.writeNumberField(operator, value)
            is Boolean -> jsonGenerator.writeBooleanField(operator, value)
            is String -> jsonGenerator.writeStringField(operator, value)
            is Set<*> -> {
                jsonGenerator.writeFieldName(operator)
                jsonGenerator.writeObject(value)
            }
            else -> throw IllegalArgumentException("Cannot use values of type ${value.javaClass} in filter expression field operations!")
        }
        jsonGenerator.writeEndObject()
        jsonGenerator.writeEndObject()
    }
}

class FilterExpressionDes : StdDeserializer<FilterExpression>(FilterExpression::class.java) {
    override fun deserialize(parser: JsonParser?, context: DeserializationContext?): FilterExpression {
        if (parser != null) {
            parser.nextToken()
            val fieldName = parser.valueAsString
            return if (fieldName == null) {
                SELECT_ALL
            } else {
                parser.nextToken()
                val result = when (fieldName) {
                    Operators.AND -> And(parseOperands(parser).filter { it != SELECT_ALL })
                    Operators.OR -> Or(parseOperands(parser).filter { it != SELECT_ALL })
                    Operators.NOT -> Not(parser.readValueAs(FilterExpression::class.java))
                    Operators.EXISTS -> HasField(parser.valueAsString)
                    Operators.WITHTAG -> HasTag(parser.valueAsString)
                    Operators.WITHANYTAG -> {
                        parser.nextToken()
                        val tags = parser.readValuesAs(String::class.java).asSequence().toList()
                        //parser.nextToken()
                        HasOneOfTags(tags)
                    }
                    Operators.LOCATIONINCIRCLE -> {
                        val map = parser.readValueAs(Map::class.java)
                        LocationInCircle(
                            map["center"]!!.let { JsonObject(it as Map<String, Any>).mapTo(Location::class.java) },
                            map["radius"]!!.let { it as Int })
                    }
                    Operators.LOCATIONINPOLYGON -> {
                        parser.nextToken()
                        val vertices = parser.readValuesAs(Coordinate2D::class.java).asSequence().toList()
                        parser.nextToken()
                        LocationInPolygon(vertices)
                    }
                    else -> parseFieldExpression(fromJsonKey(fieldName), parser)
                }
                parser.nextToken()
                result
            }
        } else {
            throw IllegalArgumentException("Parser cannot be null!")
        }
    }

    private fun parseOperands(parser: JsonParser): List<FilterExpression> {
        parser.nextToken()
        val operands = parser.readValuesAs(FilterExpression::class.java).asSequence().toList()
        //parser.nextToken() => original implementation but causes rest of input JSON to be ignored after a filter...
        return operands
    }

    private fun parseFieldExpression(fieldName: Field, parser: JsonParser): FilterExpression {
        val map = parser.readValueAs(LinkedHashMap::class.java)
        return when (val operator = map.keys.first()) {
            Operators.EQ -> Eq(fieldName, map[operator]!!)
            Operators.NEQ -> Neq(fieldName, map[operator]!!)
            Operators.GT -> Gt(fieldName, map[operator]!!)
            Operators.GTE -> Gte(fieldName, map[operator]!!)
            Operators.LT -> Lt(fieldName, map[operator]!!)
            Operators.LTE -> Lte(fieldName, map[operator]!!)
            Operators.IN -> In(fieldName, map[operator]!! as Set<Any>)
            Operators.STARTSWITH -> StartsWith(fieldName, map[operator]!!.toString())
            Operators.REGEX -> RegexMatches(
                fieldName,
                map[operator]!!.toString(),
                map[Operators.REGEX_OPTIONS]?.toString()
            )
            else -> throw JsonParseException(parser, "Unrecognized filter field operator: $operator!")
        }
    }

    private fun primitiveArray(parser: JsonParser): List<Any> {
        if (parser.currentToken == JsonToken.START_ARRAY) {
            parser.nextToken()
            val tmp = mutableListOf<Any>()
            while (parser.currentToken != JsonToken.END_ARRAY) {
                tmp.add(primitive(parser))
                parser.nextToken()
            }
            return tmp;
        } else {
            throw JsonParseException(parser, "Expected array!")
        }
    }

    private fun primitive(parser: JsonParser): Any {
        return when (parser.currentToken) {
            JsonToken.VALUE_NULL -> {
                throw JsonParseException(
                    parser,
                    "Field ${parser.parsingContext.currentName} cannot have null as value!"
                )
            }
            JsonToken.VALUE_STRING -> parser.valueAsString
            JsonToken.VALUE_FALSE, JsonToken.VALUE_TRUE -> parser.valueAsBoolean
            JsonToken.VALUE_NUMBER_FLOAT -> parser.valueAsDouble
            JsonToken.VALUE_NUMBER_INT -> parser.valueAsInt
            else -> throw JsonParseException(parser, "Expected string, boolean or number!")
        }
    }
}