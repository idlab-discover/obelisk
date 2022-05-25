package idlab.obelisk.services.pub.ngsi.impl.utils

import idlab.obelisk.definitions.PagedResult
import idlab.obelisk.services.pub.ngsi.Constants
import idlab.obelisk.services.pub.ngsi.NgsiLDService
import idlab.obelisk.services.pub.ngsi.impl.model.*
import idlab.obelisk.services.pub.ngsi.impl.state.EntityOperationResult
import io.vertx.core.http.HttpHeaders
import io.vertx.core.json.JsonArray
import io.vertx.core.json.JsonObject
import io.vertx.reactivex.core.http.HttpServerResponse
import io.vertx.reactivex.ext.web.RoutingContext
import mu.KotlinLogging

object NgsiResponses {
    private val logger = KotlinLogging.logger { }

    fun entityCreated(ctx: RoutingContext): (EntityOperationResult) -> Unit {
        return { operationResult ->
            val entitiesPath =
                "${NgsiLDService.basePath.replaceFirst(":datasetId", ctx.pathParam("datasetId"))}/entities/"
            val entityUri = "$entitiesPath${operationResult.entityId}"
            ctx.response()
                .setStatusCode(201)
                .putContextLinkHeader(ctx)
                .putHeader(HttpHeaders.LOCATION.toString(), entityUri)
                .end()
        }
    }

    fun entitiesResult(
        ctx: RoutingContext,
        outputKeyValues: Boolean = false
    ): (PagedResult<JsonObject>) -> Unit {
        return { entitiesPage ->
            entitiesPage.cursor?.let { addCursor(ctx, it) }
            ctx.jsonOrLd(JsonArray(if (outputKeyValues) entitiesPage.items.map { convertToKeyValues(it) } else entitiesPage.items))
        }
    }

    fun entityResult(ctx: RoutingContext, outputKeyValues: Boolean = false): (JsonObject) -> Unit {
        return { entity -> ctx.jsonOrLd(if (outputKeyValues) convertToKeyValues(entity) else entity) }
    }

    fun entityOperationsResult(ctx: RoutingContext): (List<EntityOperationResult>) -> Unit {
        return { results ->
            val output = JsonObject()
            results.filter { it.error == null }.map { it.entityId }.takeIf { it.isNotEmpty() }
                ?.let { output.put("success", it) }
            results.filter { it.error != null }
                .map { JsonObject().put("entityId", it.entityId).put("error", NgsiError.wrap(it.error!!).toJson()) }
                .takeIf { it.isNotEmpty() }?.let { output.put("errors", it) }
            ctx.json(output)
        }
    }

    fun subscriptionCreated(ctx: RoutingContext): (String) -> Unit {
        return { subscriptionId ->
            val subscriptionsPath =
                "${NgsiLDService.basePath.replaceFirst(":datasetId", ctx.pathParam("datasetId"))}/subscriptions/"
            val subscriptionUri = "$subscriptionsPath${subscriptionId}"
            ctx.response()
                .setStatusCode(201)
                .putContextLinkHeader(ctx)
                .putHeader(HttpHeaders.LOCATION.toString(), subscriptionUri)
                .end()
        }
    }

    fun subscriptionResults(ctx: RoutingContext): (List<Subscription>) -> Unit {
        return { results -> ctx.jsonOrLd(JsonArray(results)) }
    }

    fun subscriptionResult(ctx: RoutingContext): (Subscription) -> Unit {
        return { result -> ctx.jsonOrLd(JsonObject.mapFrom(result)) }
    }

    fun typeResults(ctx: RoutingContext, details: Boolean): (PagedResult<EntityType>) -> Unit {
        return { typesPage ->
            typesPage.cursor?.let { addCursor(ctx, it) }
            ctx.jsonOrLd(if (details) JsonArray(typesPage.items) else EntityTypeList(typesPage.items))
        }
    }

    fun typeResult(ctx: RoutingContext): (EntityTypeInfo) -> Unit {
        return { entityType -> ctx.jsonOrLd(entityType) }
    }

    fun attributeResults(ctx: RoutingContext, details: Boolean): (PagedResult<Attribute>) -> Unit {
        return { typesPage ->
            typesPage.cursor?.let { addCursor(ctx, it) }
            ctx.jsonOrLd(if (details) JsonArray(typesPage.items) else AttributeList(typesPage.items))
        }
    }

    fun attributeResult(ctx: RoutingContext): (Attribute) -> Unit {
        return { attribute -> ctx.jsonOrLd(attribute) }
    }

    fun noContent(ctx: RoutingContext): () -> Unit {
        return { ctx.response().putContextLinkHeader(ctx).setStatusCode(204).end() }
    }

    fun error(ctx: RoutingContext): (Throwable) -> Unit {
        return { err -> writeError(ctx, err) }
    }

    fun writeError(ctx: RoutingContext, err: Throwable) {
        logger.warn(err) { "An error has occurred!" }
        NgsiError.wrap(err).writeToResponse(ctx)
    }

    private fun addCursor(ctx: RoutingContext, cursor: String) {
        /**
         * The spec does not specify a standard way of handling next links.
         * We choose to use the Link header with a next rel attribute
         * The linked uri equals the original request uri with an additional cursor query parameter (similar to how the cursor works in other Obelisk APIs)
         */
        // TODO: remove count parameter for follow up links! (or store result in next link, so it does not need to be calculated again)
        val linkUri = if (ctx.request().query() == null) {
            ctx.request().uri() + "?cursor=$cursor"
        } else if (ctx.request().query().contains("cursor=")) {
            ctx.request().uri().replace(Regex("cursor=[^&]*"), "cursor=$cursor")
        } else {
            ctx.request().uri() + "&cursor=$cursor"
        }
        ctx.response().putOrAppendHeader("Link", "<$linkUri>; rel=next")
    }
}

/**
 * See spec section 6.3.6
 *
 * There were some bugs with this code, when using a complex context for the request (e.g. mixed reference array + alias mapping)
 * It is also not clear what the spec expects us to do in this case...
 *
 * That is why I've put a try-catch around this block, when a failure occurs, we write the default context in the link header...
 */
internal fun HttpServerResponse.putContextLinkHeader(ctx: RoutingContext): HttpServerResponse {
    val link = try {
        when (val context = JsonLdUtils.getLDContext(ctx).getValue(Constants.LD_CONTEXT)) {
            is String -> context
            is JsonArray -> context.getString(0)
            else -> Constants.DEFAULT_LD_CONTEXT_URI
        }
    } catch (t: Throwable) {
        Constants.DEFAULT_LD_CONTEXT_URI
    }
    this.putOrAppendHeader(
        "Link",
        "<$link>; rel=\"http://www.w3.org/ns/json-ld/#context\"; type=\"${Constants.JSON_LD_CONTENT_TYPE}\""
    )
    return this
}

private fun HttpServerResponse.putOrAppendHeader(name: String, value: String): HttpServerResponse {
    if (this.headers().contains(name)) {
        this.putHeader(name, "${this.headers().get(name)}, $value")
    } else {
        this.putHeader(name, value)
    }
    return this
}

internal fun RoutingContext.jsonOrLd(json: Any) {
    when (this.acceptableContentType) {
        Constants.JSON_LD_CONTENT_TYPE -> {
            val context = JsonLdUtils.getLDContext(this).getValue(Constants.LD_CONTEXT)
            this.response()
                .putHeader(HttpHeaders.CONTENT_TYPE, Constants.JSON_LD_CONTENT_TYPE)
                .end(when (json) {
                    is JsonArray -> {
                        json.forEach { entry ->
                            (entry as JsonObject).takeUnless { it.containsKey(Constants.LD_CONTEXT) }
                                ?.apply { put(Constants.LD_CONTEXT, context) }
                        }
                        json.encode()
                    }
                    is JsonObject -> {
                        json.put(Constants.LD_CONTEXT, context)
                        json.encode()
                    }
                    else -> throw InternalError("Internal Error", "Could not serialize JSON-LD output!")
                })
        }
        else -> {
            this.response().putContextLinkHeader(this)
            this.json(json)
        }
    }
}
