package idlab.obelisk.services.pub.monitor.agents

import idlab.obelisk.client.OblxClient
import idlab.obelisk.definitions.DataRange
import idlab.obelisk.definitions.data.EventsQuery
import idlab.obelisk.services.pub.monitor.datasetId
import idlab.obelisk.services.pub.monitor.metricName
import io.reactivex.Single
import io.vertx.core.json.JsonObject
import java.time.Instant
import java.time.temporal.ChronoUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class QueryAgent @Inject constructor(private val oblxClient: OblxClient) : AbstractApiAgent() {

    override fun id(): String {
        return "query-service"
    }

    override fun performCall(): Single<CallResult> {
        val now = Instant.now()
        // Query last hour of data
        val query = EventsQuery(
                dataRange = DataRange(datasets = listOf(datasetId), metrics = listOf(metricName)),
                from = now.minus(1, ChronoUnit.HOURS).toEpochMilli(),
                to = now.toEpochMilli()
        )
        return oblxClient.queryEvents(query)
                .map { result ->
                    CallResult(state = result.items.count())
                }
    }

    override fun validate(callState: Any?, report: JsonObject): Single<JsonObject> {
        report.put("fetchedRecords#", callState)
        return Single.just(report)
    }

}