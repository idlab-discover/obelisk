package idlab.obelisk.services.pub.auth.oauth

import idlab.obelisk.definitions.framework.OblxConfig
import idlab.obelisk.services.pub.auth.HTTP_BASE_PATH
import io.reactivex.Completable
import io.reactivex.Single
import io.vertx.core.Handler
import io.vertx.reactivex.core.Vertx
import io.vertx.reactivex.ext.web.Router
import io.vertx.reactivex.ext.web.RoutingContext
import io.vertx.reactivex.ext.web.handler.BodyHandler
import io.vertx.reactivex.ext.web.handler.StaticHandler

interface OAuthProvider {
    /**
     * Auth handler
     *
     * Everything that happens once a client hits the auth endpoint
     *
     * @param ctx
     */
    fun authEndpointsHandler(ctx: RoutingContext)

    /**
     * Identity Provider callback handler
     *
     * Callback after IdP authentication.
     *
     * @param ctx
     */
    fun idpCallbackHandler(ctx: RoutingContext)

    /**
     * Token handler
     *
     * Everything that happens once a client hits the token endpoint
     *
     * @param ctx
     */
    fun tokenEndpointHandler(ctx: RoutingContext)

    /**
     * Logout by destroying the token and session
     */
    fun logoutHandler(ctx: RoutingContext)

    /**
     * JWKS Handler
     *
     * Provides a handler to host the JWKS set
     */
    fun jwksHandler(): Handler<RoutingContext>

    /**
     * OpenId Configuration handler
     *
     * Provides a handler to host the .well-known openid-configuration page
     */
    fun openIdConfigHandler(): Handler<RoutingContext>

    /**
     * The path of the auth endpoint
     *
     * (Defaults to `$basePath/auth`)
     *
     * @return The path as a string, starting with a slash
     */
    fun getAuthEndpoint(config: OblxConfig): String {
        val basePath = config.getString(OblxConfig.HTTP_BASE_PATH_PROP, HTTP_BASE_PATH)
        return "$basePath/auth"
    }

    /**
     * The path of the token endpoint
     *
     * (Defaults to `$basePath/token`)
     *
     * @return The path as a string, starting with a slash
     */
     fun getTokenEndpoint(config: OblxConfig): String {
        val basePath = config.getString(OblxConfig.HTTP_BASE_PATH_PROP, HTTP_BASE_PATH)
        return "$basePath/token"
    }

    /**
     * The path of the Identity Provider callback endpoint
     *
     * (Defaults to `$basePath/auth/idp`)
     *
     * @return The path as a string, starting with a slash
     */
    fun getIdpCallbackEndpoint(config: OblxConfig): String {
        val basePath = config.getString(OblxConfig.HTTP_BASE_PATH_PROP, HTTP_BASE_PATH)
        return "$basePath/auth/idp"
    }

    /**
     * The path of the login page
     *
     * (Defaults to `$basePath/login`)
     *
     * @return The path as a string, starting with a slash
     */
    fun getLoginPageEndpoint(config: OblxConfig): String {
        val basePath = config.getString(OblxConfig.HTTP_BASE_PATH_PROP, HTTP_BASE_PATH)
        return "$basePath/login"
    }

    fun getLogoutEndpoint(config: OblxConfig): String {
        val basePath = config.getString(OblxConfig.HTTP_BASE_PATH_PROP, HTTP_BASE_PATH)
        return "$basePath/logout"
    }

    /**
     * The path of the JWKS page
     *
     * (Defaults to `$basePath/jwks`)
     *
     * @return The path as a string, starting with a slash
     */
    fun getJWKSEndpoint(config: OblxConfig): String {
        val basePath = config.getString(OblxConfig.HTTP_BASE_PATH_PROP, HTTP_BASE_PATH)
        return "$basePath/jwks"
    }

    /**
     * The path of the .well-known openid-configuration file
     *
     * (Defaults to `$basePath/.well-known/openid-configuration`)
     *
     * @return The path as a string, starting with a slash
     */
    fun getOpenIdEndpoint(config: OblxConfig): String {
        val basePath = config.getString(OblxConfig.HTTP_BASE_PATH_PROP, HTTP_BASE_PATH)
        return "$basePath/.well-known/openid-configuration"
    }

    /**
     * A hook that is called before registering the handler to the endpoints in the setup code. Does nothing by default
     *
     * @param router The router instance of this verticle
     */
    fun initHook(router: Router): Completable {
        return Completable.complete()
    }

    /**
     * Setup calls the [.initHook] method and then proceeds to register all handler
     * with their respective endpoints
     *
     * @param vertx  Vertx instance
     * @param config Config given to this verticle
     * @param router The router instance of this verticle
     */
    fun setup(vertx: Vertx, config: OblxConfig, router: Router): Single<Router> {
        // Hook
        val basePath = config.getString(OblxConfig.HTTP_BASE_PATH_PROP, HTTP_BASE_PATH)
        return initHook(router).andThen(Single.defer {

            // Register JWKS store endpoint
            router.get(getJWKSEndpoint(config)).handler(jwksHandler())

            // Register OpenID well-known config
            router.get(getOpenIdEndpoint(config)).handler(openIdConfigHandler())
            router.head(getOpenIdEndpoint(config)).handler(openIdConfigHandler())

            // Register required endpoints and their handlers
            val staticHandler = StaticHandler.create()
            router.post(getTokenEndpoint(config))
                .handler(BodyHandler.create())
                .handler(this::tokenEndpointHandler)
            router.get(getAuthEndpoint(config)).handler(this::authEndpointsHandler)
            router.post(getAuthEndpoint(config))
                .handler(BodyHandler.create())
                .handler(this::authEndpointsHandler)
            router.get(getIdpCallbackEndpoint(config) + "/:idp").handler(this::idpCallbackHandler)
            router.get(getLoginPageEndpoint(config) + "/*").handler(staticHandler)
            router.get(getLogoutEndpoint(config)).handler(this::logoutHandler)
            Single.just(router)
        })
    }
}