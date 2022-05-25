package idlab.obelisk.utils.mongo

import idlab.obelisk.definitions.Ordering
import io.vertx.core.json.JsonArray
import io.vertx.core.json.JsonObject

internal const val UNICODE_DOT = '\uff0E'

object Conversions {

    fun escapeDocument(doc: JsonObject): JsonObject {
        doc.fieldNames().forEach { field ->
            val child = doc.getValue(field)
            if (child is JsonArray) {
                child.filterIsInstance<JsonObject>().forEach { escapeDocument(it) }
            }
            if (child is JsonObject) {
                doc.put(field, escapeDocument(child))
            }
            if ('.' in field) {
                doc.put(field.replace('.', UNICODE_DOT), doc.getValue(field))
                doc.remove(field)
            }
        }
        return doc
    }

    fun unescapeDocument(doc: JsonObject): JsonObject {
        doc.fieldNames().forEach { field ->
            val child = doc.getValue(field)
            if (child is JsonArray) {
                child.filterIsInstance<JsonObject>().forEach { unescapeDocument(it) }
            }
            if (child is JsonObject) {
                doc.put(field, unescapeDocument(child))
            }
            if (UNICODE_DOT in field) {
                doc.put(field.replace(UNICODE_DOT, '.'), doc.getValue(field))
                doc.remove(field)
            }
        }
        return doc
    }

}

fun fromSortMap(sortMap: Map<Enum<*>, Ordering>): JsonObject {
    val result = JsonObject()
    sortMap.forEach { (field, order) ->
        result.put(
            field.toString(), when (order) {
                Ordering.asc -> 1
                Ordering.desc -> -1
            }
        )
    }
    return result
}