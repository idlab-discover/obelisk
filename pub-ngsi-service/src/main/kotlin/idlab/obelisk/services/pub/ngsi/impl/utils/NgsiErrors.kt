package idlab.obelisk.services.pub.ngsi.impl.utils

import idlab.obelisk.utils.service.http.AuthorizationException
import io.vertx.core.json.JsonObject
import io.vertx.kotlin.core.json.json
import io.vertx.kotlin.core.json.obj
import io.vertx.reactivex.ext.web.RoutingContext
import java.lang.IllegalArgumentException

private const val BASE_URI = "https://uri.etsi.org/ngsi-ld/errors"

open class NgsiError(val status: Int, val type: String, val title: String, val detail: String? = null, cause: Throwable? = null) : RuntimeException(detail, cause) {
    companion object {
        fun wrap(err: Throwable): NgsiError {
            return when (err) {
                is NgsiError -> err
                is AuthorizationException -> NotAuthorized(cause = err)
                is IllegalArgumentException -> InvalidRequest("Illegal Argument", err.message, err)
                is NoSuchElementException -> ResourceNotFound("Resource Not Found", err.message, err)
                is UnsupportedOperationException, is NotImplementedError -> OperationNotSupported("Operation not Supported", err.message, err)
                else -> InternalError("An unexpected Error occurred", err.message, err)
            }
        }
    }

    fun toJson(): JsonObject {
        return json {
            obj(
                    "type" to type,
                    "title" to title,
                    "detail" to (detail ?: "")
            )
        }
    }

    fun writeToResponse(ctx: RoutingContext) {
        ctx.response().setStatusCode(status)
        ctx.json(toJson())
    }
}

class NotAuthorized(detail: String? = "Make sure you are authenticated and have the appropriate permissions to access this resource!", cause: Throwable? = null) : NgsiError(401, "$BASE_URI/NotAuthorized", "Not Authorized", detail, cause)
class InvalidRequest(title: String, detail: String? = null, cause: Throwable? = null) : NgsiError(400, "$BASE_URI/InvalidRequest", title, detail, cause)
class BadRequestData(title: String, detail: String? = null, cause: Throwable? = null) : NgsiError(400, "$BASE_URI/BadRequestData", title, detail, cause)
class AlreadyExists(title: String, detail: String? = null, cause: Throwable? = null) : NgsiError(409, "$BASE_URI/AlreadyExists", title, detail, cause)
class OperationNotSupported(title: String, detail: String? = null, cause: Throwable? = null) : NgsiError(422, "$BASE_URI/OperationNotSupported", title, detail, cause)
class ResourceNotFound(title: String, detail: String? = null, cause: Throwable? = null) : NgsiError(404, "$BASE_URI/ResourceNotFound", title, detail, cause)
class InternalError(title: String, detail: String? = null, cause: Throwable? = null) : NgsiError(500, "$BASE_URI/InternalError", title, detail, cause)
class UnsupportedMediaType(title: String = "Unsupported Media Type", detail: String? = "The Content-Type of the request body should be application/json or application/ld+json") : NgsiError(415, "$BASE_URI/UnsupportedMediaType", title, detail, null)