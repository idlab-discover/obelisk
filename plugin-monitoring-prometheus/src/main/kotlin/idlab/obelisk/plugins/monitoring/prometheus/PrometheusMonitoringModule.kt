package idlab.obelisk.plugins.monitoring.prometheus

import idlab.obelisk.definitions.framework.MonitoringDataProvider
import idlab.obelisk.definitions.framework.OblxConfig
import idlab.obelisk.definitions.framework.OblxModule
import idlab.obelisk.plugins.monitoring.prometheus.impl.PrometheusMonitoringDataProvider
import io.vertx.reactivex.core.Vertx
import org.codejargon.feather.Provides
import javax.inject.Singleton

class PrometheusMonitoringModule : OblxModule {

    @Singleton
    @Provides
    fun monitoringDataProvider(vertx: Vertx, config: OblxConfig): MonitoringDataProvider {
        return PrometheusMonitoringDataProvider(vertx, config)
    }

}