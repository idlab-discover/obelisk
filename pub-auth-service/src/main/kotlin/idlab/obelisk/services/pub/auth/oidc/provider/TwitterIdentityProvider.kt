package idlab.obelisk.services.pub.auth.oidc.provider

import idlab.obelisk.definitions.catalog.User
import idlab.obelisk.definitions.framework.OblxConfig
import idlab.obelisk.services.pub.auth.model.Claims
import io.reactivex.Completable
import io.reactivex.Single
import io.vertx.core.json.JsonObject
import io.vertx.reactivex.core.Vertx
import io.vertx.reactivex.ext.auth.oauth2.OAuth2Auth
import io.vertx.reactivex.ext.auth.oauth2.providers.TwitterAuth
import io.vertx.reactivex.ext.web.Router
import io.vertx.reactivex.ext.web.client.WebClient
import mu.KotlinLogging
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import javax.inject.Inject

private const val ENV_TWITTER_IDP_CLIENT_ID = "TWITTER_IDP_CLIENT_ID"
private const val ENV_TWITTER_IDP_CLIENT_SECRET = "TWITTER_IDP_CLIENT_SECRET"
private const val ENV_TWITTER_IDP_ISSUER_SITE = "TWITTER_IDP_ISSUER_SITE"

class TwitterIdentityProvider @Inject constructor(
    val vertx: Vertx, val client: WebClient, val config: OblxConfig
) : IdentityProvider {
    private var oAuthInstance: OAuth2Auth? = null
    private val logger = KotlinLogging.logger { }

    override val clientId: String
        get() = config.getString(ENV_TWITTER_IDP_CLIENT_ID)!!
    override val clientSecret: String?
        get() = config.getString(ENV_TWITTER_IDP_CLIENT_SECRET)
    override val name: String
        get() = "Twitter"
    override val id: String
        get() = URLEncoder.encode(name.toLowerCase(), StandardCharsets.UTF_8)
    override val type: IdentityProvider.Type
        get() = IdentityProvider.Type.REDIRECT
    override val tokenIssuers: Set<String>
        get() {
            val issuerSite = config.getString(ENV_TWITTER_IDP_ISSUER_SITE)!!
            return setOf(issuerSite, issuerSite.substring("https://".length))
        }

    override fun initHook(router: Router): Completable {
        return oAuthInstance
            ?.let { Completable.complete() } // exists
            ?: Single.just(TwitterAuth.create(vertx, clientId, clientSecret))
                .doOnSuccess { oAuthInstance = it }
                .ignoreElement()
    }

    override fun authorizeURL(params: JsonObject): String {
        return oAuthInstance!!.authorizeURL(params);
    }

    override fun rxAuthenticate(authInfo: JsonObject): Single<io.vertx.reactivex.ext.auth.User> {
        logger.trace("Authenticating with google using authInfo: {}", authInfo)
        return oAuthInstance!!.rxAuthenticate(authInfo)
            .doOnError { logger.trace("Error authenticating with Google Idp: {}", it.message) }
    }

    override fun retrieveEmail(idpUser: io.vertx.reactivex.ext.auth.User): String {
        TODO("Not yet implemented")
    }

    override fun transformToClaim(idpUser: io.vertx.reactivex.ext.auth.User, oblxUser: User): Claims {
        TODO("Not yet implemented")
    }

    override fun convertToObeliskUser(idpUser: io.vertx.reactivex.ext.auth.User): User {
        TODO("Not yet implemented")
    }
}