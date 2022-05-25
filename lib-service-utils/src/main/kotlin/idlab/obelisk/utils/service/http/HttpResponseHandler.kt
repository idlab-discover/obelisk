package idlab.obelisk.utils.service.http

import io.vertx.core.json.JsonArray
import io.vertx.core.json.JsonObject
import io.vertx.reactivex.ext.web.RoutingContext

/**
 * Writes any object that can be serialized to JSON to the HTTP Response as the Response body.
 * The Response Content-Type is set to 'application/json'.
 */
fun writeHttpResponse(ctx: RoutingContext): (Any) -> Unit {
    return { ctx.json(if (it is List<*>) JsonArray(it) else JsonObject.mapFrom(it)) }
}