package idlab.obelisk.plugins.messagebroker.pulsar

import idlab.obelisk.definitions.framework.OblxConfig
import idlab.obelisk.definitions.framework.OblxModule
import idlab.obelisk.definitions.messaging.MessageBroker
import org.apache.pulsar.client.admin.PulsarAdmin
import org.apache.pulsar.client.api.PulsarClient
import org.codejargon.feather.Provides
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

/**
 * @param initLocalPulsar Set to true when running a local setup (e.g. developer monolith, unit tests).
 */
class PulsarMessageBrokerModule(private val initLocalPulsar: Boolean = false) : OblxModule {

    @Provides
    @Singleton
    fun pulsarClient(config: OblxConfig): PulsarClient {
        if (initLocalPulsar) {
            tryInitLocalPulsar(config)
        }
        return PulsarClient.builder().serviceUrl(config.pulsarServiceUrl).listenerThreads(config.pulsarListenerThreads)
            .operationTimeout(2, TimeUnit.SECONDS).build()
    }

    @Provides
    @Singleton
    fun messageBroker(pulsarMessageBroker: PulsarMessageBroker): MessageBroker {
        return pulsarMessageBroker
    }

}

internal fun tryInitLocalPulsar(config: OblxConfig) {
    try {
        val pulsarAdmin = PulsarAdmin.builder().serviceHttpUrl(config.pulsarAdminApiUri).build()
        pulsarAdmin.namespaces().createNamespace(config.pulsarMetricEventsTopic.substringBeforeLast("/"))
        pulsarAdmin.namespaces().createNamespace(config.pulsarDatasetTopicsPrefix.substringBeforeLast("/"))
    } catch (err: Throwable) {
        //ignore
    }
}
