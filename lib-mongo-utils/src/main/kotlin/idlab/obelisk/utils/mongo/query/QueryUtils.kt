package idlab.obelisk.utils.mongo.query

import idlab.obelisk.definitions.*
import idlab.obelisk.utils.mongo.UNICODE_DOT
import io.vertx.core.json.JsonArray
import io.vertx.core.json.JsonObject
import java.lang.IllegalArgumentException

private fun Field.mongoField(): String {
    return if (this.path.size == 1 && this.path.first() == "id") {
        "_id"
    } else {
        this.path.map { it.replace('.', UNICODE_DOT) }.joinToString(separator = ".")
    }
}

fun fromFilter(filter: FilterExpression, negate: Boolean = false): JsonObject {
    return when (filter) {
        is And -> {
            val operandsArr = filter.operands.map { fromFilter(it, negate) }.toTypedArray()
            if (negate) or(*operandsArr) else and(*operandsArr)
        }
        is Or -> {
            val operandsArr = filter.operands.map { fromFilter(it, negate) }.toTypedArray()
            if (negate) and(*operandsArr) else or(*operandsArr)
        }
        is Not -> fromFilter(filter.operand, !negate)
        is Eq -> eq(filter.field.mongoField(), filter.value, negate)
        is Gt -> gt(filter.field.mongoField(), filter.value, negate)
        is Lt -> lt(filter.field.mongoField(), filter.value, negate)
        is Gte -> gte(filter.field.mongoField(), filter.value, negate)
        is Lte -> lte(filter.field.mongoField(), filter.value, negate)
        is Neq -> if (negate) eq(filter.field.mongoField(), filter.value) else neq(
            filter.field.mongoField(),
            filter.value
        )
        is In -> `in`(filter.field.mongoField(), filter.values.toList(), negate)
        is StartsWith -> regex(filter.field.mongoField(), "^${filter.prefix}", negate = negate)
        is RegexMatches -> regex(filter.field.mongoField(), filter.regex, filter.options, negate)
        SELECT_ALL -> JsonObject()
        else -> throw IllegalArgumentException("FilterExpression ${filter.javaClass.simpleName} is currently not supported on the MongoDB backend!")
    }
}

fun main() {
    println(fromFilter(RegexMatches("name", "bLargh", "i")).encodePrettily())
}

fun withId(id: String): JsonObject {
    return JsonObject().put("_id", id)
}

fun byExample(vararg queryFields: Pair<String, Any?>): JsonObject {
    val result = JsonObject()
    queryFields.forEach { result.put(it.first, it.second) }
    return result
}

fun and(vararg queries: JsonObject): JsonObject {
    val operands = JsonArray()
    queries.forEach { operands.add(it) }
    return JsonObject().put("\$and", operands)
}

fun or(vararg queries: JsonObject): JsonObject {
    val operands = JsonArray()
    queries.forEach { operands.add(it) }
    return JsonObject().put("\$or", operands)
}

fun not(query: JsonObject): JsonObject {
    return JsonObject().put("\$not", query)
}

fun `in`(field: String, values: List<Any>, negate: Boolean = false): JsonObject {
    val op = JsonObject().put("\$in", JsonArray(values))
    return JsonObject().put(field, if (negate) not(op) else op)
}

fun eq(field: String, value: Any, negate: Boolean = false): JsonObject {
    val op = JsonObject().put("\$eq", value)
    return JsonObject().put(field, if (negate) not(op) else op)
}

fun neq(field: String, value: Any): JsonObject {
    return JsonObject().put(field, JsonObject().put("\$ne", value))
}

fun gt(field: String, value: Any, negate: Boolean = false): JsonObject {
    val op = JsonObject().put("\$gt", value)
    return JsonObject().put(field, if (negate) not(op) else op)
}

fun gte(field: String, value: Any, negate: Boolean = false): JsonObject {
    val op = JsonObject().put("\$gte", value)
    return JsonObject().put(field, if (negate) not(op) else op)
}

fun lt(field: String, value: Any, negate: Boolean = false): JsonObject {
    val op = JsonObject().put("\$lt", value)
    return JsonObject().put(field, if (negate) not(op) else op)
}

fun lte(field: String, value: Any, negate: Boolean = false): JsonObject {
    val op = JsonObject().put("\$lte", value)
    return JsonObject().put(field, if (negate) not(op) else op)
}

fun regex(field: String, regex: String, options: String? = null, negate: Boolean = false): JsonObject {
    val tmp = JsonObject().put("\$regex", regex)
    if (options?.contains("i", false) == true) {
        tmp.put("\$options", "i")
    }
    return JsonObject().put(field, if (negate) not(tmp) else tmp)
}

