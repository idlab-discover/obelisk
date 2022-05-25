package idlab.obelisk.services.pub.monitor.agents

import idlab.obelisk.client.OblxClient
import idlab.obelisk.definitions.framework.OblxConfig
import io.reactivex.Single
import io.vertx.core.json.JsonObject
import javax.inject.Inject
import javax.inject.Singleton

private const val adminMail = "admin@obelisk.ilabt.imec.be"

@Singleton
class MetaReadAgent @Inject constructor(private val oblxClient: OblxClient) : AbstractApiAgent() {

    override fun id(): String {
        return "catalog-service-read"
    }

    override fun performCall(): Single<CallResult> {
        val graphQLQuery = """
            {
              me {
                email
              }
            }
        """.trimIndent()
        return oblxClient.queryCatalog(graphQLQuery).map { result ->
            val email = result.getJsonObject("data")?.getJsonObject("me")?.getString("email")
            CallResult(success = email == adminMail)
        }
    }

    override fun validate(callState: Any?, report: JsonObject): Single<JsonObject> {
        return Single.just(report)
    }
}