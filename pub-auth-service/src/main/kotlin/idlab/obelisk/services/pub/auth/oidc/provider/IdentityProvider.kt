package idlab.obelisk.services.pub.auth.oidc.provider

import idlab.obelisk.definitions.catalog.User
import idlab.obelisk.definitions.framework.OblxConfig
import idlab.obelisk.services.pub.auth.model.Claims
import io.reactivex.Completable
import io.reactivex.Single
import io.vertx.core.json.JsonObject
import io.vertx.reactivex.core.Vertx
import io.vertx.reactivex.ext.web.Router

interface IdentityProvider {

    enum class Type { REDIRECT, POST }

    val clientId: String

    val clientSecret: String?

    /**
     * Human readable name
     * @return
     */
    val name: String

    /**
     * URL safe, lower-case identifier
     * @return
     */
    val id: String

    val type: Type
    /**
     * Returns a list of possible token issuers for this Identity Provider (will match the iss field in id_tokens)
     * @return
     */
    val tokenIssuers: Set<String>

    /**
     * Setup the idp
     */
    fun initHook(router: Router): Completable

    /**
     * Generates the url to send your auth request to for this idp
     */
    fun authorizeURL(params: JsonObject): String

    /**
     * Authenticate with the idp and return Vertx User object with idp token info
     */
    fun rxAuthenticate(authInfo: JsonObject): Single<io.vertx.reactivex.ext.auth.User>

    /**
     * Return the email from the Vertx User object filled with the idp token info
     * (only idp itself knows where it is)
     */
    fun retrieveEmail(idpUser: io.vertx.reactivex.ext.auth.User): String

    /**
     * Convert the idpUser to Claims to construct an Obeliks IdToken from.
     * @param idpUser The user object as filled by the idp
     * @param oblxUser The user object as known to Obelisk
     */
    fun transformToClaim(idpUser: io.vertx.reactivex.ext.auth.User, oblxUser: User): Claims

    /**
     * Convert to an Obelisk User, from idp-filled user object (returned by #rxAuthenticate)
     */
    fun convertToObeliskUser(idpUser: io.vertx.reactivex.ext.auth.User): User
}