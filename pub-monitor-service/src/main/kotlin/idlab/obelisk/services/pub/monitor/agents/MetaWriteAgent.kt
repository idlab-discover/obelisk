package idlab.obelisk.services.pub.monitor.agents

import idlab.obelisk.client.OblxClient
import idlab.obelisk.services.pub.monitor.datasetId
import io.reactivex.Single
import io.vertx.core.json.JsonObject
import javax.inject.Inject
import javax.inject.Singleton

private const val CONTROL_PROPERTY = "monitoringUpdate"

@Singleton
class MetaWriteAgent @Inject constructor(private val oblxClient: OblxClient) : AbstractApiAgent() {

    override fun id(): String {
        return "catalog-service-write"
    }

    override fun performCall(): Single<CallResult> {
        val touchedTimestamp = System.currentTimeMillis()
        val graphqlMutation = """
            mutation {
              onDataset(id: "$datasetId") {
                setProperties(properties: {
                  $CONTROL_PROPERTY: ${touchedTimestamp}
                }) {
                  item {
                    properties
                  }
                }
              }
            }
        """.trimIndent()
        return oblxClient.queryCatalog(graphqlMutation).map { result ->
            val returnedTs = result.getJsonObject("data")?.getJsonObject("onDataset")
                    ?.getJsonObject("setProperties")?.getJsonObject("item")?.getJsonObject("properties")
                    ?.getLong(CONTROL_PROPERTY)
            CallResult(success = returnedTs == touchedTimestamp)
        }
    }

    override fun validate(callState: Any?, report: JsonObject): Single<JsonObject> {
        return Single.just(report)
    }
}