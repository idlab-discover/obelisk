package idlab.obelisk.services.pub.ngsi.impl.endpoints

import idlab.obelisk.definitions.catalog.AccessManager
import idlab.obelisk.services.pub.ngsi.impl.state.NgsiStore
import idlab.obelisk.services.pub.ngsi.impl.state.ReadContext
import idlab.obelisk.services.pub.ngsi.impl.utils.*
import io.reactivex.rxkotlin.subscribeBy
import io.vertx.reactivex.ext.web.RoutingContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class Metadata @Inject constructor(
    private val accessManager: AccessManager,
    private val ngsiStore: NgsiStore
) : AbstractEndpointsHandler() {

    fun getTypes(ctx: RoutingContext) {
        val details = ctx.request().getParam("details")?.toBooleanStrictOrNull() ?: false
        getTokenWithReadPermission(accessManager, ctx)
            .flatMap {
                val scope = metaQuery(ctx)
                ngsiStore.getTypes(scope, details, JsonLdUtils.getLDContext(ctx))
            }
            .subscribeBy(
                onSuccess = NgsiResponses.typeResults(ctx, details),
                onError = NgsiResponses.error(ctx)
            )
    }

    fun getType(ctx: RoutingContext) {
        getTokenWithReadPermission(accessManager, ctx)
            .flatMap {
                val scope = metaQuery(ctx)
                ngsiStore.getType(scope, ctx.pathParam("type"), JsonLdUtils.getLDContext(ctx))
            }
            .subscribeBy(
                onSuccess = NgsiResponses.typeResult(ctx),
                onError = NgsiResponses.error(ctx)
            )
    }

    fun getAttributes(ctx: RoutingContext) {
        val details = ctx.request().getParam("details")?.toBooleanStrictOrNull() ?: false
        getTokenWithReadPermission(accessManager, ctx)
            .flatMap {
                val scope = metaQuery(ctx)
                ngsiStore.getAttributes(scope, details, JsonLdUtils.getLDContext(ctx))
            }
            .subscribeBy(
                onSuccess = NgsiResponses.attributeResults(ctx, details),
                onError = NgsiResponses.error(ctx)
            )
    }

    fun getAttribute(ctx: RoutingContext) {
        getTokenWithReadPermission(accessManager, ctx)
            .flatMap {
                val scope = metaQuery(ctx)
                ngsiStore.getAttribute(scope, ctx.pathParam("attrId"), JsonLdUtils.getLDContext(ctx))
            }
            .subscribeBy(
                onSuccess = NgsiResponses.attributeResult(ctx),
                onError = NgsiResponses.error(ctx)
            )
    }

}
