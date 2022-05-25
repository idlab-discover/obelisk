package idlab.obelisk.pulsar.utils

import idlab.obelisk.definitions.framework.OblxConfig
import org.apache.pulsar.client.admin.PulsarAdmin

internal fun tryInitLocalPulsar(config: OblxConfig) {
    try {
        val pulsarAdmin = PulsarAdmin.builder().serviceHttpUrl(config.pulsarAdminApiUri).build()
        pulsarAdmin.namespaces().createNamespace(config.pulsarMetricEventsTopic.substringBeforeLast("/"))
        pulsarAdmin.namespaces().createNamespace(config.pulsarDatasetTopicsPrefix.substringBeforeLast("/"))
    } catch (err: Throwable) {
        //ignore
    }
}
