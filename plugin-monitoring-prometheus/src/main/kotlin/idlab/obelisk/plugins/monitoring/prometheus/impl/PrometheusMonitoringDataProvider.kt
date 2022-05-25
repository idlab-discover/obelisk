package idlab.obelisk.plugins.monitoring.prometheus.impl

import idlab.obelisk.definitions.framework.*
import io.reactivex.Single
import io.vertx.core.json.JsonObject
import io.vertx.reactivex.core.Vertx
import io.vertx.reactivex.ext.web.client.WebClient
import mu.KotlinLogging
import java.util.concurrent.TimeUnit

class PrometheusMonitoringDataProvider(vertx: Vertx, config: OblxConfig) : MonitoringDataProvider {

    private val logger = KotlinLogging.logger { }
    private val webClient = WebClient.create(vertx)
    private val prometheusBaseUri = config.prometheusUri.trimEnd('/')

    override fun countIngestRequests(
        fromMs: Long,
        toMs: Long,
        duration: MonitoringDuration,
        datasetId: String?
    ): Single<MonitoringTimeSeries> {
        val q = "sum(rate(oblx_ingest_request_size_events_count${filter(datasetId = datasetId)}[$duration]))"
        return executeRangeQuery(q, fromMs, toMs, duration).map {
            it.firstOrNull() ?: MonitoringTimeSeries(
                emptyMap(),
                emptyList()
            )
        }
    }

    override fun countQueryRequests(
        fromMs: Long,
        toMs: Long,
        duration: MonitoringDuration,
        datasetId: String?,
        restrictTo: QueryType?
    ): Single<List<MonitoringTimeSeries>> {
        val q = if (datasetId != null) {
            "sum by (queryType) (rate(oblx_query_requests_seconds_count${
                filter(
                    datasetId = datasetId,
                    queryType = restrictTo
                )
            }[$duration]))"
        } else {
            "sum by (queryType) (rate(oblx_query_global_requests_seconds_count${filter(queryType = restrictTo)}[$duration]))"
        }
        return executeRangeQuery(q, fromMs, toMs, duration) { labels ->
            labels["queryType"]?.let { mapOf("queryType" to it) } ?: emptyMap()
        }
    }

    override fun countIngestedEvents(
        fromMs: Long,
        toMs: Long,
        duration: MonitoringDuration,
        datasetId: String?
    ): Single<MonitoringTimeSeries> {
        val q = "sum(rate(oblx_ingest_received_events_total${filter(datasetId = datasetId)}[$duration]))"
        return executeRangeQuery(q, fromMs, toMs, duration).map {
            it.firstOrNull() ?: MonitoringTimeSeries(
                emptyMap(),
                emptyList()
            )
        }
    }

    override fun countConsumedEvents(
        fromMs: Long,
        toMs: Long,
        duration: MonitoringDuration,
        datasetId: String?,
        restrictTo: ConsumptionType?
    ): Single<List<MonitoringTimeSeries>> {
        val consumedViaQueryQ = if (datasetId != null) {
            "rate(oblx_query_response_size_sum${filter(datasetId)}[$duration])/rate(oblx_query_response_size_count${
                filter(
                    datasetId
                )
            }[$duration])"
        } else {
            "rate(oblx_query_global_response_size_sum[$duration])/rate(oblx_query_global_response_size_count[$duration])"
        }

        val consumedViaSSEQ = "rate(oblx_sse_events_streamed_total${filter(datasetId)}[$duration])"

        val q = "sum by (service) (${
            when (restrictTo) {
                ConsumptionType.QUERIES -> consumedViaQueryQ
                ConsumptionType.STREAMING -> consumedViaSSEQ
                else -> "$consumedViaQueryQ or $consumedViaSSEQ"
            }
        })"
        return executeRangeQuery(q, fromMs, toMs, duration) { labels ->
            labels["service"]?.let {
                mapOf(
                    "consumptionType" to when (it) {
                        "oblx-pub-query" -> ConsumptionType.QUERIES.toString().lowercase()
                        "oblx-pub-streaming" -> ConsumptionType.STREAMING.toString().lowercase()
                        else -> ""
                    }
                )
            } ?: emptyMap()
        }
    }

    override fun countActiveDataStreams(
        fromMs: Long,
        toMs: Long,
        duration: MonitoringDuration,
        datasetId: String?
    ): Single<MonitoringTimeSeries> {
        val q = "sum by (service) (${
            if (datasetId != null) {
                "oblx_sse_active_streams${filter(datasetId)}"
            } else {
                "oblx_sse_global_active_streams"
            }
        })"
        return executeRangeQuery(q, fromMs, toMs, duration).map {
            it.firstOrNull() ?: MonitoringTimeSeries(
                emptyMap(),
                emptyList()
            )
        }
    }

    private fun executeRangeQuery(
        promExprQ: String,
        fromMs: Long,
        toMs: Long,
        duration: MonitoringDuration,
        metricDimensionsTransformer: (Map<String, String>) -> Map<String, String> = { emptyMap() }
    ): Single<List<MonitoringTimeSeries>> {
        logger.debug { "Executing Prometheus query '$promExprQ' on $prometheusBaseUri" }
        return if (prometheusBaseUri.isNotEmpty()) {
            webClient.getAbs("$prometheusBaseUri/api/v1/query_range")
                .addQueryParam("query", promExprQ)
                .addQueryParam("start", TimeUnit.SECONDS.convert(fromMs, TimeUnit.MILLISECONDS).toString())
                .addQueryParam("end", TimeUnit.SECONDS.convert(toMs, TimeUnit.MILLISECONDS).toString())
                .addQueryParam("step", duration.toString())
                .rxSend()
                .flatMap { resp ->
                    if (resp.statusCode() == 200) {
                        Single.just(resp.bodyAsJsonObject().getJsonObject("data").getJsonArray("result")
                            .map { it as JsonObject }
                            .map {
                                val result =
                                    it.mapTo(PrometheusTimeseries::class.java).convert(metricDimensionsTransformer)
                                result
                            })
                    } else {
                        Single.error(RuntimeException("Prometheus request resulted in error (code ${resp.statusCode()}): ${resp.bodyAsString()}"))
                    }
                }
                .doOnError { logger.warn(it) { "Error while executing Prometheus query: $promExprQ" } }
        } else {
            Single.just(listOf(MonitoringTimeSeries(values = listOf(), labels = emptyMap())))
        }
    }

    private fun filter(
        datasetId: String? = null,
        queryType: QueryType? = null
    ): String {
        return listOfNotNull(
            datasetId?.let { "datasetId=\"$it\"" },
            queryType?.let { "queryType=\"${it.toString().lowercase()}\"" }
        ).joinToString(separator = ", ", prefix = "{", postfix = "}")
    }
}

data class PrometheusTimeseries(
    val metric: Map<String, String>,
    val values: List<List<Any?>>
) {
    fun convert(metricDimensionsTransformer: (Map<String, String>) -> Map<String, String> = { emptyMap() }): MonitoringTimeSeries {
        return MonitoringTimeSeries(metricDimensionsTransformer.invoke(metric), values.map { record ->
            val valStr = record[1] as String
            val value = if (valStr.lowercase() == "nan") 0.0f else valStr.toFloat()
            TimedFloat(((record[0] as Int).toLong() * 1000L), value)
        })
    }
}