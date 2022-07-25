package idlab.obelisk.services.pub.monitor.agents

import com.google.common.math.StatsAccumulator
import idlab.obelisk.client.OblxClient
import idlab.obelisk.definitions.TimestampPrecision
import idlab.obelisk.definitions.data.MetricEvent
import idlab.obelisk.definitions.framework.OblxConfig
import idlab.obelisk.services.pub.monitor.*
import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.Timer
import io.reactivex.Single
import io.reactivex.rxkotlin.subscribeBy
import io.vertx.core.json.JsonObject
import io.vertx.micrometer.backends.BackendRegistries
import io.vertx.reactivex.core.Vertx
import java.time.Instant
import java.util.*
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

private const val SSE_LATENCY_METRIC = "oblx.monitor.sse.latency"

@Singleton
class StreamingAgent @Inject constructor(
    private val vertx: Vertx,
    private val oblxClient: OblxClient,
    config: OblxConfig
) :
    AbstractApiAgent() {

    private val microMeterRegistry = BackendRegistries.getDefaultNow()
    private val sseLatency = Timer
        .builder(SSE_LATENCY_METRIC)
        .publishPercentiles(0.5, 0.95)
        .publishPercentileHistogram()
        .description("Times the latency of a complete ingest -> stream -> receive via SSE cycle.")
        .register(microMeterRegistry)

    private var dropouts = 0
    private var lastReceivedEvent = LatestEventContainer()
    private var lagAccumulator = StatsAccumulator()
    private var matchRateAccumulator = StatsAccumulator()
    private val monitoringPeriodMs = config.getLong(ENV_MONITORING_PERIOD_MS, DEFAULT_MONITORING_PERIOD_MS)

    init {
        streamEvents()
    }

    private fun streamEvents() {
        oblxClient.openStream(streamId = datasetId, receiveBacklog = false)
            .filter { it.timestamp >= SERVICE_STARTUP_TIME_MS } // Ignore events not produced by the current instance of the monitor service
            .onBackpressureBuffer()
            .subscribeBy(
                onNext = { processEvent(it) },
                onError = { err ->
                    logger.error(err) { "An error occurred while streaming events!" }
                    vertx.setTimer(monitoringPeriodMs) {
                        streamEvents()
                    }
                },
                onComplete = {
                    logger.warn { "Streaming completed, did not expect this to happen. Restarting..." }
                    vertx.setTimer(monitoringPeriodMs) {
                        streamEvents()
                    }
                }
            )
    }

    override fun performCall(): Single<CallResult> {
        val event = MetricEvent(
            timestamp = System.currentTimeMillis(),
            metric = metricName,
            value = UUID.randomUUID().toString()
        )
        return oblxClient.ingest(
            datasetId,
            listOf(event),
            TimestampPrecision.milliseconds,
            OblxClient.IngestMode.stream_only
        )
            .toSingleDefault(CallResult(state = event))
    }

    override fun validate(callState: Any?, report: JsonObject): Single<JsonObject> {
        report.clear()
        report.put("component", id())
        lastReceivedEvent.get()?.let { (event, tsReceivedAt) ->
            report.put("lastEventThrough", Instant.ofEpochMilli(tsReceivedAt))
            // If data has still come through within the window of the monitoring period, we consider the streaming service to be operational (even if there is a lot of latency)
            report.put("lastCallSucceeded", (tsReceivedAt - event.timestamp) <= monitoringPeriodMs)
        }
        report.put("connectionDropouts", dropouts)
        report.put("successRate", matchRateAccumulator.safeMean(1.0))
        report.put("meanLagMs", lagAccumulator.safeMean(0.0))
        report.put("minLagMs", lagAccumulator.safeMin())
        report.put("maxLagMs", lagAccumulator.safeMax())

        return Single.just(report)
    }

    override fun id(): String {
        return "streaming-service"
    }

    private fun processEvent(event: MetricEvent) {
        if (lastReceivedEvent.update(event)) {
            val delay = System.currentTimeMillis() - event.timestamp
            sseLatency.record(delay, TimeUnit.MILLISECONDS)
            lagAccumulator.add(delay.toDouble())
            // If an event cannot be received by the SSE client in the window of a monitoring period (default 15s), it is considered a failure
            matchRateAccumulator.add(if (delay <= monitoringPeriodMs) 1.0 else 0.0)
        }
    }

}

data class LatestEventContainer(private var event: MetricEvent? = null, private var receivedTs: Long? = null) {

    // Returns 'true' if the update resulted in the new event being the latest
    fun update(newEvent: MetricEvent): Boolean {
        event = event?.takeIf { it.timestamp > newEvent.timestamp } ?: newEvent

        return if (event == newEvent) {
            receivedTs = System.currentTimeMillis()
            true
        } else {
            false
        }
    }

    // Returns the last received event + ts at which it was received (if any)
    fun get(): Pair<MetricEvent, Long>? {
        return if (event != null && receivedTs != null) Pair(event!!, receivedTs!!) else null
    }


}
