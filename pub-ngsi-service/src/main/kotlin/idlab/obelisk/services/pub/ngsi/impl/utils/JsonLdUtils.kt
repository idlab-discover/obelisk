package idlab.obelisk.services.pub.ngsi.impl.utils

import com.github.jsonldjava.core.JsonLdOptions
import com.github.jsonldjava.core.JsonLdProcessor
import com.github.jsonldjava.utils.JsonUtils
import idlab.obelisk.services.pub.ngsi.Constants
import idlab.obelisk.services.pub.ngsi.NgsiLDService
import io.reactivex.Single
import io.vertx.core.http.HttpHeaders
import io.vertx.core.http.HttpMethod
import io.vertx.core.json.Json
import io.vertx.core.json.JsonArray
import io.vertx.core.json.JsonObject
import io.vertx.reactivex.ext.web.RoutingContext

object JsonLdUtils {
    /**
     * Extracts a JSON-LD object from the request body of the HTTP context and convert it to its fully qualified form
     * as a JsonObject (all context URIs are resolved.
     */
    fun resolveJsonLDBody(ctx: RoutingContext): Single<JsonObject> {
        return expandJsonLDEntity(ctx.bodyAsJson, getLDContext(ctx))
    }

    fun expandJsonLDEntity(rawEntity: JsonObject, ldContext: JsonObject): Single<JsonObject> {
        return NgsiLDService.vertx!!.rxExecuteBlocking<JsonObject> { promise ->
            try {
                val contextVal = JsonUtils.fromString(Json.encode(ldContext.getValue(Constants.LD_CONTEXT)))
                rawEntity.put(Constants.LD_CONTEXT, contextVal)
                val jsonBody = rawEntity.map
                val expandedBody = JsonLdProcessor.expand(jsonBody)
                promise.complete(JsonArray(JsonUtils.toString(expandedBody)).getJsonObject(0))
            } catch (err: Throwable) {
                promise.fail(InvalidRequest("Invalid JSON LD body!", err.message, err))
            }
        }.toSingle()
    }

    fun compactJsonLDResponse(response: JsonObject, ctx: RoutingContext): Single<JsonObject> {
        return compactJsonLDEntity(response, getLDContext(ctx))
    }

    fun compactJsonLDEntity(expandedEntity: JsonObject, ldContext: JsonObject): Single<JsonObject> {
        return NgsiLDService.vertx!!.rxExecuteBlocking<JsonObject> { promise ->
            try {
                val options = JsonLdOptions()
                options.omitDefault = false
                val compactForm = JsonLdProcessor.compact(
                    JsonUtils.fromString(expandedEntity.encode()),
                    JsonUtils.fromString(ldContext.encode()),
                    options
                )
                promise.complete(JsonObject(compactForm))
            } catch (err: Throwable) {
                promise.fail(InvalidRequest("Could not compact Response, invalid context!", err.message, err))
            }
        }.toSingle()
    }

    fun getLDContext(ctx: RoutingContext): JsonObject {
        return when (ctx.request().method()) {
            HttpMethod.GET, HttpMethod.DELETE -> getLDContextFromLinkHeader(ctx)
            HttpMethod.POST, HttpMethod.PATCH -> when (ctx.request().getHeader(HttpHeaders.CONTENT_TYPE)) {
                "application/json" -> getLDContextFromLinkHeader(ctx)
                Constants.JSON_LD_CONTENT_TYPE -> getLDContextFromBody(ctx)
                else -> throw UnsupportedMediaType()
            }
            else -> throw InvalidRequest("LD Context cannot be derived for this request!")
        }
    }

    fun getLDContextFromLinkHeader(ctx: RoutingContext): JsonObject {
        val link = ctx.request().getHeader("Link")?.let {
            try {
                it.substringAfter('<').substringBefore('>')
            } catch (err: Throwable) {
                throw InvalidRequest("Could not parse Link header to find LD Context!")
            }
        }
        return JsonObject().put(Constants.LD_CONTEXT, contextHelper(link ?: Constants.DEFAULT_LD_CONTEXT_URI))
    }

    fun getLDContextFromBody(ctx: RoutingContext): JsonObject {
        try {
            return getLDContextFromObject(ctx.bodyAsJson)
        } catch (err: Throwable) {
            throw InvalidRequest("Request body does not have an LD Context!")
        }
    }

    fun getLDContextFromObject(obj: JsonObject): JsonObject {
        try {
            return JsonObject().put(
                Constants.LD_CONTEXT,
                contextHelper(obj.getValue(Constants.LD_CONTEXT, Constants.DEFAULT_LD_CONTEXT_URI))
            )
        } catch (err: Throwable) {
            throw InvalidRequest("Object does not have an LD Context!")
        }
    }

    // Utility method, converts the context-value to a compound that always contains the default context
    private fun contextHelper(contextVal: Any): Any {
        return when (contextVal) {
            is String -> JsonArray().add(if (Constants.DEFAULT_LD_CONTEXT_URI == contextVal) contextVal else Constants.DEFAULT_LD_CONTEXT_URI)
            is JsonArray -> {
                JsonArray(contextVal.filterNot { it == Constants.DEFAULT_LD_CONTEXT_URI }).add(Constants.DEFAULT_LD_CONTEXT_URI)
            }
            is JsonObject -> {
                JsonArray().add(contextVal).add(Constants.DEFAULT_LD_CONTEXT_URI)
            }
            else -> throw IllegalArgumentException("Unacceptable context value!")
        }
    }

    fun getFQType(type: String, ctx: RoutingContext): String {
        return getFQType(type, getLDContext(ctx))
    }

    fun getFQType(type: String, ldContext: JsonObject): String {
        val jsonLd = ldContext.copy().put(Constants.FQ_ENTITY_TYPE, type)
        val expandedJsonLd = JsonLdProcessor.expand(JsonUtils.fromString(jsonLd.encode()))
        return JsonArray(JsonUtils.toString(expandedJsonLd)).getJsonObject(0).getJsonArray(Constants.FQ_ENTITY_TYPE)
            .getString(0)
    }

    fun getFQAttribute(attribute: String, ctx: RoutingContext): String {
        return getFQAttribute(attribute, getLDContext(ctx))
    }

    fun getFQAttribute(attribute: String, ldContext: JsonObject): String {
        val jsonLd = ldContext.copy().put(attribute, "")
        val expandedJsonLd = JsonLdProcessor.expand(JsonUtils.fromString(jsonLd.encode()))
        return JsonArray(JsonUtils.toString(expandedJsonLd)).getJsonObject(0).fieldNames().first()
    }

    fun compactFQType(type: String, ldContext: JsonObject): String {
        val jsonLd = JsonObject().put(type, "")
        val options = JsonLdOptions()
        options.omitDefault = false
        val compactedJsonLd = JsonLdProcessor.compact(
            JsonUtils.fromString(jsonLd.encode()),
            JsonUtils.fromString(ldContext.encode()),
            options
        )
        return JsonObject(JsonUtils.toString(compactedJsonLd)).fieldNames()
            .filterNot { it == Constants.LD_CONTEXT }.first()
    }

    fun compactFQAttribute(attribute: String, ldContext: JsonObject): String {
        val jsonLd = JsonObject().put(attribute, "")
        val options = JsonLdOptions()
        options.omitDefault = false
        val compactedJsonLd = JsonLdProcessor.compact(
            JsonUtils.fromString(jsonLd.encode()),
            JsonUtils.fromString(ldContext.encode()),
            options
        )
        return JsonObject(JsonUtils.toString(compactedJsonLd)).fieldNames()
            .filterNot { it == Constants.LD_CONTEXT }.first()
    }
}
