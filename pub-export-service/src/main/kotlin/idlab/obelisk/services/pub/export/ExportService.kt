package idlab.obelisk.services.pub.export

import idlab.obelisk.definitions.Eq
import idlab.obelisk.definitions.Field
import idlab.obelisk.definitions.catalog.*
import idlab.obelisk.definitions.catalog.codegen.DataExportField
import idlab.obelisk.definitions.catalog.codegen.DataExportUpdate
import idlab.obelisk.definitions.control.ControlChannels
import idlab.obelisk.definitions.control.ExportEvent
import idlab.obelisk.definitions.framework.OblxConfig
import idlab.obelisk.definitions.framework.OblxService
import idlab.obelisk.definitions.messaging.MessageBroker
import idlab.obelisk.definitions.messaging.MessageProducer
import idlab.obelisk.plugins.accessmanager.basic.BasicAccessManagerModule
import idlab.obelisk.plugins.datastore.clickhouse.ClickhouseDataStoreModule
import idlab.obelisk.plugins.messagebroker.pulsar.PulsarMessageBrokerModule
import idlab.obelisk.plugins.metastore.mongo.MongoDBMetaStoreModule
import idlab.obelisk.utils.service.OblxBaseModule
import idlab.obelisk.utils.service.OblxLauncher
import idlab.obelisk.utils.service.http.AuthorizationException
import idlab.obelisk.utils.service.http.writeHttpError
import idlab.obelisk.utils.service.utils.unpage
import io.reactivex.Completable
import io.reactivex.rxkotlin.subscribeBy
import io.vertx.reactivex.core.http.HttpHeaders
import io.vertx.reactivex.ext.web.Router
import kotlinx.coroutines.rx2.await
import kotlinx.coroutines.rx2.rxCompletable
import javax.inject.Inject
import javax.inject.Singleton

const val EXPORT_ID_PARAM = "exportId"
const val ENV_EXPORTS_DIR = "EXPORTS_DIR"
const val DEFAULT_EXPORTS_DIR = "./exports"
const val DEFAULT_EXPORTS_ENDPOINT = "/data/exports"

fun main(args: Array<String>) {
    OblxLauncher.with(
        OblxBaseModule(),
        PulsarMessageBrokerModule(),
        MongoDBMetaStoreModule(),
        BasicAccessManagerModule(),
        ClickhouseDataStoreModule()
    ).bootstrap(ExportService::class.java)
}

@Singleton
class ExportService @Inject constructor(
    private val router: Router,
    private val config: OblxConfig,
    private val metaStore: MetaStore,
    private val messageBroker: MessageBroker,
    private val accessManager: AccessManager,
    private val exportRunner: ExportRunner
) : OblxService {

    private val basePath = config.getString(OblxConfig.HTTP_BASE_PATH_PROP, DEFAULT_EXPORTS_ENDPOINT);
    private val exportDir = config.getString(ENV_EXPORTS_DIR, DEFAULT_EXPORTS_DIR).trimEnd('/')
    private lateinit var jobTriggerProducer: MessageProducer<ExportEvent>

    override fun start(): Completable = rxCompletable {
        // Init the job-trigger producer
        jobTriggerProducer = messageBroker.createProducer(
            topicName = ControlChannels.EXPORT_EVENT_TOPIC,
            senderName = "${config.hostname()}_exporter",
            contentType = ExportEvent::class
        )

        // Reschedule jobs that were busy
        rescheduleActiveJobs()

        // Start the export runner associated with this service instance
        exportRunner.start(jobTriggerProducer)

        // Register the download export endpoint
        router.get("$basePath/:$EXPORT_ID_PARAM").handler { ctx ->
            accessManager.getToken(ctx.request())
                .flatMapCompletable { token ->
                    metaStore.getDataExport(ctx.pathParam(EXPORT_ID_PARAM)).flatMapCompletable { export ->
                        if (hasAccess(token, export)) {
                            val filename = export.name!!.replace(" ", "_") + ".zip"
                            // Continue with transferring the export file
                            ctx.response()
                                .putHeader(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"${filename}\"")
                                .rxSendFile("$exportDir/${export.id}.zip")
                        } else {
                            Completable.error { AuthorizationException("No permission to access export ${export.id}") }
                        }
                    }
                }
                .subscribeBy(onError = writeHttpError(ctx))
        }
    }

    private fun hasAccess(token: Token, export: DataExport): Boolean {
        val clientCondition = token.client == null || (token.client!!.scope.contains(Permission.READ))
        return (
                token.user.platformManager
                        || token.user.id == export.userId
                        || (export.teamId != null && token.user.teamMemberships.any { it.teamId == export.teamId })
                ) && clientCondition
    }

    /**
     * Reschedules jobs after a service restart
     */
    private suspend fun rescheduleActiveJobs() {
        // Find all exports in state GENERATING, these are the jobs that were running when the system went down.
        unpage { cursor ->
            metaStore.queryDataExports(
                filter = Eq(Field(DataExportField.STATUS, DataExportStatusFields.STATUS), ExportStatus.QUEUING),
                cursor = cursor
            )
        }.toList().await().forEach { activeJob ->
            val exportId = activeJob.id!!
            // Reset state to queuing
            metaStore.updateDataExport(
                exportId,
                DataExportUpdate(status = activeJob.status.copy(status = ExportStatus.QUEUING))
            ).await()
            // Send trigger
            jobTriggerProducer.send(ExportEvent(exportId))
        }
    }

}
