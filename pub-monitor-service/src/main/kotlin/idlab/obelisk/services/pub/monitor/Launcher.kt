package idlab.obelisk.services.pub.monitor

import idlab.obelisk.definitions.*
import idlab.obelisk.definitions.catalog.*
import idlab.obelisk.definitions.data.DataStore
import idlab.obelisk.definitions.data.EventsQuery
import idlab.obelisk.definitions.framework.OblxConfig
import idlab.obelisk.definitions.framework.OblxService
import idlab.obelisk.plugins.accessmanager.basic.utils.SecureSecret
import idlab.obelisk.plugins.datastore.clickhouse.ClickhouseDataStoreModule
import idlab.obelisk.plugins.metastore.mongo.MongoDBMetaStoreModule
import idlab.obelisk.services.pub.monitor.agents.*
import idlab.obelisk.utils.service.OblxBaseModule
import idlab.obelisk.utils.service.OblxLauncher
import idlab.obelisk.utils.service.http.writeHttpError
import idlab.obelisk.utils.service.reactive.flatMap
import io.reactivex.Completable
import io.reactivex.Flowable
import io.reactivex.rxkotlin.subscribeBy
import io.vertx.reactivex.ext.web.Router
import mu.KotlinLogging
import org.redisson.api.RedissonClient
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.system.exitProcess

const val ENV_MONITORING_SECRET = "MONITORING_CLIENT_SECRET"
const val ENV_MONITORING_PERIOD_MS = "MONITORING_PERIOD_MS"
const val ENV_MONITORING_STATUS_WINDOW_MINUTES = "MONITORING_STATUS_WINDOW_MINUTES"
const val ENV_MONITORING_INGEST_CHECK_INITIAL_DELAY_MS = "MONITORING_INGEST_CHECK_INITIAL_DELAY_MS"
const val ENV_MONITORING_INGEST_CHECK_MAX_RETRIES = "MONITORING_INGEST_CHECK_MAX_RETRIES"
const val ENV_API_BASE_URL = "API_BASE_URL"
const val DEFAULT_MONITORING_PERIOD_MS = 15L * 1000
const val DEFAULT_MONITORING_CLIENT_SECRET = "43624f39-e175-475d-889d-027eacec44c6"
const val DEFAULT_MONITORING_STATUS_WINDOW_MINUTES = 30L
const val DEFAULT_MONITORING_INGEST_CHECK_INITIAL_DELAY_MS = 500L
const val DEFAULT_MONITORING_INGEST_CHECK_MAX_RETRIES = 3
const val DEFAULT_API_BASE_URL = "http://localhost:8080/"
const val HTTP_BASE_PATH = "/monitor"

const val datasetId = "int-oblx-monitor"
const val clientId = "int-oblx-monitor"

val SERVICE_STARTUP_TIME_MS = System.currentTimeMillis()

val metricName = MetricName("pulse::string")

fun main(args: Array<String>) {
    OblxLauncher.with(OblxBaseModule(), MongoDBMetaStoreModule(), ClickhouseDataStoreModule(), MonitoringModule())
        .bootstrap(MonitorService::class.java)
}

@Singleton
class MonitorService @Inject constructor(
    private val config: OblxConfig,
    private val router: Router,
    private val metaStore: MetaStore,
    private val dataStore: DataStore,
    private val redis: RedissonClient,
    private val ingestAgent: IngestAgent,
    private val metaReadAgent: MetaReadAgent,
    private val metaWriteAgent: MetaWriteAgent,
    private val queryAgent: QueryAgent,
    private val streamingAgent: StreamingAgent
) : OblxService {
    private val logger = KotlinLogging.logger { }

    override fun start(): Completable {
        val basePath = config.getString(OblxConfig.HTTP_BASE_PATH_PROP, HTTP_BASE_PATH)
        val hq = MonitoringHQ(redis, config, ingestAgent, metaReadAgent, metaWriteAgent, queryAgent, streamingAgent)

        val initPhase = initPrerequisites().flatMap { clearOldData() }.cache()

        initPhase.flatMap {
            Flowable.interval(
                0,
                config.getLong(ENV_MONITORING_PERIOD_MS, DEFAULT_MONITORING_PERIOD_MS),
                TimeUnit.MILLISECONDS
            )
                .onBackpressureDrop()
                .concatMapCompletable {
                    hq.cycle()
                }
        }
            .subscribeBy(onError = { err ->
                logger.error(err) { "Error while triggering a monitoring cycle!" }
                exitProcess(1)
            })

        router.get(basePath).handler { ctx -> ctx.json(hq.report()) }
        router.get("$basePath/status").handler { ctx ->
            hq.status(ctx.request().getParam("maxRecords")?.toInt()).subscribeBy(
                onSuccess = { ctx.json(it) },
                onError = writeHttpError(ctx)
            )
        }
        router.get("$basePath/status/:componentId").handler { ctx ->
            hq.statusById(
                componentId = ctx.pathParam("componentId"),
                maxRecords = ctx.request().getParam("maxRecords")?.toInt()
            ).subscribeBy(
                onSuccess = { ctx.json(it) },
                onError = writeHttpError(ctx)
            )
        }

        return initPhase
    }

    // When the monitor service boots, we can check to delete old data produced by the monitor (to save disk space)
    private fun clearOldData(): Completable {
        // Delete any records older than today (produced by the monitor)
        return dataStore.delete(
            EventsQuery(
                dataRange = DataRange.fromDatasetId(datasetId),
                to = Instant.now().minus(1, ChronoUnit.DAYS).toEpochMilli()
            )
        )
    }

    private fun initPrerequisites(): Completable {
        // Client for monitoring
        return metaStore.createClient(
            Client(
                id = "1",
                name = clientId,
                userId = "0",
                confidential = true,
                onBehalfOfUser = false,
                secretHash = SecureSecret.hash(
                    config.getString(
                        ENV_MONITORING_SECRET,
                        DEFAULT_MONITORING_CLIENT_SECRET
                    )
                ),
                restrictions = listOf(ClientRestriction(datasetId, Permission.readAndWrite())),
                scope = setOf(Permission.READ, Permission.WRITE)
            )
        )
            .ignoreElement()
            .onErrorComplete { it is AlreadyExistsException } // Ignore AlreadyExistException
            .flatMap {
                // Setting up monitor dataset
                metaStore.createDataset(Dataset(id = datasetId, name = datasetId, published = false))
                    .ignoreElement()
                    .onErrorComplete { it is AlreadyExistsException } // Ignore AlreadyExistException

            }
            .flatMap {
                // Setting up Datastream that will be used (for monitoring SSE)
                metaStore.createDataStream(
                    DataStream(
                        id = datasetId,
                        userId = "0",
                        name = datasetId,
                        dataRange = DataRange(datasets = listOf(datasetId)),
                        timestampPrecision = TimestampPrecision.milliseconds,
                        fields = EventField.values().toList(),
                        clientConnected = false
                    )
                )
                    .ignoreElement()
                    .onErrorComplete { it is AlreadyExistsException } // Ignore AlreadyExistException
            }
    }

}
