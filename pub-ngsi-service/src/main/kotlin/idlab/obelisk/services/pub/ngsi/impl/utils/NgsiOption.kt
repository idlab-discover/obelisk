package idlab.obelisk.services.pub.ngsi.impl.utils

import io.vertx.reactivex.ext.web.RoutingContext

enum class NgsiOption {
    noOverwrite, sysAttrs, keyValues;

    fun hasOption(ctx: RoutingContext): Boolean {
        return ctx.request().getParam("options")?.split(',')?.map { valueOf(it) }?.contains(this) ?: false
    }
}