package idlab.obelisk.definitions.ratelimiting

import idlab.obelisk.definitions.catalog.Token
import idlab.obelisk.definitions.catalog.UsageLimit
import idlab.obelisk.definitions.catalog.UsageLimitId
import io.reactivex.Single
import io.vertx.reactivex.ext.web.RoutingContext
import java.lang.RuntimeException

interface RateLimiter {

    /**
     * Applies rate limiting for an Obelisk call
     *
     * @param ctx Optional Web context, allows the rate limiter to write informative response headers
     * @param token The token being used for making the call
     * @param increase Map of specific UsageLimitIds to the value of the increase for this call (e.g. maxHourlyPrimitiveEventQueries -> 1 when the call is a query on primitive events)
     *
     * @return The supplied Token is passed on upon success,
     * otherwise an Exception is produced through the Single.
     */
    fun apply(ctx: RoutingContext? = null, token: Token, increase: Map<UsageLimitId, Int>): Single<Token>

    /**
     * Applies rate limiting for an Obelisk call
     *
     * @param ctx Optional Web context, allows the rate limiter to write informative response headers
     * @param token The token being used for making the call
     * @param limitId ID of the limit to increase for the call (e.g. maxHourlyPrimitiveEventQueries)
     * @param increase Optional value for the limit increase for the call (default: 1)
     *
     * @return The supplied Token is passed on upon success,
     * otherwise an Exception is produced through the Single.
     */
    fun apply(ctx: RoutingContext? = null, token: Token, limitId: UsageLimitId, increase: Int = 1): Single<Token> {
        return apply(ctx, token, mapOf(limitId to increase))
    }

    /**
     * Retrieves the rate limiting status for a session
     *
     * @param token The token representing the session
     *
     * @return A Single producing an instance of a Map which contains the current budget spent for each UsageLimitId type.
     */
    fun getStatus(token: Token): Single<Map<UsageLimitId, Long>>
}

class LimitExceededException(limit: Long, resetTime: Long) :
    RuntimeException("The limit of $limit for this operation has been reached (resets at $resetTime)")