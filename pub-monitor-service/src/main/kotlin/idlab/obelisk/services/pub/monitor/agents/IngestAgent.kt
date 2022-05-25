package idlab.obelisk.services.pub.monitor.agents

import com.google.common.math.StatsAccumulator
import idlab.obelisk.client.OblxClient
import idlab.obelisk.definitions.DataRange
import idlab.obelisk.definitions.Ordering
import idlab.obelisk.definitions.TimestampPrecision
import idlab.obelisk.definitions.data.*
import idlab.obelisk.definitions.framework.OblxConfig
import idlab.obelisk.services.pub.monitor.*
import idlab.obelisk.utils.service.reactive.flatMapSingle
import idlab.obelisk.utils.service.reactive.retryWithExponentialBackoff
import io.reactivex.Completable
import io.reactivex.Single
import io.vertx.core.json.JsonObject
import java.util.*
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

class NoMatchException : RuntimeException()

@Singleton
class IngestAgent @Inject constructor(private val oblxClient: OblxClient, config: OblxConfig) : AbstractApiAgent() {

    private val initialDelayMs =
        config.getLong(ENV_MONITORING_INGEST_CHECK_INITIAL_DELAY_MS, DEFAULT_MONITORING_INGEST_CHECK_INITIAL_DELAY_MS)
    private val maxRetries =
        config.getInteger(ENV_MONITORING_INGEST_CHECK_MAX_RETRIES, DEFAULT_MONITORING_INGEST_CHECK_MAX_RETRIES)
    private var persistedRate = StatsAccumulator()

    override fun id(): String {
        return "ingest-service"
    }

    override fun performCall(): Single<CallResult> {
        val event = MetricEvent(
            timestamp = System.currentTimeMillis(),
            metric = metricName,
            value = UUID.randomUUID().toString()
        )
        return oblxClient.ingest(datasetId, event).toSingleDefault(CallResult(state = event))
    }

    override fun validate(callState: Any?, report: JsonObject): Single<JsonObject> {
        return Completable.complete()
            .delay(initialDelayMs, TimeUnit.MILLISECONDS)
            .flatMapSingle {
                oblxClient.queryEvents(
                    EventsQuery(
                        dataRange = DataRange(datasets = listOf(datasetId), metrics = listOf(metricName)),
                        timestampPrecision = TimestampPrecision.milliseconds,
                        orderBy = OrderBy(fields = listOf(IndexField.timestamp), ordering = Ordering.desc)
                    )
                )
            }
            .flatMapCompletable { result ->
                if (isEventEqual(callState as MetricEvent, result.items.firstOrNull())) {
                    persistedRate.add(1.0)
                    Completable.complete()
                } else {
                    Completable.error(NoMatchException())
                }
            }
            .retryWithExponentialBackoff(initialDelayMs, maxRetries)
            .doOnError {
                persistedRate.add(0.0)
                // Also set lastCallSucceeded in report to false
                report.put("lastCallSucceeded", false)
            }
            .onErrorComplete()
            .toSingle {
                report.put("persistedRate", persistedRate.safeMean(1.0))
                report
            }
    }

    private fun isEventEqual(expected: MetricEvent, actual: MetricEvent?): Boolean {
        return if (actual != null) {
            expected.timestamp == actual.timestamp && expected.value == actual.value
        } else {
            false
        }
    }
}
