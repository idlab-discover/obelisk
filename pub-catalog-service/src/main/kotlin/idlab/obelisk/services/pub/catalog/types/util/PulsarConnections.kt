package idlab.obelisk.services.pub.catalog.types.util

import idlab.obelisk.definitions.control.ControlChannels
import idlab.obelisk.definitions.control.DataStreamEvent
import idlab.obelisk.definitions.control.ExportEvent
import idlab.obelisk.definitions.framework.OblxConfig
import org.apache.pulsar.client.api.PulsarClient
import org.apache.pulsar.client.api.Schema
import javax.inject.Inject
import javax.inject.Singleton

// TODO: Better abstraction: encapsulate functionality instead of exposing pulsar clients
@Singleton
class PulsarConnections @Inject constructor(pulsarClient: PulsarClient, config: OblxConfig) {

    val exportTriggerProducer =
        pulsarClient.newProducer(Schema.JSON(ExportEvent::class.java))
            .producerName(config.hostname())
            .topic(ControlChannels.EXPORT_EVENT_TOPIC)
            .blockIfQueueFull(true)
            .create()

}