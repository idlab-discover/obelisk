package idlab.obelisk.services.pub.monitor

import idlab.obelisk.client.OblxClient
import idlab.obelisk.client.OblxClientOptions
import idlab.obelisk.definitions.framework.OblxConfig
import idlab.obelisk.definitions.framework.OblxModule
import io.vertx.reactivex.core.Vertx
import org.codejargon.feather.Provides
import java.net.URI
import javax.inject.Singleton

class MonitoringModule : OblxModule {

    @Singleton
    @Provides
    fun oblxClient(vertx: Vertx, config: OblxConfig): OblxClient {
        val rootUrl = URI(config.authPublicUri)
        val apiUrl = config.getString(ENV_API_BASE_URL, DEFAULT_API_BASE_URL).removeSuffix("/")
        return OblxClient.create(
            vertx,
            OblxClientOptions(
                apiUrl = apiUrl,
                clientId = "1",
                secret = config.getString(ENV_MONITORING_SECRET, DEFAULT_MONITORING_CLIENT_SECRET),
                virtualHost = rootUrl.host
            )
        )
    }

}