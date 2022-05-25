package idlab.obelisk.plugins.ratelimiter.gubernator

import idlab.obelisk.definitions.framework.OblxConfig
import idlab.obelisk.definitions.framework.OblxModule
import idlab.obelisk.definitions.ratelimiting.RateLimiter
import io.vertx.reactivex.core.Vertx
import org.codejargon.feather.Provides
import javax.inject.Singleton

class GubernatorRateLimiterModule : OblxModule {

    @Singleton
    @Provides
    fun ratelimiter(vertx: Vertx, oblxConfig: OblxConfig): RateLimiter {
        return GubernatorRateLimiter(vertx, oblxConfig)
    }

}