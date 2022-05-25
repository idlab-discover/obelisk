package idlab.obelisk.pulsar.utils

import idlab.obelisk.definitions.framework.OblxConfig
import idlab.obelisk.definitions.framework.OblxModule
import org.apache.pulsar.client.api.PulsarClient
import org.codejargon.feather.Provides
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

/**
 * @param initLocalPulsar Set to true when running a local setup (e.g. developer monolith, unit tests).
 */
class PulsarModule(private val initLocalPulsar: Boolean = false) : OblxModule {
    @Provides
    @Singleton
    fun pulsarClient(config: OblxConfig): PulsarClient {
        if (initLocalPulsar) {
            tryInitLocalPulsar(config)
        }
        return PulsarClient.builder().serviceUrl(config.pulsarServiceUrl).listenerThreads(config.pulsarListenerThreads)
            .operationTimeout(2, TimeUnit.SECONDS).build()
    }
}
