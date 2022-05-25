package idlab.obelisk.plugins.ratelimiter.gubernator

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import idlab.obelisk.definitions.catalog.Token
import idlab.obelisk.definitions.catalog.UsageLimitId
import io.reactivex.Single
import io.vertx.core.json.JsonArray
import io.vertx.core.json.JsonObject
import io.vertx.ext.web.client.WebClientOptions
import io.vertx.reactivex.core.Vertx
import io.vertx.reactivex.ext.web.client.WebClient

private const val REQUEST_PATH = "/v1/GetRateLimits"

class GubernatorClient(vertx: Vertx, host: String = "localhost", port: Int = 9080) {

    private val httpClient = WebClient.create(vertx, WebClientOptions().setDefaultHost(host).setDefaultPort(port))

    fun getRateLimits(spec: RateLimitsSpec): Single<RateLimitsResponse> {
        val requestObj = JsonObject().put("requests", JsonArray().add(spec))
        return httpClient.post(REQUEST_PATH)
            .rxSendJsonObject(requestObj)
            .flatMap { resp ->
                if (resp.statusCode() == 200) {
                    try {
                        Single.just(
                            resp.bodyAsJsonObject().getJsonArray("responses").getJsonObject(0)
                                .mapTo(RateLimitsResponse::class.java)
                        )
                    } catch (err: Throwable) {
                        Single.error<RateLimitsResponse>(
                            RuntimeException(
                                "Could not parse rate limiter response: ${resp.bodyAsString()}",
                                err
                            )
                        )
                    }
                } else {
                    Single.error<RateLimitsResponse>(
                        RuntimeException(
                            "Unexpected error while contacting rate limiter service: ${resp.bodyAsString()}",
                        )
                    )
                }
            }
    }

}

data class RateLimitsSpec(
    val name: String,
    val unique_key: String,
    val hits: Int = 1,
    val duration: Long = 1000 * 60 * 60, // Default is per hour
    val limit: Long
) {
    companion object {

        fun decrementAndGet(token: Token, opCode: UsageLimitId, amount: Int): RateLimitsSpec {
            return get(token, opCode).copy(hits = amount)
        }

        fun get(token: Token, opCode: UsageLimitId): RateLimitsSpec {
            val uniqueKey =
                if (token.client != null && !token.client!!.onBehalfOfUser && token.client!!.teamId != null) {
                    /**
                     * If the request is not on behalf of a User and is coming from a Client that was created in the context of a Team,
                     * the Client has individual limits, not tied to the User that issued the Client.
                     */
                    "client.id=${token.client!!.id}"
                } else {
                    // In all other cases, Client is bound to the same limits as the User that issued the Client.
                    "user.id=${token.user.id}"
                }
            return RateLimitsSpec(
                name = opCode.toString(),
                unique_key = uniqueKey,
                hits = 0,
                limit = token.usageLimit.values[opCode] ?: 0
            )
        }

    }
}

@JsonIgnoreProperties(ignoreUnknown = true)
data class RateLimitsResponse(
    val limit: Long,
    val remaining: Long = 0,
    private val reset_time: String,
    private val status: String? = null
) {
    fun resetTime(): Long {
        return reset_time.toLong()
    }

    fun isOverLimit(): Boolean {
        return status != null && status == "OVER_LIMIT"
    }
}
