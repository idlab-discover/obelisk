package idlab.obelisk.plugins.accessmanager.basic

import idlab.obelisk.definitions.catalog.AccessManager
import idlab.obelisk.definitions.framework.OblxConfig
import idlab.obelisk.definitions.framework.OblxModule
import idlab.obelisk.plugins.accessmanager.basic.store.RedisAuthStore
import idlab.obelisk.plugins.accessmanager.basic.store.TokenModifyStore
import org.codejargon.feather.Provides
import org.redisson.Redisson
import org.redisson.api.RedissonClient
import javax.inject.Singleton

class BasicAccessManagerModule: OblxModule {

    @Provides
    fun defaultAccessManager(accessManager: BasicAccessManager): AccessManager {
        return accessManager
    }

    @Provides
    @Singleton
    fun tokenModifyStore(redisAuthStore: RedisAuthStore): TokenModifyStore {
        return redisAuthStore
    }


}