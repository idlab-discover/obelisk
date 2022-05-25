package idlab.obelisk.plugins.accessmanager.basic

import idlab.obelisk.definitions.SELECT_ALL
import idlab.obelisk.definitions.catalog.*
import idlab.obelisk.plugins.accessmanager.basic.store.TokenModifyStore
import idlab.obelisk.utils.service.http.UnauthorizedException
import io.reactivex.Completable
import io.reactivex.Maybe
import io.reactivex.Observable
import io.reactivex.Single
import io.vertx.core.http.HttpHeaders
import io.vertx.core.json.Json
import io.vertx.reactivex.core.http.HttpServerRequest
import mu.KotlinLogging
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BasicAccessManager @Inject constructor(
    val metaStore: MetaStore,
    val tokenModifyStore: TokenModifyStore
) : AccessManager {
    private val logger = KotlinLogging.logger { }

    override fun getAccessInfo(user: User, clientId: String?): Single<Map<String, Grant>> {
        return createAccessInfo(user, clientId)
    }

    override fun getToken(request: HttpServerRequest): Single<Token> {
        return getOpaqueToken(request)
            .flatMap {
                tokenModifyStore.rxGetToken(it)
                    .toSingle()
                    .onErrorResumeNext(Single.error(UnauthorizedException("Invalid access_token")))
            }
    }

    override fun invalidateToken(token: Token): Completable {
        logger.debug("Invalidating token: recalculating new token based on old token properties")
        // Recalculate new token and replace
        return recreateToken(token.opaqueToken, token.user!!.id!!, token?.client?.id)
            .flatMap { newToken -> tokenModifyStore.rxReplaceToken(token, newToken) }
            .ignoreElement()
    }

    /**
     * Invalidates sessions linked to the given users and clients.
     */
    override fun invalidateSessions(userIds: Set<String>, clientIds: Set<String>): Completable {
        var subs = setOfNotNull(*userIds.map { "u$it" }.toTypedArray(), *clientIds.map { "c$it" }.toTypedArray())
        return tokenModifyStore.rxGetTokensBySubs(subs)
            .flatMapObservable { Observable.fromIterable(it) }
            .flatMapSingle { recreateToken(it.opaqueToken, it.user.id!!, it.client?.id) }
            .toMap { it.opaqueToken }
            .flatMapCompletable { tokenModifyStore.rxBatchPut(it) }
    }

    private fun getOpaqueToken(request: HttpServerRequest): Single<String> {
        val header = request.getHeader(HttpHeaders.AUTHORIZATION.toString())
        if (header == null || !header.startsWith("Bearer ")) {
            logger.debug("Authorization header: {}", header)
            return Single.error(UnauthorizedException("No valid Bearer token present in Authorization header."))
        }
        return Single.just(header.substring("Bearer ".length))
    }

    /**
     * Recreates a token based on the values of an old token.
     *
     * This might change the access info and usage limits.
     * All other values like user/client/opaqueToken remain the same
     *
     * @param accessToken The old opaque access token (as given to clients/users)
     * @param userId The user id of this Token
     * @param clientId The clientId if there is any
     */
    private fun recreateToken(accessToken: String, userId: String, clientId: String?): Single<Token> {
        return metaStore.getUser(userId).toSingle()
            .flatMap { user ->
                if (!clientId.isNullOrEmpty()) {
                    metaStore.getClient(clientId).toSingle().map { Pair(user, it) }
                } else {
                    Single.just(Pair(user, null))
                }
            }
            .flatMap { pair ->
                getAccessInfo(pair.first, pair.second?.id)
                    .zipWith(metaStore.getAggregatedUsageLimits(pair.first, pair.second),
                        { ai, limits -> Token(pair.first, pair.second, ai, limits, accessToken) })
            }
    }

    /**
     * Creates an AccessInfo object by cross referencing all rights applying to the user, its usergroups, client and datasets
     *
     * @param holder
     * @param clientId
     * @return
     */
    private fun createAccessInfo(user: User, clientId: String?): Single<Map<String, Grant>> {
        return metaStore.getAggregatedGrantsForUser(user.id!!)
            .flatMap { restrictToClientRights(it.toMutableMap(), clientId, user.platformManager) }
            .map { it.filter { entry -> entry.value.permissions.isNotEmpty() } } // Remove entries with no permissions from grantMap
            .doOnError { logger.error(it) { "Error in BasicAccessManager#createAccessInfo()" } }
    }

    /**
     * Takes a map of datasetId to AccessGrant entries and checks if there are any restrictions imposed by a possible client.
     * If the clientId is null, there is no client, and the grantMap is passed through as is.
     * If the clientId is not null, the client info is loaded and scope and clientRestrictions are checked to
     * possibly delete permissions.
     * If the grantMap contains an entry with an empty permission list, the entry is removed form the map.
     *
     * @param grantMap
     * @param clientId
     * @return
     */
    private fun restrictToClientRights(
        grantMap: MutableMap<String, Grant>,
        clientId: String?,
        isAdmin: Boolean
    ): Single<Map<String, Grant>> {
        return clientId?.let {
            metaStore.getClient(clientId)
                .toSingle()
                .map { client ->
                    // Admin has no grantMap, but can implicitly do everything: clientRestrictions are used as actual permissions
                    if (isAdmin && !client.onBehalfOfUser) {
                        logger.trace { "Client owned by admin: adding all clientRestrictions to grantMap." }
                        client.restrictions.forEach { grantMap[it.datasetId] = Grant(it.permissions, SELECT_ALL) }
                    }
                    // Loop over grantMap entries an restrict as needed
                    grantMap.replaceAll { datasetId, accessGrant ->
                        val tmpPermissions = accessGrant.permissions.toMutableSet()

                        // Remove permissions 'bigger' than the client scope
                        tmpPermissions.removeIf { !client.scope.contains(it) }

                        // client on behalf of its own
                        if (!client.onBehalfOfUser) {
                            // if this dataset is part of the dataset restrictions: clear this dataset=>accessgrant-mapping's permissions of those not specified in the ClientRestriction
                            val clientRestrictionOrNull = client.restrictions.firstOrNull { it.datasetId == datasetId }
                            // Remove any permission of the localGrant that are not in the clientRestriction permissions for this dataset
                            clientRestrictionOrNull?.let { cr -> tmpPermissions.removeIf { !cr.permissions.contains(it) } }
                        }
                        Grant(tmpPermissions, accessGrant.readFilter)
                    }
                    grantMap
                }
        } ?: Single.just(grantMap) // Just grantMap if no clientId
    }


    private fun toToken(json: String): Token {
        return Json.decodeValue(json, Token::class.java)
    }
}


