package idlab.obelisk.utils.service.http

import io.vertx.reactivex.ext.web.Route
import io.vertx.reactivex.ext.web.Router
import io.vertx.reactivex.ext.web.handler.BodyHandler

fun Router.postWithBody(path: String): Route {
    this.route(path).handler(BodyHandler.create())
    return this.post(path)
}

fun Router.putWithBody(path: String): Route {
    this.route(path).handler(BodyHandler.create())
    return this.put(path)
}

fun Router.patchWithBody(path: String): Route {
    this.route(path).handler(BodyHandler.create())
    return this.patch(path)
}