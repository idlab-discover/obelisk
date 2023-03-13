package idlab.obelisk.monolith

import idlab.obelisk.plugins.accessmanager.basic.BasicAccessManagerModule
import idlab.obelisk.plugins.datastore.clickhouse.ClickhouseDataStoreModule
import idlab.obelisk.plugins.metastore.mongo.MongoDBMetaStoreModule
import idlab.obelisk.plugins.monitoring.prometheus.PrometheusMonitoringModule
import idlab.obelisk.plugins.ratelimiter.gubernator.GubernatorRateLimiterModule
import idlab.obelisk.pulsar.utils.PulsarModule
import idlab.obelisk.service.internal.sink.SinkService
import idlab.obelisk.services.internal.statscollector.StatsCollectorService
import idlab.obelisk.services.internal.streamer.DatasetStreamerService
import idlab.obelisk.services.pub.auth.AuthService
import idlab.obelisk.services.pub.auth.AuthServiceModule
import idlab.obelisk.services.pub.catalog.CatalogService
import idlab.obelisk.services.pub.export.ExportService
import idlab.obelisk.services.pub.ingest.IngestService
import idlab.obelisk.services.pub.issues.IssueService
import idlab.obelisk.services.pub.monitor.MonitorService
import idlab.obelisk.services.pub.monitor.MonitoringModule
import idlab.obelisk.services.pub.query.QueryService
import idlab.obelisk.services.pub.streaming.StreamingService
import idlab.obelisk.utils.service.OblxBaseModule
import idlab.obelisk.utils.service.OblxLauncher
import io.reactivex.rxkotlin.subscribeBy
import mu.KotlinLogging

// Add environment variable 'POPULATE_DEMO_DATA=true' to start the monolith with some example data and meta-data
const val POPULATE_DEMO_DATA = "POPULATE_DEMO_DATA"
private val logger = KotlinLogging.logger { }

fun main(args: Array<String>) {
    val services = mutableListOf(
        AuthService::class.java,
        IngestService::class.java,
        DatasetStreamerService::class.java,
        QueryService::class.java,
        CatalogService::class.java,
        SinkService::class.java,
        StreamingService::class.java,
        MonitorService::class.java,
        ExportService::class.java,
        IssueService::class.java,
        StatsCollectorService::class.java
    )
    System.getenv(POPULATE_DEMO_DATA)?.let { if ("true" == it) services.add(DemoDataGenerator::class.java) }

    val launcher = OblxLauncher.with(
        OblxBaseModule(),
        AuthServiceModule(),
        BasicAccessManagerModule(),
        PulsarModule(initLocalPulsar = true),
        ClickhouseDataStoreModule(),
        MongoDBMetaStoreModule(),
        MonitoringModule(),
        GubernatorRateLimiterModule(),
        PrometheusMonitoringModule()
    )
    launcher
        .rxBootstrap(*services.toTypedArray())
        .subscribeBy(
            onComplete = { logger.info { "All services started successfully!" } },
            onError = { logger.error(it) { "Error while launching services!" } }
        )
}
