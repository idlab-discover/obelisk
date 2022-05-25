package idlab.obelisk.services.pub.auth.model

import idlab.obelisk.utils.service.http.BadRequestException
import idlab.obelisk.utils.service.http.HttpError
import io.vertx.core.http.HttpHeaders
import io.vertx.core.http.HttpMethod
import io.vertx.reactivex.ext.web.RoutingContext

object AuthParams {
    val LOGIN_HINT = "login_hint"
    val STATE = "state"
    val REDIRECT_URI = "redirect_uri"
    val CLIENT_ID = "client_id"
    val CLIENT_SECRET = "client_secret"
    val RESPONSE_TYPE = "response_type"
    val CODE_CHALLENGE = "code_challenge"
    val CODE_CHALLENGE_METHOD = "code_challenge_method"
    val SCOPE = "scope"
    val CODE = "code"
    val AUTH_CODE = "acode"
    val GRANT_TYPE = "grant_type"
    val CODE_VERIFIER = "code_verifier"
    val PROMPT = "prompt"
    val ID_TOKEN = "id_token"
    val REMEMBER_ME = "remember_me"
    val ACCESS_TYPE = "access_type"

    fun getParam(ctx: RoutingContext, param: String): String? {

        return when (ctx.request().method()) {
            HttpMethod.POST -> {
                return when (ctx.request().getHeader(HttpHeaders.CONTENT_TYPE)) {
                    "application/x-www-form-urlencoded" -> ctx.request().getFormAttribute(param)
                    "application/json" -> ctx.bodyAsJson.getString(param)
                    else -> throw BadRequestException("Unsupported Content-Type! Try application/x-www-form-urlencoded or application/json..")
                }
            }
            HttpMethod.GET -> ctx.request().getParam(param)
            else -> throw BadRequestException("HTTP Method not supported")
        }
    }
}