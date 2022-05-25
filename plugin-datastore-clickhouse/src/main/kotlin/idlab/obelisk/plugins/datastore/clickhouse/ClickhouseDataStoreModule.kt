package idlab.obelisk.plugins.datastore.clickhouse

import idlab.obelisk.definitions.data.DataStore
import idlab.obelisk.definitions.framework.OblxConfig
import idlab.obelisk.definitions.framework.OblxModule
import idlab.obelisk.plugins.datastore.clickhouse.impl.CHDataStore
import io.vertx.kotlin.core.json.json
import io.vertx.kotlin.core.json.obj
import io.vertx.reactivex.core.Vertx
import io.vertx.reactivex.ext.jdbc.JDBCClient
import org.codejargon.feather.Provides
import com.clickhouse.jdbc.ClickHouseDriver
import io.vertx.reactivex.jdbcclient.JDBCPool
import javax.inject.Singleton

class ClickhouseDataStoreModule : OblxModule {

    @Provides
    @Singleton
    fun dataStore(vertx: Vertx, config: OblxConfig): DataStore {
        val chConfig = json {
            obj(
                "driver_class" to ClickHouseDriver::class.qualifiedName,
                "url" to config.chConnectionUri
            )
        }
        return CHDataStore(JDBCPool.pool(vertx, chConfig), config)
    }

}
