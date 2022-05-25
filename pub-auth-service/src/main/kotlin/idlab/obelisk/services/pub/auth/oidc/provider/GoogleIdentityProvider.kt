package idlab.obelisk.services.pub.auth.oidc.provider

import idlab.obelisk.definitions.catalog.User
import idlab.obelisk.definitions.framework.ENV_GOOGLE_IDP_CLIENT_ID
import idlab.obelisk.definitions.framework.ENV_GOOGLE_IDP_CLIENT_SECRET
import idlab.obelisk.definitions.framework.ENV_GOOGLE_IDP_ISSUER_SITE
import idlab.obelisk.definitions.framework.OblxConfig
import idlab.obelisk.services.pub.auth.model.Claims
import io.reactivex.Completable
import io.reactivex.Single
import io.vertx.core.json.JsonObject
import io.vertx.ext.auth.oauth2.OAuth2Options
import io.vertx.reactivex.core.Vertx
import io.vertx.reactivex.ext.auth.oauth2.OAuth2Auth
import io.vertx.reactivex.ext.auth.oauth2.providers.GoogleAuth
import io.vertx.reactivex.ext.web.Router
import io.vertx.reactivex.ext.web.client.WebClient
import mu.KotlinLogging
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import javax.inject.Inject
import javax.inject.Singleton

private const val DEFAULT_GOOGLE_IDP_ISSUER_SITE = "https://accounts.google.com"

@Singleton
class GoogleIdentityProvider @Inject constructor(val vertx: Vertx, val client: WebClient, val config: OblxConfig) :
    IdentityProvider {
    private var oAuthInstance: OAuth2Auth? = null

    private val logger = KotlinLogging.logger { }

    override val id: String
        get() = URLEncoder.encode(name.toLowerCase(), StandardCharsets.UTF_8)

    override val name: String
        get() = "Google"

    override val clientId: String
        get() = config.getString(ENV_GOOGLE_IDP_CLIENT_ID)
            ?: throw RuntimeException("Please set env variable $ENV_GOOGLE_IDP_CLIENT_ID")

    override val clientSecret: String?
        get() = config.getString(ENV_GOOGLE_IDP_CLIENT_SECRET)
            ?: throw RuntimeException("Please set env variable $ENV_GOOGLE_IDP_CLIENT_SECRET")

    override val type: IdentityProvider.Type
        get() = IdentityProvider.Type.REDIRECT

    override val tokenIssuers: Set<String>
        get() {
            val configEntry = config.getString(ENV_GOOGLE_IDP_ISSUER_SITE)
            val issuerSite = if (configEntry != null) {
                configEntry
            } else {
                logger.warn { "IDP issuer site not configured, defaulting to $DEFAULT_GOOGLE_IDP_ISSUER_SITE." }
                DEFAULT_GOOGLE_IDP_ISSUER_SITE
            }
            return setOf(issuerSite, issuerSite.substring("https://".length))
        }

    override fun initHook(router: Router): Completable {
        return oAuthInstance
            ?.let { Completable.complete() } // exists
            ?: GoogleAuth // does not exist yet
                .rxDiscover(
                    vertx, OAuth2Options()
                        .setClientId(clientId)
                        .setClientSecret(clientSecret)
                )
                .doOnSuccess { oAuthInstance = it }
                .ignoreElement()
    }

    override fun authorizeURL(params: JsonObject): String {
        return oAuthInstance!!.authorizeURL(params)
    }

    override fun rxAuthenticate(authInfo: JsonObject): Single<io.vertx.reactivex.ext.auth.User> {
        // Add redirectUri in camelCase, since it seems to be required (without proper documentation on vertx' side! https://github.com/vert-x3/vertx-auth/pull/484 )
        authInfo.put("redirectUri", authInfo.getString("redirect_uri"))
        logger.trace("Authenticating with google using authInfo: {}", authInfo)
        return oAuthInstance!!.rxAuthenticate(authInfo)
            .doOnError { logger.trace("Error authenticating with Google Idp: {}", it.message) }
    }

    override fun retrieveEmail(idpUser: io.vertx.reactivex.ext.auth.User): String {
        return idpUser.attributes().getString("email")
    }

    override fun transformToClaim(idpUser: io.vertx.reactivex.ext.auth.User, oblxUser: User): Claims {
        return createClaims(oblxUser.id!!, idpUser)
    }

    override fun convertToObeliskUser(idpUser: io.vertx.reactivex.ext.auth.User): User {
        val att = idpUser.attributes().getJsonObject("idToken")
        return User(
            email = att.getString("email"),
            firstName = att.getString("given_name"),
            lastName = att.getString("family_name"),
        )
    }

    /**
     * Create all claims needed to create an IdToken later from a google IdToken
     */
    private fun createClaims(userId: String, user: io.vertx.reactivex.ext.auth.User): Claims {
        val googleIdToken = user.attributes().getJsonObject("idToken")
        val name = googleIdToken.getString("name")
        val email = googleIdToken.getString("email")
        val firstName = googleIdToken.getString("given_name")
        val lastName = googleIdToken.getString("family_name")
        val picture = googleIdToken.getString("picture")
        val locale = googleIdToken.getString("locale")
        return Claims.forUser(
            userId = userId,
            firstName = firstName,
            lastName = lastName,
            email = email,
            idp = id,
            name = name,
            otherClaims = mutableMapOf("picture" to picture, "locale" to locale, "idp" to id)
        )
    }
}
