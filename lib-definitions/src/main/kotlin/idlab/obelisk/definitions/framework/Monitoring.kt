package idlab.obelisk.definitions.framework

import com.fasterxml.jackson.annotation.JsonProperty
import io.reactivex.Single
import java.lang.Integer.max
import java.util.concurrent.TimeUnit


data class TimedFloat(val timestamp: Long, val value: Float)

data class MonitoringTimeSeries(
    @JsonProperty("metric")
    val labels: Map<String, String>,
    val values: List<TimedFloat>
)

data class MonitoringDuration(val value: Int, val unit: MonitoringDurationUnit) {

    companion object {
        fun from(duration: Long, unit: TimeUnit): MonitoringDuration {
            return when (unit) {
                TimeUnit.HOURS -> MonitoringDuration(duration.toInt(), MonitoringDurationUnit.HOURS)
                TimeUnit.MINUTES -> MonitoringDuration(duration.toInt(), MonitoringDurationUnit.MINUTES)
                else -> MonitoringDuration(
                    java.lang.Long.max(1L, TimeUnit.SECONDS.convert(duration, unit)).toInt(),
                    MonitoringDurationUnit.SECONDS
                )
            }
        }
    }

    override fun toString(): String {
        return "$value${unit.unitStr}"
    }
}

enum class MonitoringDurationUnit(val unitStr: String) {
    SECONDS("s"), MINUTES("m"), HOURS("h")
}

enum class QueryType {
    EVENTS, STATS
}

enum class ConsumptionType {
    QUERIES, STREAMING
}

interface MonitoringDataProvider {

    fun countIngestRequests(
        fromMs: Long,
        toMs: Long,
        duration: MonitoringDuration,
        datasetId: String? = null
    ): Single<MonitoringTimeSeries>

    fun countQueryRequests(
        fromMs: Long,
        toMs: Long,
        duration: MonitoringDuration,
        datasetId: String? = null,
        restrictTo: QueryType? = null
    ): Single<List<MonitoringTimeSeries>>

    fun countIngestedEvents(
        fromMs: Long,
        toMs: Long,
        duration: MonitoringDuration,
        datasetId: String? = null
    ): Single<MonitoringTimeSeries>

    fun countConsumedEvents(
        fromMs: Long,
        toMs: Long,
        duration: MonitoringDuration,
        datasetId: String? = null,
        restrictTo: ConsumptionType? = null
    ): Single<List<MonitoringTimeSeries>>

    fun countActiveDataStreams(
        fromMs: Long,
        toMs: Long,
        duration: MonitoringDuration,
        datasetId: String? = null
    ): Single<MonitoringTimeSeries>

}

fun sumMonitoringTimeSeries(series: List<MonitoringTimeSeries>): MonitoringTimeSeries {
    return if (series.isEmpty()) MonitoringTimeSeries(mapOf(), listOf()) else series.reduce(::sumMonitoringTimeSeries)
}

fun sumMonitoringTimeSeries(series1: MonitoringTimeSeries, series2: MonitoringTimeSeries): MonitoringTimeSeries {
    return MonitoringTimeSeries(mapOf(), (0 until max(series1.values.size, series2.values.size))
        .map { index ->
            val record1 = series1.values.getOrNull(index)
            val record2 = series2.values.getOrNull(index)
            TimedFloat(
                record1?.timestamp ?: (record2?.timestamp) ?: 0L,
                (record1?.value ?: 0.0f) + (record2?.value ?: 0.0f)
            )
        })
}
