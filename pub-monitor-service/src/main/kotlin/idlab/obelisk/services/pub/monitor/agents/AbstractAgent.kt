package idlab.obelisk.services.pub.monitor.agents

import com.google.common.math.StatsAccumulator
import idlab.obelisk.services.pub.monitor.Agent
import io.reactivex.Single
import io.vertx.core.json.JsonObject
import mu.KotlinLogging

data class CallResult(val success: Boolean = true, val state: Any? = null)

abstract class AbstractApiAgent(
    private var rttAccumulator: StatsAccumulator = StatsAccumulator(),
    private var successAccumulator: StatsAccumulator = StatsAccumulator()
) : Agent {

    val logger = KotlinLogging.logger { }

    override fun trigger(): Single<Pair<Boolean, JsonObject>> {
        val start = System.currentTimeMillis()
        return performCall()
            .onErrorReturn { err ->
                logger.warn(err) { "Error while performing status check for ${id()}" }
                CallResult(success = false)
            }
            .flatMap { result ->
                if (result.success) {
                    rttAccumulator.add((System.currentTimeMillis() - start).toDouble())
                    successAccumulator.add(1.0)
                } else {
                    successAccumulator.add(0.0)
                }

                validate(
                    result.state, JsonObject()
                        .put("component", id())
                        .put("lastCallSucceeded", result.success)
                        .put("callSuccessRate", successAccumulator.safeMean(1.0))
                        .put("meanRTT", rttAccumulator.safeMean())
                        .put("maxRTT", rttAccumulator.safeMax())
                        .put("minRTT", rttAccumulator.safeMin())
                ).map {
                    val state = it.getBoolean("lastCallSucceeded")?:false
                    Pair(state, it)
                }
            }
    }

    /**
     * Perform the Obelisk call (or calls) for the monitoring component.
     * The call is timed!
     * You can return state as a Single (which is passed on to the subsequent validation call).
     */
    abstract fun performCall(): Single<CallResult>

    /**
     * Validate the call that was performed (e.g. check latency thresholds)
     */
    abstract fun validate(callState: Any?, report: JsonObject): Single<JsonObject>
}

internal fun StatsAccumulator.safeMean(default: Double = 0.0): Double {
    return if (this.count() > 0) this.mean() else default
}

internal fun StatsAccumulator.safeMax(): Double {
    return if (this.count() > 0) this.max() else 0.0
}

internal fun StatsAccumulator.safeMin(): Double {
    return if (this.count() > 0) this.min() else 0.0
}