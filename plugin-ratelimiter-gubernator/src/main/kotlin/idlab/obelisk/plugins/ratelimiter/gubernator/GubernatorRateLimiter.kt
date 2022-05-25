package idlab.obelisk.plugins.ratelimiter.gubernator

import idlab.obelisk.definitions.catalog.Token
import idlab.obelisk.definitions.catalog.UsageLimitId
import idlab.obelisk.definitions.framework.OblxConfig
import idlab.obelisk.definitions.ratelimiting.LimitExceededException
import idlab.obelisk.definitions.ratelimiting.RateLimiter
import io.reactivex.Single
import io.reactivex.rxkotlin.toFlowable
import io.vertx.reactivex.core.Vertx
import io.vertx.reactivex.ext.web.RoutingContext
import mu.KotlinLogging
import java.net.URI

// Response header containing the requests quota in the time window
private const val RL_HEADER_LIMIT = "RateLimit-Limit"

// Response header containing the remaining requests quota in the current window
private const val RL_HEADER_REMAINING = "RateLimit-Remaining"

// Response header containing the time remaining in the current window, specified as a duration in ms;
private const val RL_HEADER_RESET = "RateLimit-Reset"

class GubernatorRateLimiter(vertx: Vertx, oblxConfig: OblxConfig) : RateLimiter {

    private val logger = KotlinLogging.logger {  }
    private val connectionUri = URI.create(oblxConfig.gubernatorConnectionUri)
    private val client = GubernatorClient(vertx, connectionUri.host, connectionUri.port)

    override fun apply(ctx: RoutingContext?, token: Token, increase: Map<UsageLimitId, Int>): Single<Token> {
        return increase.entries.toFlowable()
            .flatMapSingle { request ->
                client.getRateLimits(RateLimitsSpec.decrementAndGet(token, request.key, request.value))
                    .flatMap { resp ->
                        ctx?.response()?.putHeader(RL_HEADER_LIMIT, "${resp.limit}")?.putHeader(RL_HEADER_REMAINING, "${resp.remaining}")
                            ?.putHeader(RL_HEADER_RESET, "${resp.resetTime() - System.currentTimeMillis()}")

                        if (resp.isOverLimit()) {
                            logger.debug{ "Rate limit triggered: $resp" }
                            Single.error<Token>(LimitExceededException(resp.limit, resp.resetTime()))
                        } else {
                            Single.just(token)
                        }
                    }
            }.lastOrError()
    }

    override fun getStatus(token: Token): Single<Map<UsageLimitId, Long>> {
        return UsageLimitId.values()
            .toFlowable()
            .flatMapSingle { opCode ->
                client.getRateLimits(RateLimitsSpec.get(token, opCode)).map { Pair(opCode, it) }
            }
            .toList()
            .map { result -> result.map { Pair(it.first, it.second.remaining) }.toMap() }
    }
}