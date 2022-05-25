package idlab.obelisk.services.pub.auth.oidc.provider

import idlab.obelisk.definitions.catalog.User
import idlab.obelisk.definitions.framework.OblxConfig
import idlab.obelisk.plugins.accessmanager.basic.store.AuthStore
import idlab.obelisk.services.pub.auth.ADMIN_EMAIL
import idlab.obelisk.services.pub.auth.ADMIN_EMAIL_PROP
import idlab.obelisk.services.pub.auth.AuthService
import idlab.obelisk.services.pub.auth.HTTP_BASE_PATH
import idlab.obelisk.services.pub.auth.model.AuthParams
import idlab.obelisk.plugins.accessmanager.basic.store.model.LoginState
import idlab.obelisk.services.pub.auth.model.Claims
import idlab.obelisk.services.pub.auth.model.CompositeState
import idlab.obelisk.plugins.accessmanager.basic.store.RedisAuthStore
import idlab.obelisk.services.pub.auth.util.Utils
import idlab.obelisk.utils.service.http.UnauthorizedException
import io.reactivex.Completable
import io.reactivex.Single
import io.vertx.core.http.HttpHeaders
import io.vertx.core.json.JsonObject
import io.vertx.reactivex.core.Vertx
import io.vertx.reactivex.ext.web.Router
import io.vertx.reactivex.ext.web.RoutingContext
import io.vertx.reactivex.ext.web.handler.BodyHandler
import mu.KotlinLogging
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException
import javax.inject.Inject
import javax.inject.Singleton

private const val ENV_LOCAL_IDP_CLIENT_SECRET = "LOCAL_IDP_CLIENT_SECRET"

private const val ADMIN_ID = "0";
private const val CLIENT_ID = "localIdpId"

@Singleton
class LocalIdentityProvider : IdentityProvider {
    private val vertx: Vertx
    private val config: OblxConfig
    private val basePath: String
    private val digest: MessageDigest?
    private val adminEmail: String
    private val memStore: AuthStore

    private val logger = KotlinLogging.logger { }

    @Inject
    constructor(vertx: Vertx, config: OblxConfig, memStore: AuthStore) {
        this.vertx = vertx;
        this.config = config;
        this.memStore = memStore
        basePath = config.getString(OblxConfig.HTTP_BASE_PATH_PROP, HTTP_BASE_PATH)
        digest = try {
            MessageDigest.getInstance("SHA-256")
        } catch (e: NoSuchAlgorithmException) {
            null
        }
        adminEmail = config.getString(ADMIN_EMAIL_PROP, ADMIN_EMAIL)
    }


    override val id: String
        get() = URLEncoder.encode(name.toLowerCase(), StandardCharsets.UTF_8)

    override val name: String
        get() = "Local"

    override val clientId: String
        get() = CLIENT_ID

    override val clientSecret: String?
        get() = config.getString(ENV_LOCAL_IDP_CLIENT_SECRET)?:throw RuntimeException("Please set env variable $ENV_LOCAL_IDP_CLIENT_SECRET")

    override val type: IdentityProvider.Type
        get() = IdentityProvider.Type.POST

    override val tokenIssuers: Set<String>
        get() = setOf(config.authPublicUri)

    override fun initHook(router: Router): Completable {
        router.post("$basePath/auth/local").handler(BodyHandler.create());
        router.post("$basePath/auth/local").handler(::localAuthHandler);
        return Completable.complete()
    }

    override fun authorizeURL(params: JsonObject): String {
        return config.authPublicUri + "?" + params.map.entries.map { "${it.key}=${it.value}" }.joinToString("&")
    }

    override fun rxAuthenticate(authInfo: JsonObject): Single<io.vertx.reactivex.ext.auth.User> {
        return memStore
            .rxGetLogin(authInfo.getString(AuthParams.CODE))
            .toSingle()
            .onErrorResumeNext { Single.error(UnauthorizedException("Invalid auth code", it)) }
            .flatMap {
                // Check if clientId and clientSecret are ok (this call comes from BasicOAuth provider, who knows the clientId and ClientSecret
                if (clientId.equals(authInfo.getString(AuthParams.CLIENT_ID))
                    && clientSecret.equals(authInfo.getString(AuthParams.CLIENT_SECRET))
                ) {
                    Single.just(gereateAdminIdpUser())
                } else {
                    Single.error(UnauthorizedException("Wrong client_id, client_secret or code."))
                }
            }
    }

    override fun retrieveEmail(idpUser: io.vertx.reactivex.ext.auth.User): String {
        return idpUser.principal().getString("email")
    }

    override fun transformToClaim(idpUser: io.vertx.reactivex.ext.auth.User, oblxUser: User): Claims {
        val p = idpUser.principal()
        val id = p.getString("id")
        val firstName = p.getString("firstName")
        val lastName = p.getString("lastName")
        val email = p.getString("email")
        val idp = p.getString("idp")
        val name = p.getString("name")
        return Claims.forUser(id, firstName, lastName, email, idp, name)
    }

    override fun convertToObeliskUser(idpUser: io.vertx.reactivex.ext.auth.User): User {
        val p = idpUser.principal()
        val firstName = p.getString("firstName")
        val lastName = p.getString("lastName")
        val email = p.getString("email")
        return User(email = email, firstName = firstName, lastName = lastName)
    }

    private fun localAuthHandler(ctx: RoutingContext) {
        // state en redirectUri
        // check password and user in body
        val isForm = ctx.request().getHeader(HttpHeaders.CONTENT_TYPE) == "application/x-www-form-urlencoded"
        val user = if (isForm) ctx.request().getFormAttribute("user") else ctx.bodyAsJson.getString("user")
        val parts = if (isForm) ctx.request().getFormAttribute("pass").split("-")
            .toTypedArray() else ctx.bodyAsJson.getString("pass").split("-").toTypedArray()
        val state =
            if (isForm) ctx.request().getFormAttribute(AuthParams.STATE) else ctx.bodyAsJson.getString(AuthParams.STATE)
        val authStateCode =
            if (isForm) ctx.request().getFormAttribute(AuthParams.AUTH_CODE) else ctx.bodyAsJson.getString(
                AuthParams.AUTH_CODE
            )
        val redirectUri = if (isForm) URLDecoder.decode(
            ctx.request().getFormAttribute(AuthParams.REDIRECT_URI),
            StandardCharsets.UTF_8
        ) else ctx.bodyAsJson.getString(AuthParams.REDIRECT_URI)
        val rememberMe = if (isForm) ctx.request()
            .getFormAttribute(AuthParams.REMEMBER_ME) else ctx.bodyAsJson.getString(AuthParams.REMEMBER_ME)
        val seed = parts[1]
        val enc = parts[0]
        if (config.authAdminUser == user) {
            val hashInBytes = digest!!.digest((config.authAdminPassword + seed).toByteArray(StandardCharsets.UTF_8))
            val sb = StringBuilder()
            for (b in hashInBytes) {
                sb.append(String.format("%02x", b))
            }
            val hashInHex = sb.toString()
            // Admin Password matches
            if (hashInHex == enc) {
                // Store code
                val localCode = Utils.generateRandomString()
                val obs = memStore
                    .rxSaveLogin(localCode, LoginState(state = state, redirectUri = redirectUri))
                // Trigger localIdp auth endpoint
                obs.subscribe { ok: Boolean ->
                    val compositeState = CompositeState(authStateCode, rememberMe)
                    val uri = "$basePath/auth/idp/local?code=$localCode&state=$compositeState"
                    ctx.response().setStatusCode(303).putHeader(HttpHeaders.LOCATION, uri).end()
                }
            } else {
                ctx.response().setStatusCode(303)
                    .putHeader(
                        HttpHeaders.LOCATION,
                        redirectUri + "?error=401wc&message=Unauthorized&state=$state&remember_me=$rememberMe"
                    ).end()
            }
        } else {
            ctx.response().setStatusCode(303)
                .putHeader(
                    HttpHeaders.LOCATION,
                    redirectUri + "?error=401wc&message=Unauthorized&state=$state&remember_me=$rememberMe"
                ).end()
        }
    }

    private fun gereateAdminIdpUser(): io.vertx.reactivex.ext.auth.User {
        return io.vertx.reactivex.ext.auth.User.create(JsonObject()
            .put("id", ADMIN_ID)
            .put("firstName", "Admin")
            .put("lastName", "")
            .put("email", adminEmail)
            .put("idp", id)
            .put("name", "Admin Account"))
    }
}
