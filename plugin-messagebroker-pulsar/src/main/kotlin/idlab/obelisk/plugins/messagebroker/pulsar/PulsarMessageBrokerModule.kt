package idlab.obelisk.plugins.messagebroker.pulsar

import idlab.obelisk.definitions.framework.OblxModule
import idlab.obelisk.definitions.messaging.MessageBroker
import org.codejargon.feather.Provides
import javax.inject.Singleton

class PulsarMessageBrokerModule : OblxModule {

    @Provides
    @Singleton
    fun messageBroker(pulsarMessageBroker: PulsarMessageBroker): MessageBroker {
        return pulsarMessageBroker
    }

}
