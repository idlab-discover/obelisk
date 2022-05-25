package idlab.obelisk.definitions.catalog

import idlab.obelisk.definitions.FilterExpression
import idlab.obelisk.definitions.SELECT_ALL
import io.reactivex.Completable
import io.reactivex.Maybe
import io.reactivex.Single
import io.vertx.reactivex.core.http.HttpServerRequest

interface AccessManager {
    /**
     * Returns a Single of AccessInfo for the user and optionally the client acting on its behalf.
     *
     * @param user: The user
     * @param clientId: Id of the client, can be null.
     * @return Single of AccessInfo
     */
    fun getAccessInfo(user: User, clientId: String? = null): Single<Map<String, Grant>>

    /**
     * Parses the opaque token string from the Authorization header and exchanges it for the corresponding token.
     * If the Token is not found, the appropriate HTTP errors are thrown wrapped in a Single.error(...)
     * The result may be cached.
     *
     * @param request The incoming request
     * @return Single of a Token instance
     */
    fun getToken(request: HttpServerRequest): Single<Token>

    /**
     * Invalidates the token, since it might be cached.
     * Enforces reevaluation of the AccessInfo next time the token is retrieved using getToken (ensures the next API call gets an up-to-date token).
     *
     * @param token The token that has to be invalidated
     */
    fun invalidateToken(token: Token): Completable

    fun invalidateSessions(userIds: Set<String> = emptySet(), clientIds: Set<String> = emptySet()): Completable
}

data class Token(
    val user: User,
    val client: Client? = null,
    /**
     * Map of dataset IDs to access Grants, based on the Access Control groups the user is a member of in Datasets.
     */
    val grants: Map<String, Grant>,
    /**
     * Actual usage limit for the session represented by this Token (evaluated by looking at limits set for the User, Team memberships and platform defaults)
     */
    val usageLimit: UsageLimit,
    /**
     * Opaque token that will be sent to the user. It is also used as the key in redis for this token.
     */
    val opaqueToken: String
)

data class Grant(
    val permissions: Set<Permission> = setOf(Permission.READ),
    val readFilter: FilterExpression = SELECT_ALL
)