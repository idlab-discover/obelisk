package idlab.obelisk.services.pub.ngsi

import idlab.obelisk.definitions.data.DataStore
import idlab.obelisk.definitions.framework.OblxConfig
import idlab.obelisk.definitions.framework.OblxModule
import idlab.obelisk.definitions.ratelimiting.RateLimiter
import idlab.obelisk.services.pub.ngsi.impl.state.EntityContext
import idlab.obelisk.services.pub.ngsi.impl.state.NgsiStore
import idlab.obelisk.services.pub.ngsi.impl.state.NgsiStoreImpl
import idlab.obelisk.services.pub.ngsi.impl.state.SimpleEntityContext
import io.vertx.reactivex.core.Vertx
import org.apache.pulsar.client.api.PulsarClient
import org.codejargon.feather.Provides
import javax.inject.Singleton

class NgsiModule : OblxModule {

    @Singleton
    @Provides
    fun entityContext(dataStore: DataStore): EntityContext {
        return SimpleEntityContext(dataStore)
    }

    @Singleton
    @Provides
    fun ngsiStore(
        vertx: Vertx,
        config: OblxConfig,
        entityContext: EntityContext,
        dataStore: DataStore,
        pulsarClient: PulsarClient,
        rateLimiter: RateLimiter
    ): NgsiStore {
        return NgsiStoreImpl(vertx, config, entityContext, dataStore, rateLimiter, pulsarClient)
    }

}