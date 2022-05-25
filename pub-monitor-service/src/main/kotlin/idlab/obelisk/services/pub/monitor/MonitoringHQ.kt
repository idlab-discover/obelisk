package idlab.obelisk.services.pub.monitor

import hu.akarnokd.rxjava3.bridge.RxJavaBridge
import idlab.obelisk.definitions.framework.OblxConfig
import idlab.obelisk.utils.service.http.NotFoundException
import io.reactivex.Completable
import io.reactivex.Single
import io.reactivex.rxkotlin.toFlowable
import io.vertx.core.json.JsonObject
import org.redisson.api.RedissonClient
import java.lang.Integer.min
import java.util.concurrent.TimeUnit

interface Agent {

    fun id(): String
    fun trigger(): Single<Pair<Boolean, JsonObject>>

}

enum class Status {
    HEALTHY, FAILED, DEGRADED, UNKNOWN
}

// Map of timestamp to bool representing if the component was up.
data class StatusWindow(
    val componentId: String,
    val windowDurationMs: Long,
    val groupByMs: Long,
    val history: List<Status>
)

internal const val GLOBAL = "global"

class MonitoringHQ(
    private val redis: RedissonClient,
    config: OblxConfig,
    private vararg val agents: Agent
) {

    private val windowMinutes =
        config.getLong(ENV_MONITORING_STATUS_WINDOW_MINUTES, DEFAULT_MONITORING_STATUS_WINDOW_MINUTES)
    private val periodMs = config.getLong(ENV_MONITORING_PERIOD_MS, DEFAULT_MONITORING_PERIOD_MS)
    private val stats: MutableMap<String, JsonObject> = mutableMapOf()

    private fun bufferKey(id: String): String {
        return "monitoring_$id"
    }

    /**
     * Triggers a monitoring cycle
     */
    fun cycle(): Completable {
        val timestamp = System.currentTimeMillis()
        return agents.toFlowable().concatMapSingle { agent ->
            agent.trigger()
                .flatMap { report ->
                    redis.rxJava().getTimeSeries<Boolean>(bufferKey(agent.id()))
                        .add(timestamp, report.first, windowMinutes, TimeUnit.MINUTES)
                        .to(RxJavaBridge.toV2Completable())
                        .doOnError { it.printStackTrace() }
                        .onErrorComplete()
                        .toSingleDefault(report)
                }
                .doOnSuccess { stats[agent.id()] = it.second }
                .map { it.first }
        }.toList()
            .flatMapCompletable {
                redis.rxJava().getTimeSeries<Boolean>(bufferKey(GLOBAL)).add(timestamp, it.all { value ->
                    value == true
                }, windowMinutes, TimeUnit.MINUTES)
                    .to(RxJavaBridge.toV2Completable())
                    .doOnError { err -> err.printStackTrace() }
                    .onErrorComplete()
            }
    }

    /**
     * Generates a report of the status of the system
     */
    fun report(): List<JsonObject> {
        return stats.map { it.value }
    }

    fun status(maxRecords: Int?): Single<List<StatusWindow>> {
        val currentTime = System.currentTimeMillis()
        return agents.map { it.id() }.plus(GLOBAL).toFlowable()
            .flatMapSingle { componentId -> statusById(componentId, currentTime, maxRecords) }
            .toList()
    }

    fun statusById(
        componentId: String,
        atTimestamp: Long = System.currentTimeMillis(),
        maxRecords: Int?
    ): Single<StatusWindow> {
        return if (agents.map { it.id() }.plus(GLOBAL).any { it == componentId }) {
            val windowMs = TimeUnit.MILLISECONDS.convert(windowMinutes, TimeUnit.MINUTES)
            val startTs = atTimestamp - windowMs
            redis.rxJava().getTimeSeries<Boolean>(bufferKey(componentId))
                .entryRange(startTs, atTimestamp)
                .to(RxJavaBridge.toV2Single())
                .map { results ->
                    val nrOfBuckets =
                        maxRecords?.let { min((windowMs / periodMs).toInt(), it) } ?: (windowMs / periodMs).toInt()
                    val intervalMs = windowMs / nrOfBuckets
                    val buckets = (0 until nrOfBuckets).map { b ->
                        val bucketStartTs = startTs + (b * intervalMs)
                        (bucketStartTs until bucketStartTs + intervalMs)
                    }

                    val groupByBuckets = results.filterNot { it.value == null }
                        .groupBy { result -> buckets.find { result.timestamp in it } }
                    StatusWindow(
                        componentId,
                        windowMs,
                        intervalMs,
                        buckets.map { bucket ->
                            try {
                                if (componentId == GLOBAL) {
                                    aggregateGlobalStatus(groupByBuckets[bucket]?.map { if (it.value) Status.HEALTHY else Status.FAILED }
                                        ?: emptyList())
                                } else {
                                    aggregateStatus(groupByBuckets[bucket]?.map { if (it.value) Status.HEALTHY else Status.FAILED }
                                        ?: emptyList())
                                }
                            } catch (ex: Throwable) {
                                throw ex
                            }
                        }
                    )
                }
        } else {
            Single.error { NotFoundException("There is no component with id $componentId...") }
        }
    }

    private fun aggregateStatus(stati: List<Status>): Status {
        return when {
            stati.isEmpty() -> Status.UNKNOWN
            stati.any { it == Status.FAILED } -> Status.FAILED
            else -> Status.HEALTHY
        }
    }

    private fun aggregateGlobalStatus(stati: List<Status>): Status {
        return when {
            stati.isEmpty() -> Status.UNKNOWN
            stati.all { it == Status.FAILED } -> Status.FAILED
            stati.any { it == Status.FAILED } -> Status.DEGRADED
            else -> Status.HEALTHY
        }
    }

}