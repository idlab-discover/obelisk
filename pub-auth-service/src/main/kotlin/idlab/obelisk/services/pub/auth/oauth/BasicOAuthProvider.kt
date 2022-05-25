package idlab.obelisk.services.pub.auth.oauth

import idlab.obelisk.definitions.Eq
import idlab.obelisk.definitions.catalog.AccessManager
import idlab.obelisk.definitions.catalog.Client
import idlab.obelisk.definitions.catalog.MetaStore
import idlab.obelisk.definitions.catalog.Token
import idlab.obelisk.definitions.catalog.codegen.UserField
import idlab.obelisk.definitions.framework.OblxConfig
import idlab.obelisk.plugins.accessmanager.basic.store.AuthStore
import idlab.obelisk.plugins.accessmanager.basic.store.model.LoginState
import idlab.obelisk.plugins.accessmanager.basic.store.model.Session
import idlab.obelisk.plugins.accessmanager.basic.utils.CodeChallenge
import idlab.obelisk.plugins.accessmanager.basic.utils.SecureSecret
import idlab.obelisk.services.pub.auth.keys.JWKSHandler
import idlab.obelisk.services.pub.auth.keys.KeyStore
import idlab.obelisk.services.pub.auth.model.AuthParams
import idlab.obelisk.services.pub.auth.model.Claims
import idlab.obelisk.services.pub.auth.model.CompositeState
import idlab.obelisk.services.pub.auth.oidc.OpenIdConnector
import idlab.obelisk.services.pub.auth.oidc.provider.IdentityProvider
import idlab.obelisk.services.pub.auth.token.TokenMinter
import idlab.obelisk.services.pub.auth.util.Utils
import idlab.obelisk.utils.service.http.*
import idlab.obelisk.utils.service.utils.Base64.decodeFromBase64
import idlab.obelisk.utils.service.utils.TTL
import io.reactivex.Completable
import io.reactivex.Observable
import io.reactivex.Single
import io.vertx.core.Handler
import io.vertx.core.http.HttpHeaders
import io.vertx.core.json.Json
import io.vertx.core.json.JsonArray
import io.vertx.core.json.JsonObject
import io.vertx.reactivex.core.MultiMap
import io.vertx.reactivex.core.Vertx
import io.vertx.reactivex.ext.auth.User
import io.vertx.reactivex.ext.web.MIMEHeader
import io.vertx.reactivex.ext.web.ParsedHeaderValues
import io.vertx.reactivex.ext.web.Router
import io.vertx.reactivex.ext.web.RoutingContext
import mu.KotlinLogging
import org.jose4j.jwt.consumer.InvalidJwtException
import java.lang.IndexOutOfBoundsException
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

@Singleton
class BasicOAuthProvider @Inject constructor(
    val vertx: Vertx,
    val config: OblxConfig,
    @Named("basic")
    val openIdConnector: OpenIdConnector,
    val metaStore: MetaStore,
    val keyStore: KeyStore,
    val authStore: AuthStore,
    val accessManager: AccessManager,
    val tokenMinter: TokenMinter,
) : OAuthProvider {
    private val authMap: MutableMap<String, IdentityProvider> = HashMap()
    private val issMap: MutableMap<String, String> = HashMap()
    private val logger = KotlinLogging.logger { }

    private val tokenTTL: TTL = TTL(config.getString(TTL.TOKEN_TTL_PROP, TTL.TOKEN_TTL))
    private val tokenIdleTTL: TTL = TTL(config.getString(TTL.TOKEN_IDLE_TTL_PROP, TTL.TOKEN_IDLE_TTL))

    override fun initHook(router: Router): Completable {
        return Observable.fromIterable(openIdConnector.identityProviders)
            .flatMapSingle { idp ->
                idp.initHook(router)
                    .doOnComplete { logger.info { "Identity Provider ${idp.name} initialized" } }
                    .doOnError { logger.error(it) { "Identity Provider ${idp.name} could not be initialized!" } }
                    .toSingleDefault(idp)
            }
            .doOnNext { idp -> authMap[idp.id] = idp }
            .doOnNext { idp -> idp.tokenIssuers.forEach { issMap[it] = idp.id } }
            .ignoreElements()
            .andThen(Utils.obeliskWebClientSetup(metaStore, config)) // Create ObeliskWeb-client
//            .andThen(Utils.testMethod(metaStore)) // Do some teststuff
    }

    override fun authEndpointsHandler(ctx: RoutingContext) {
        logger.trace { "authEndpointsHandler will: checkFormat, validateClientId, saveAuthRequest and redirectToLogin" }
        checkFormat(ctx)
            .flatMap { validateClientId(ctx, it) }
            .flatMap { saveAuthRequest(ctx, it) }
            .flatMapCompletable { redirectToLogin(ctx, it) }
            .subscribe({ }, { writeHttpError(ctx, logger).accept(it) }
            )

    }

    override fun idpCallbackHandler(ctx: RoutingContext) {
        logger.trace { "idpCallbackHandler will: check if idpExists, codePresent, exchangeIdpCodeForToken" }
        idpExists(ctx)
            .flatMap { codePresent(ctx).toSingleDefault(it) }
            .flatMapCompletable { exchangeIdpCodeForToken(ctx, it) }
            .subscribe({}, { writeHttpError(ctx, logger).accept(it) })
    }

    private fun getSentValues(ctx: RoutingContext): JsonObject {
        val json = JsonObject();
        val contentType = ctx.request().headers().get(HttpHeaders.CONTENT_TYPE)
        when (contentType) {
            HttpHeaders.APPLICATION_X_WWW_FORM_URLENCODED.toString() -> {
                ctx.request().formAttributes().entries().stream()
                    .forEach { entry -> json.put(entry.key, entry.value) }

                // Temporary fix for mbrain
                if (json.isEmpty) {
                    try {
                        ctx.bodyAsJson.map { entry -> json.put(entry.key, entry.value) }
                        val clientId = json.getString(AuthParams.CLIENT_ID);
                        logger.warn { "Content-Type WRONG: Content-Type application/x-www-form-urlencoded was sent, but parsed as application/json. Notify client (ID: $clientId) code owners!" }
                    } catch (ex: Exception) {
                        throw  BadRequestException("Body not parsable as application/json even with incorrect application/x-www-form-urlencoded content type.")
                    }
                }
            }
            "application/json" -> {
                ctx.bodyAsJson.map { entry -> json.put(entry.key, entry.value) }
            }
            else -> {
                throw  BadRequestException("Body not parsable as application/json or application/x_www_form_urlencoded. Dit you add a correct Content-Type header?")
            }
        }
        return json;
    }


    override fun tokenEndpointHandler(ctx: RoutingContext) {
        logger.trace { "Entering tokenEndpointHandler: requesting token..." }
        try {
            val body = getSentValues(ctx)
            val code = body.getString(AuthParams.CODE)
            var clientId = body.getString(AuthParams.CLIENT_ID)
            var clientSecret = body.getString(AuthParams.CLIENT_SECRET)
            val codeVerifier = body.getString(AuthParams.CODE_VERIFIER)

            // If Basic Auth header is present, override clientId and clientSecret
            val basicAuthStr = ctx.request().getHeader(HttpHeaders.AUTHORIZATION.toString())

            var exception: Exception? = null;
            if (basicAuthStr?.startsWith("Basic ") == true) {
                try {
                    val dec = basicAuthStr.substringAfter("Basic ").decodeFromBase64().split(":")
                    clientId = dec[0]
                    clientSecret = dec[1]
                } catch (ex: Exception) {
                    throw UnauthorizedException("Incorrect basic Basic auth string.");
                }
            }
            val grantType = body.getString(AuthParams.GRANT_TYPE)
            if (grantType.isNullOrBlank()) {
                throw BadRequestException("No or invalid grant_type parameter")
            }
            val obs =
                if (clientId == null) Single.error(BadRequestException("ClientId should not be null! (did not find a proper Authorization header 'Basic base64str' and no clientId in body)")) else validateIncoming(
                    grantType,
                    code,
                    clientId,
                    clientSecret,
                    codeVerifier
                )
            obs.flatMap { loginState ->
                createAndStoreToken(grantType, loginState, clientId).map { session ->
                    Pair(
                        loginState,
                        session
                    )
                }
            }
                .flatMap { createTokenResponse(it.first, it.second) }
                .subscribe(
                    { ctx.response().putHeader(HttpHeaders.CONTENT_TYPE, "application/json").end(it) },
                    { writeHttpError(ctx, logger).accept(it) }
                )
        } catch (ex: Exception) {
            // Catch any lingering exceptions and report as Bad Request Exception
            writeHttpError(ctx, logger).accept(ex)
        }
    }

    override fun logoutHandler(ctx: RoutingContext) {
        accessManager.getToken(ctx.request())
            .flatMap { tok ->
                var sub = "u" + tok.user.id
                if (tok.client != null) {
                    sub = "c" + tok.client?.id
                }
                authStore.rxRemoveSessionBySub(sub).map { tok.opaqueToken }
            }
            .flatMap { authStore.rxRemoveToken(it) }
            .ignoreElement()
            .subscribe(
                { ctx.response().end() },
                { err -> writeHttpError(ctx, logger).accept(err) }
            )
    }

    override fun jwksHandler(): Handler<RoutingContext> {
        return JWKSHandler.create(keyStore)
    }

    override fun openIdConfigHandler(): Handler<RoutingContext> {
        val base = config.authPublicUri;
        val config = JsonObject()
            .put("issuer", base + "/auth")
            .put("token_endpoint", base + getTokenEndpoint(config))
            .put("end_session_endpoint", base + getLogoutEndpoint(config))
            .put("jwks_uri", base + getJWKSEndpoint(config))
            .put("id_token_signing_alg_values_supported", JsonArray(listOf("RS256")))
            .put("code_challenge_methods_supported", JsonArray(listOf("plain", "S256")))
        return Handler<RoutingContext> {
            it.end(config.encode())
        }

    }

    private fun checkFormat(ctx: RoutingContext): Single<FlowState> {
        val paramResponseType = AuthParams.getParam(ctx, AuthParams.RESPONSE_TYPE)
        val paramClientId = AuthParams.getParam(ctx, AuthParams.CLIENT_ID)
        val paramRedirectUri = AuthParams.getParam(ctx, AuthParams.REDIRECT_URI)
        val paramState = AuthParams.getParam(ctx, AuthParams.STATE)
        val paramScope = AuthParams.getParam(ctx, AuthParams.SCOPE)
        val paramCodeChallenge = AuthParams.getParam(ctx, AuthParams.CODE_CHALLENGE)
        val paramCodeChallengeMethod = AuthParams.getParam(ctx, AuthParams.CODE_CHALLENGE_METHOD)

        return if ("code" != paramResponseType) {
            Single.error(BadRequestException("Wrong or missing response_type parameter"))
        } else if (paramState == null) {
            Single.error(BadRequestException("Missing state parameter"))
        } else if (paramClientId == null) {
            Single.error(BadRequestException("Missing client_id parameter"))
        } else if (paramRedirectUri == null) {
            Single.error(BadRequestException("Missing redirect_uri parameter"))
        } else if (paramScope == null) {
            Single.error(BadRequestException("Missing scope parameter"))
        } else {
            if ("openid" == paramScope.trim() || paramScope.trim().split(' ').contains("openid")) {
                // 'openid' means authentication via IdentityProvider
                if (paramCodeChallenge != null && paramCodeChallengeMethod != null) {
                    if (setOf("plain", "S256").contains(paramCodeChallengeMethod)) {
                        Single.just(FlowState(CaseNumber.CLIENT_SIDE_AS_USER))
                    } else {
                        Single.error(BadRequestException("Unsupported code_challenge_method"))
                    }
                } else {
                    Single.just(FlowState(CaseNumber.SERVER_SIDE_AS_USER))
                }
            } else if ("client" == paramScope.trim() || paramScope.trim().split(' ').contains("client")) {
                // 'client' means not authentication
                if (paramCodeChallenge != null && paramCodeChallengeMethod != null) {
                    if (setOf("plain", "S256").contains(paramCodeChallengeMethod)) {
                        Single.just(FlowState(CaseNumber.CLIENT_SIDE))
                    } else {
                        Single.error(BadRequestException("Unsupported code_challenge_method"))
                    }
                } else {
                    Single.just(FlowState(CaseNumber.SERVER_SIDE))
                }
            } else {
                Single.error(BadRequestException("No appropriate scope included"))
            }
        }
    }

    /**
     * Removes the loginstate after a successful login and returnts the token response
     */
    private fun createTokenResponse(loginState: LoginState, session: Session): Single<String> {
        return authStore.rxRemoveLogin(session.loginStateKey)
            .map {
                JsonObject()
                    .put("token_type", "bearer")
                    .put("token", session!!.access_token)
                    .put("access_token", session!!.access_token)
                    .put("id_token", session.id_token) // empty in case III ?
                    .put("max_idle_time", tokenIdleTTL.inSeconds())
                    .put("expires_in", tokenIdleTTL.inSeconds())
                    .put("max_valid_time", tokenTTL.inSeconds())
                    .put("remember_me", loginState.remember_me)
                    .encode();
            }
    }

    private fun createAndStoreToken(
        grantType: String,
        loginState: LoginState,
        clientId: String?
    ): Single<Session> {
        return authStore
            .rxGetSession(loginState.sub)
            .toSingle()
            .onErrorResumeNext(Single.error(UnauthorizedException("No session found")))
            .flatMap { session ->
                val email = session.idToken?.get("email") as String
                getUserByEmail(email)
                    .onErrorResumeNext {
                        if (email == null) {
                            Single.error(UnauthorizedException("Email was null"))
                        } else {
                            when (grantType) {
                                "authorization_code" -> Single.error(UnauthorizedException("No user for $email was found"))
                                "client_credentials" -> Single.error(UnauthorizedException("No user for $email was found for known Client $clientId"))
                                else -> Single.error(ServerError("This error should not happen"))
                            }
                        }
                    }
                    .flatMap { user ->
                        accessManager.getAccessInfo(user, clientId)
                            .flatMap { ai ->
                                if (clientId == null) {
                                    metaStore.getAggregatedUsageLimits(user)
                                        .flatMap { limits ->
                                            getOrGenerateAccessToken(session).map {
                                                Pair(it, Token(user, null, ai, limits, it.access_token!!))
                                            }
                                        }
                                } else {
                                    metaStore.getClient(clientId)
                                        .toSingle()
                                        .onErrorResumeNext { Single.error(UnauthorizedException("Client with id $clientId was not found in the Identity Store")) }
                                        .flatMap { client ->
                                            metaStore.getAggregatedUsageLimits(user, client)
                                                .flatMap { limits ->
                                                    getOrGenerateAccessToken(session).map {
                                                        Pair(it, Token(user, client, ai, limits, it.access_token!!))
                                                    }
                                                }
                                        }
                                }
                            }
                    }
                    // save token, overwrite opaque token if it exists, also store it in session
                    .flatMap { pair ->
                        authStore
                            .rxSaveToken(pair.second)
                            .map { pair.first }
                    }
            }
    }


    /**
     * First refresh the token to get the latest values.
     * Then see if access_token exists
     * If so, return session
     * If not, return session with newly created token
     */
    private fun getOrGenerateAccessToken(session: Session): Single<Session> {
        fun generateNewToken(oldSession: Session): Single<Session> {
            // Generate new Access Token here
            val newSession = oldSession.copy(access_token = Utils.generateRandomString())
            return authStore.rxSaveSession(newSession).map { newSession }
        }

        // Fetch latest session again to be sure
        return authStore.rxGetSession(session.sub)
            .flatMapSingle { if (it.access_token == null) generateNewToken(it) else Single.just(it) }
    }

    private fun validateIncoming(
        grant_type: String,
        code: String?,
        clientId: String,
        clientSecret: String?,
        codeVerifier: String?
    ): Single<LoginState> {
        return when (grant_type) {
            "authorization_code" -> {
                if (code == null) {
                    return Single.error(BadRequestException("Missing code parameter"))
                }
                codeVerifier
                    ?.let { checkCodeVerifier(code, clientId, it) }
                    ?: (clientSecret
                        ?.let { checkClientSecret(code, clientId, it) }
                        ?: Single.error(BadRequestException("Missing codeVerifier or clientSecret to validate clientId")))
            }
            "client_credentials" -> {
                if (clientSecret != null) {
                    checkClientCredentials(clientId, clientSecret)
                } else {
                    Single.error(BadRequestException("Missing clientId or clientSecret"))
                }
            }
            else -> Single.error(BadRequestException("Missing or invalid grant_type"))
        }
    }

    private fun checkCodeVerifier(code: String, clientId: String, codeVerifier: String): Single<LoginState> {
        return authStore.rxGetLogin(code).toSingle()
            .doOnSuccess { logger.trace("Redis LoginState object for {} retrieved: {}", code, Json.encodePrettily(it)) }
            .onErrorResumeNext { Single.error(UnauthorizedException("Authorization code expired")) }
            .flatMap { loginState ->
                if (clientId != loginState.clientId) {
                    Single.error(UnauthorizedException("Invalid code for client_id"))
                } else {
                    try {
                        val codeChallenge = CodeChallenge(codeVerifier, loginState.codeChallengeMethod!!)
                        if (codeChallenge.matches(loginState.codeChallenge!!)) {
                            if (loginState.onBehalfOfUser) {
                                Single.just(loginState)
                            } else {
                                metaStore.getClient(clientId).toSingle()
                                    .onErrorResumeNext { Single.error(UnauthorizedException("Client with client_id not found")) }
                                    .flatMap { createSessionForClientLogin(it) }
                            }
                        } else {
                            Single.error(BadRequestException("Wrong code_verifier"))
                        }
                    } catch (ex: IllegalArgumentException) {
                        Single.error(BadRequestException("Unsupported code_challenge_method"))
                    }
                }
            }
    }

    private fun checkClientSecret(code: String, clientId: String, clientSecret: String): Single<LoginState> {
        return authStore.rxGetLogin(code).toSingle()
            .onErrorResumeNext { Single.error(UnauthorizedException("Authorization code expired")) }
            .flatMap { loginState ->
                if (clientId == loginState.clientId) {
                    metaStore.getClient(clientId)
                        .toSingle()
                        .onErrorResumeNext(Single.error(UnauthorizedException("Client with id $clientId was not found in the Identity Store")))
                        .flatMap { client ->
                            if (SecureSecret.isValid(
                                    clientSecret,
                                    client.secretHash
                                )
                            ) Single.just(loginState) else Single.error(UnauthorizedException("Invalid client_secret"))
                        }
                } else {
                    Single.error(UnauthorizedException("Invalid client_id"))
                }
            }
    }

    private fun checkClientCredentials(clientId: String, clientSecret: String): Single<LoginState> {
        return metaStore.getClient(clientId)
            .toSingle()
            .onErrorResumeNext { Single.error(UnauthorizedException("Client with id $clientId was not found in the Identity Store")) }
            .flatMap { client ->
                if (!client.confidential) {
                    Single.error(UnauthorizedException("Client is not marked as confidential, hence no client_credentials login allowed"))
                } else if (SecureSecret.isValid(clientSecret, client.secretHash)) {
                    createSessionForClientLogin(client)
                } else {
                    Single.error(UnauthorizedException("Invalid client_id-client_secret combination"))
                }
            }
    }

    /**
     * Creates a session when this is a pure client login.
     * There will not be a session yet, because no authentication identity provider was hit.
     */
    private fun createSessionForClientLogin(client: Client): Single<LoginState> {
        return metaStore.getUser(client.userId)
            .toSingle()
            .onErrorResumeNext { Single.error(UnauthorizedException("User for client (id: ${client.userId}) was not found in the Identity Store")) }
            .map { it.email }
            .flatMap {
                // Use clientId as sub claim and set idp to local for clients (since they are managed locally, not by google or any other external idp)
                val claims = Claims.forClient(client.id!!, name = client.name, ownerEmail = it, idp = "local")
                getActiveSessionOrCreate(claims.sub)                // create sessions
                    .flatMap { session ->
                        authStore.rxGetNewSessionSid(session).map { sid -> Pair(sid, session) }
                    }   // generate unique sid
                    .flatMap { (sid, session) ->
                        authStore.rxSaveSession(
                            session.copy(
                                idToken = claims.toMap(),
                                id_token = tokenMinter.mintToken(claims, sid)
                            )
                        ).map { LoginState(sub = session.sub) }
                    }
            } // return empty loginState object with reference to the session
    }


    /**
     * Fetch client from database and see if it matches registered redirectUri
     * (regardless of FlowState.caseNumber)
     * Throws error if client_id and redirect_uri do not match up.
     */
    private fun validateClientId(ctx: RoutingContext, flowState: FlowState): Single<FlowState> {
        val clientId = AuthParams.getParam(ctx, AuthParams.CLIENT_ID)
        val redirectUri = AuthParams.getParam(ctx, AuthParams.REDIRECT_URI)
        return metaStore.getClient(clientId!!)
            .toSingle()
            .onErrorResumeNext { Single.error(UnauthorizedException("Client with id $clientId was not found in the Identity Store")) }
            .timeout(10, TimeUnit.SECONDS)
            .onErrorResumeNext {
                if (it is TimeoutException) {
                    Single.error<Client>(ServerError("No database response"))
                } else {
                    Single.error<Client>(UnauthorizedException(it.message))
                }
            }
            .flatMap {
                if (Utils.redirectUriMatches(it.redirectURIs, redirectUri!!)) {
                    Single.just(flowState)
                } else {
                    Single.error<FlowState>(UnauthorizedException("RedirectUri is not registered with client"))
                }
            }
    }

    /**
     * Save important info in Redis AuthCodes mapCache. Differs per caseNumber.
     * Also store the code (id) in the flowState and return that flowState.
     *
     * @param ctx
     * @param flowState
     * @return Single of the FlowState
     */
    private fun saveAuthRequest(ctx: RoutingContext, flowState: FlowState): Single<FlowState> {
        flowState.code = AuthParams.getParam(ctx, AuthParams.AUTH_CODE)
        if (flowState.code == null) {
            flowState.code = Utils.generateRandomString()
        }
        // If idToken was sent, save it
        flowState.idToken = AuthParams.getParam(ctx, AuthParams.ID_TOKEN)
        // Save complete authentication attempt state
        val loginState = LoginState(
            responseType = AuthParams.getParam(ctx, AuthParams.RESPONSE_TYPE),
            clientId = AuthParams.getParam(ctx, AuthParams.CLIENT_ID),
            redirectUri = AuthParams.getParam(ctx, AuthParams.REDIRECT_URI),
            state = AuthParams.getParam(ctx, AuthParams.STATE),
            remember_me = AuthParams.getParam(ctx, AuthParams.REMEMBER_ME).toBoolean(),
            codeChallenge = if (flowState.caseNumber.isClientSide()) AuthParams.getParam(
                ctx,
                AuthParams.CODE_CHALLENGE
            ) else null,
            codeChallengeMethod = if (flowState.caseNumber.isClientSide()) AuthParams.getParam(
                ctx,
                AuthParams.CODE_CHALLENGE_METHOD
            ) else null,
            onBehalfOfUser = flowState.caseNumber.asUser()
        )
        // Store relevant information of auth request in Redis AuthCodes MapCache with 2 mins TTL
        return authStore
            .rxSaveLogin(flowState.code, loginState)
            .map { flowState }
    }

    /**
     * Redirect to the login page (or bypass it to identity provider)
     *
     * @param ctx
     * @param flowState Wrapper for caseNumber and sessionId
     * @return
     */
    private fun redirectToLogin(ctx: RoutingContext, flowState: FlowState): Completable {
        // Special case: idToken is sent with request to identify user and search for existing session
        if (flowState.idToken != null) {
            val ctxRedUri = AuthParams.getParam(ctx, AuthParams.REDIRECT_URI)
            // Validate incoming id token
            return verifyAndFetchSession(flowState.idToken!!)
                .onErrorResumeNext {
                    Single.error(RedirectHttpStatusError(ctxRedUri!!, 401, it.message))
                }
                // Fetch loginState
                .flatMap {
                    authStore.rxGetLogin(flowState.code)
                        .toSingle()
                        .onErrorResumeNext {
                            Single.error(RedirectHttpStatusError(ctxRedUri!!, 401, "LoginState code has expired."))
                        }
                        // Put sessionId in authCode (needed to fetch session when client/user returns with code to exchange for token
                        .flatMap { loginState ->
                            authStore
                                .rxSaveLogin(flowState.code, loginState.copy(sub = it.sub))
                                .map { loginState }
                        }
                }
                // Redirect back to client
                .flatMapCompletable {
                    val state = URLEncoder.encode(it.state, StandardCharsets.UTF_8)
                    ctx.response()
                        .putHeader(HttpHeaders.LOCATION, "${it.redirectUri}?code=${flowState.code}&state=${state}")
                        .setStatusCode(302).rxEnd()
                }
        }

        // TODO: Redirect depends on caseNumber
        return when {
            flowState.caseNumber.asUser() -> { // case I or II
                // redirect to login
                val uri: String
                val paramLoginHint = ctx.request().getParam(AuthParams.LOGIN_HINT)
                val paramRememberMe = ctx.request().getParam(AuthParams.REMEMBER_ME)
                if (!authMap.containsKey(paramLoginHint)) {
                    var query = ctx.request().query() + "&" + AuthParams.AUTH_CODE + "=" + flowState.code
                    uri = getLoginPageEndpoint(config) + "/index.html?" + query
                } else if (authMap[paramLoginHint]?.type == IdentityProvider.Type.REDIRECT) {
                    uri = authMap[paramLoginHint]!!.authorizeURL(
                        JsonObject()
                            .put(AuthParams.SCOPE, "openid email profile")
                            .put(AuthParams.STATE, CompositeState(flowState.code, paramRememberMe).encode())
                            .put(AuthParams.PROMPT, "consent")
                            .put(
                                AuthParams.REDIRECT_URI,
                                config.authPublicUri + getIdpCallbackEndpoint(config) + "/" + paramLoginHint
                            )
                    )
                } else {
                    return ctx.response().setStatusCode(401).rxEnd()
                }
                ctx.response()
                    .putHeader(HttpHeaders.LOCATION, uri)
                    .setStatusCode(302)
                    .rxEnd()
            }
            flowState.caseNumber.isClientSide() && flowState.caseNumber.asClient() -> { // case III: SPA (public) as Application
                // redirect to original app
                authStore.rxGetLogin(flowState.code)
                    .toSingle()
                    .onErrorResumeNext { Single.error(UnauthorizedException("Auth code has expired.")) }
                    .flatMapCompletable {
                        val state = URLEncoder.encode(it.state, StandardCharsets.UTF_8)
                        ctx.response()
                            .putHeader(HttpHeaders.LOCATION, "${it.redirectUri}?code=${flowState.code}&state=${state}")
                            .setStatusCode(302).rxEnd()
                    }
            }
            flowState.caseNumber.isServerSide() && flowState.caseNumber.asClient() -> { // Case IV: Private (confidential) as Application
                Completable.error(UnauthorizedException("Confidential clients with scope `client` should immediately request a token at the token endpoint with their client credentials"))
            }
            else -> Completable.error(RuntimeException("Should not happen!"))
        }
    }

    /**
     * Verify if the token has not been tampered with, and fetch the session if it exists
     */
    private fun verifyAndFetchSession(encIdTokenString: String): Single<Session> {
        return try {
            val claims = tokenMinter.verifyAndProcessToken(encIdTokenString)
            val sid = claims["sid"] as String
            authStore.rxGetSessionBySid(sid).toSingle()
                .onErrorResumeNext(Single.error(UnauthorizedException("No session found!")))
        } catch (ex: InvalidJwtException) {
            Single.error(UnauthorizedException("Invalid JWT, please login again."))
        }
    }

    /**
     * Check if Identity Provider used for /auth/id/:idp is existing
     *
     * @param ctx
     * @return
     */
    private fun idpExists(ctx: RoutingContext): Single<IdentityProvider> {
        return openIdConnector.getIdentityProvider(ctx.pathParam("idp"))
            ?.let { Single.just(it) }
            ?: Single.error(ServerError("Identity Provider with ID " + ctx.pathParam("idp") + " does not exist"))
    }

    /**
     * Is the parameter code present
     *
     * @param ctx
     * @return
     */
    private fun codePresent(ctx: RoutingContext): Completable {
        logger.trace("Code present? ({})", ctx.request().getParam(AuthParams.CODE))
        return ctx.request().getParam(AuthParams.CODE)
            ?.let { Completable.complete() }
            ?: Completable.error(ServerError("Code is not set."))
    }

    /**
     * Exchange the received code from the Identity Provider for a token from the idp.
     *
     * @param ctx
     * @param idp
     * @return
     */
    private fun exchangeIdpCodeForToken(ctx: RoutingContext, idp: IdentityProvider): Completable {
        val code = ctx.request().getParam(AuthParams.CODE) // Idp's code
        val compositeState = CompositeState.decode(ctx.request().getParam(AuthParams.STATE)) // own loginStateKey
        var loginStateKey = compositeState.state
        var remember_me = compositeState.rememberMe
        logger.trace("Exchanging IdpCode for (idp)Token: (code: {})", code)
        return idp
            // Get token from idp
            .rxAuthenticate(
                JsonObject()
                    .put(AuthParams.CODE, code)
                    .put(
                        AuthParams.REDIRECT_URI,
                        config.authPublicUri + getIdpCallbackEndpoint(config) + "/" + idp.id
                    ) // because it should be the same as the first one (not because it will be redirected again)
                    .put(AuthParams.CLIENT_ID, idp.clientId)
                    .put(AuthParams.CLIENT_SECRET, idp.clientSecret)
            )
            .flatMap {
                getUserByEmail(idp.retrieveEmail(it))
                    .onErrorResumeNext(registerUser(idp, it))
                    .map { oblxUser -> idp.transformToClaim(it, oblxUser) }
            }
            .onErrorResumeNext { Single.error(ServerError("Something went wrong while communicating with the IdentityProvider")) }
            // Mint token and store token in session
            .flatMap { claims ->
                logger.trace("User: {}", claims.toJwtClaims().toJson())
                getActiveSessionOrCreate(claims.sub)                // create sessions
                    .flatMap { session ->
                        authStore.rxGetNewSessionSid(session).map { sid -> Pair(sid, session) }
                    }   // generate unique sid
                    .flatMap { (sid, session) ->
                        authStore.rxSaveSession(
                            session.copy(
                                idToken = claims.toMap(),
                                id_token = tokenMinter.mintToken(claims, sid),
                                loginStateKey = loginStateKey
                            )
                        ).map { session.sub }
                    }
            }
            .flatMap { sub ->
                logger.trace { "Saving sessionid in temporary loginState Redis entry" }
                authStore.rxGetLogin(loginStateKey).toSingle()
                    .onErrorResumeNext(Single.error(UnauthorizedException("Auth code was invalidated in the meantime")))
                    .flatMap { loginState ->
                        val newloginState = loginState.copy(sub = sub, remember_me = remember_me)
                        authStore
                            .rxReplaceLogin(loginStateKey, loginState, newloginState)
                            .flatMap { worked ->
                                if (worked) {
                                    Single.just(newloginState)
                                } else {
                                    Single.error(UnauthorizedException("Auth code was invalidated in the meantime"))
                                }
                            }
                    }
            }
            .flatMapCompletable {
                // Redirect to client redirect Uri with code and client state
                logger.trace("Redirecting to client with clientState and auth code to exchange for api token later")
                var uri = it.redirectUri
                uri += "?code=" + URLEncoder.encode(loginStateKey, StandardCharsets.UTF_8)
                uri += "&state=" + URLEncoder.encode(it.state, StandardCharsets.UTF_8)
                ctx.response().putHeader(HttpHeaders.LOCATION, uri).setStatusCode(302).rxEnd()
            }

    }

    private fun registerUser(idp: IdentityProvider, idpUser: User): Single<idlab.obelisk.definitions.catalog.User> {
        if (idp.id != "local") {
            val user = idp.convertToObeliskUser(idpUser)
            return metaStore.createUser(user).map { user.copy(id = it) }
        } else {
            return Single.error(UnauthorizedException("Local Idp cannot create users"))
        }
    }

    /**
     * Tries to find an existing session.
     * If not found a new sessionId is generated with sessionId and sub already filled in
     * @return A Session object with sessionId and sub already filled in (at least)
     */
    private fun getActiveSessionOrCreate(sub: String): Single<Session> {
        return authStore.rxGetSession(sub)
            .toSingle(Session(sub = sub))
    }

    private fun getUserByEmail(email: String): Single<idlab.obelisk.definitions.catalog.User> {
        return metaStore.queryUsers(Eq(UserField.EMAIL, email)).flattenAsFlowable { it.items }.singleOrError()
    }

}


