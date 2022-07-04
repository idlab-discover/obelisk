package idlab.obelisk.services.pub.export

import com.github.davidmoten.rx2.RetryWhen
import idlab.obelisk.definitions.EventField
import idlab.obelisk.definitions.Field
import idlab.obelisk.definitions.In
import idlab.obelisk.definitions.catalog.*
import idlab.obelisk.definitions.catalog.codegen.DataExportField
import idlab.obelisk.definitions.catalog.codegen.DataExportUpdate
import idlab.obelisk.definitions.control.ControlChannels
import idlab.obelisk.definitions.control.ExportEvent
import idlab.obelisk.definitions.data.DataStore
import idlab.obelisk.definitions.data.EventsQuery
import idlab.obelisk.definitions.data.MetricEvent
import idlab.obelisk.definitions.framework.OblxConfig
import idlab.obelisk.definitions.messaging.MessageBroker
import idlab.obelisk.definitions.messaging.MessageProducer
import idlab.obelisk.definitions.messaging.MessagingMode
import idlab.obelisk.utils.service.reactive.flatMap
import idlab.obelisk.utils.service.reactive.flatMapSingle
import idlab.obelisk.utils.service.utils.pageAndProcess
import idlab.obelisk.utils.service.utils.unpage
import io.reactivex.Completable
import io.reactivex.Flowable
import io.reactivex.Single
import io.reactivex.rxkotlin.Singles
import io.reactivex.rxkotlin.subscribeBy
import io.vertx.core.file.OpenOptions
import io.vertx.core.json.Json
import io.vertx.core.json.JsonArray
import io.vertx.core.json.JsonObject
import io.vertx.reactivex.core.Vertx
import io.vertx.reactivex.core.buffer.Buffer
import io.vertx.reactivex.core.file.AsyncFile
import io.vertx.reactivex.core.file.FileProps
import kotlinx.coroutines.rx2.asFlowable
import kotlinx.coroutines.rx2.await
import kotlinx.coroutines.rx2.rxCompletable
import mu.KotlinLogging
import net.lingala.zip4j.ZipFile
import org.apache.pulsar.shade.org.apache.commons.lang.StringEscapeUtils
import java.io.File
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.min
import kotlin.system.exitProcess

private const val ENV_EXPORTS_CHUNK_SIZE = "EXPORTS_CHUNK_SIZE"
private const val ENV_MAX_RECORDS_PER_JOB = "MAX_RECORDS_PER_JOB"
private const val ENV_MAX_JOB_CONCURRENCY = "MAX_JOB_CONCURRENCY"
private const val ENV_MAX_QUERY_RETRIES = "MAX_QUERY_RETRIES"
private const val DEFAULT_EXPORTS_CHUNK_SIZE = 5000
private const val DEFAULT_MAX_RECORDS_PER_JOB = 1000_000
private const val DEFAULT_MAX_JOB_CONCURRENCY = 4
private const val DEFAULT_MAX_QUERY_RETRIES = 3
private const val EXPORT_EVENTS_CONSUMER = "pub-export-service"
private const val DB_READ_CHUNK_SIZE = 25000
private const val CR = 0x0D.toChar()
private const val LF = 0x0A.toChar()

private const val CRLF = "" + CR + LF

@Singleton
class ExportRunner @Inject constructor(
    private val metaStore: MetaStore,
    private val dataStore: DataStore,
    private val messageBroker: MessageBroker,
    private val vertx: Vertx,
    config: OblxConfig
) {

    private val logger = KotlinLogging.logger { }

    private lateinit var jobTriggerProducer: MessageProducer<ExportEvent>
    private val fileSystem = vertx.fileSystem()
    private val exportDir = config.getString(ENV_EXPORTS_DIR, DEFAULT_EXPORTS_DIR).trimEnd('/')
    private val maxRecordsPerJob = config.getInteger(ENV_MAX_RECORDS_PER_JOB, DEFAULT_MAX_RECORDS_PER_JOB)
    private val chunkSize = config.getInteger(ENV_EXPORTS_CHUNK_SIZE, DEFAULT_EXPORTS_CHUNK_SIZE)
    private val concurrency = config.getInteger(ENV_MAX_JOB_CONCURRENCY, DEFAULT_MAX_JOB_CONCURRENCY)
    private val maxQueryRetries = config.getInteger(ENV_MAX_QUERY_RETRIES, DEFAULT_MAX_QUERY_RETRIES)

    suspend fun start(jobTriggerProducer: MessageProducer<ExportEvent>) {
        this.jobTriggerProducer = jobTriggerProducer
        cleanUp().await()

        val jobTriggerConsumer = messageBroker.createConsumer(
            topicName = ControlChannels.EXPORT_EVENT_TOPIC,
            subscriptionName = EXPORT_EVENTS_CONSUMER,
            contentType = ExportEvent::class,
            mode = MessagingMode.SIGNALING
        )

        jobTriggerConsumer.receive().asFlowable()
            .flatMapCompletable({ event ->
                metaStore.getDataExport(event.content.exportId)
                    .flatMapCompletable { export ->
                        when (export.status.status) {
                            ExportStatus.GENERATING, ExportStatus.QUEUING -> runExport(export) // Start or resume the job
                            ExportStatus.COMPLETED, ExportStatus.CANCELLED, ExportStatus.FAILED -> {
                                logger.warn { "Nothing to execute, the job is already completed... (${export.status.status})" }
                                Completable.complete()
                            }
                        }
                    }
                    .doOnError { logger.error(it) { "Something went wrong setting up the job..." } }
            }, false, concurrency)
            .subscribeBy(
                onError = { err ->
                    logger.error(err) { "An unexpected error occurred while executing the export job loop..." }
                    exitProcess(1)
                }
            )
    }

    private fun cleanUp(): Completable {
        // Clean up old files first
        return unpage { cursor ->
            // Find all currently queued and running jobs
            metaStore.queryDataExports(
                filter = In(
                    Field(
                        DataExportField.STATUS,
                        DataExportStatusFields.STATUS
                    ), setOf(ExportStatus.QUEUING, ExportStatus.GENERATING)
                ),
                cursor = cursor
            )
        }
            .toList()
            .flatMapCompletable { activeJobs ->
                val activeJobIds = activeJobs.map { it.id!! }.toSet()
                // Iterate over all files in the export dir
                vertx.fileSystem().rxReadDir(exportDir)
                    .flattenAsFlowable { it }
                    .flatMapCompletable { file ->
                        vertx.fileSystem().rxProps(file).flatMapCompletable { fileProps ->
                            // If the file is a subdir and does not match the id of an active job, we can delete the dir
                            if (fileProps.isDirectory && !activeJobIds.contains(
                                    file.split(File.pathSeparatorChar).last()
                                )
                            ) {
                                vertx.fileSystem().rxDeleteRecursive(file, true)
                                    .onErrorComplete { err ->
                                        logger.warn(err) { "Caught error while trying to clean up the working dir $file" }
                                        true
                                    }
                            } else {
                                Completable.complete()
                            }
                        }
                    }
            }
            .onErrorComplete {
                logger.warn(it) { "Error while trying to clean up exports." }
                true
            }
    }

    private fun runExport(export: DataExport): Completable {
        val jobLimit =
            min(
                maxRecordsPerJob.toLong(),
                export.limit?.let { it - export.status.recordsProcessed } ?: Long.MAX_VALUE)

        val query = EventsQuery(
            dataRange = export.dataRange,
            from = export.from,
            to = export.to,
            timestampPrecision = export.timestampPrecision,
            fields = export.fields,
            filter = export.filter,
            limit = DB_READ_CHUNK_SIZE
        )
        var recordCount = 0 // Amount of records processed in this job
        export.status.status = ExportStatus.GENERATING

        logger.info { "Starting export for job ${export.id}..." }

        // Update new status and initialize the CSV file
        return updateStatus(export).flatMapSingle { initCsv(export) }.flatMapCompletable { csvFile ->
            // Start paging through the results of the query
            var currentCursor: String? = null
            pageAndProcess(
                cursor = export.status.resumeCursor,
                limit = jobLimit.toInt(),
                executor = { cursor ->
                    currentCursor = cursor
                    dataStore.getEvents(query.copy(cursor = cursor)).retryWhen(
                        RetryWhen.exponentialBackoff(200, TimeUnit.MILLISECONDS).maxRetries(maxQueryRetries).build()
                    )
                },
                processor = { result ->
                    Flowable.fromIterable(result.items)
                        .map {
                            recordCount++
                            toCSV(export, it)
                        }
                        .buffer(chunkSize)
                        .concatMapCompletable { chunk -> csvFile.rxWrite(Buffer.buffer(chunk.joinToString(separator = ""))) }
                }
            )
                .flatMap { csvFile.rxClose() }
                .flatMap {
                    if (recordCount < maxRecordsPerJob) {
                        // The job is completed, no more additional records to export
                        Singles.zip(exportSize(export), packageZip(export))
                            .map { (csvSize, zipSize) ->
                                export.apply {
                                    result = DataExportResult(
                                        sizeInBytes = csvSize,
                                        compressedSizeInBytes = zipSize,
                                        completedOn = System.currentTimeMillis()
                                    )
                                }
                            }
                            .flatMapCompletable { finishJob(it, true) }
                    } else {
                        // The job has reached the maximum allowed number of records to process in a single run, it will be re-queued!
                        export.status.status = ExportStatus.QUEUING
                        export.status.recordsProcessed += recordCount
                        export.status.resumeCursor = currentCursor
                        updateStatus(export).flatMap { reschedule(export) }
                    }
                }
        }
            .doOnComplete { logger.info { "Finished execution of export with id ${export.id}..." } }
            .onErrorResumeNext { err ->
                // Report failure
                logger.error(err) { "Failed to execute export with id ${export.id}..." }
                finishJob(export, false)
            }
    }

    /**
     * Finishes the job by saving the finished state to the metastore and also acknowledges the trigger message
     * + tries to clean up the working directory
     */
    private fun finishJob(export: DataExport, success: Boolean): Completable {
        export.status.status = if (success) ExportStatus.COMPLETED else ExportStatus.FAILED
        return updateStatus(export)
            .flatMap { appendResult(export) }
            .flatMap {
                fileSystem.rxDeleteRecursive(export.targetFolder(), true)
                    .onErrorComplete { err ->
                        logger.warn(err) { "Caught error while trying to clean up the working dir for export ${export.id}" }
                        true
                    }
            }
    }

    private fun updateStatus(export: DataExport): Completable {
        return metaStore.updateDataExport(export.id!!, DataExportUpdate(status = export.status))
    }

    private fun appendResult(export: DataExport): Completable {
        return if (export.result != null) metaStore.updateDataExport(
            export.id!!,
            DataExportUpdate(result = export.result)
        ) else Completable.complete()
    }

    private fun reschedule(export: DataExport): Completable = rxCompletable {
        jobTriggerProducer.send(ExportEvent(export.id!!))
    }

    /**
     * Packages the CSV file and reports the size in bytes.
     */
    private fun packageZip(export: DataExport): Single<Long> {
        val zipFilePath = "${export.targetFolder()}.zip"
        return vertx
            .rxExecuteBlocking<Unit> { future ->
                try {
                    val zipFile = ZipFile(zipFilePath)
                    zipFile.addFile(File(export.targetFile()))
                    future.complete()
                } catch (t: Throwable) {
                    future.fail(t)
                }
            }
            .ignoreElement()
            .flatMapSingle { fileSystem.rxProps(zipFilePath).map(FileProps::size) }
    }

    /**
     * Reports the size in bytes of the CSV file.
     */
    private fun exportSize(export: DataExport): Single<Long> {
        return fileSystem.rxProps(export.targetFile()).map(FileProps::size)
    }

    private fun toCSV(export: DataExport, event: MetricEvent): String {
        return export.fields
            .mapNotNull { event.extract(it) }
            .joinToString(separator = ",", postfix = CRLF) { StringEscapeUtils.escapeCsv("" + it) }
    }

    private fun initCsv(export: DataExport): Single<AsyncFile> {
        return fileSystem.rxExists(export.targetFile())
            .flatMapCompletable { exists ->
                if (!exists) {
                    logger.info { "Creating export file ${File(export.targetFile()).absolutePath}" }
                    fileSystem.rxMkdirs(export.targetFolder()).flatMap { fileSystem.rxCreateFile(export.targetFile()) }
                } else {
                    Completable.complete()
                }
            }
            .flatMapSingle { fileSystem.rxOpen(export.targetFile(), OpenOptions().setAppend(true)) }
    }

    private fun DataExport.targetFile(): String {
        return "$exportDir/${this.id}/events.csv"
    }

    private fun DataExport.targetFolder(): String {
        return "$exportDir/${this.id}"
    }
}

private fun MetricEvent.extract(field: EventField): Any? {
    return when (field) {
        EventField.metric -> metric?.getFullyQualifiedId()
        EventField.source -> source
        EventField.timestamp -> timestamp
        EventField.location -> location?.let { Json.encode(it) }
        EventField.tsReceived -> tsReceived
        EventField.producer -> producer?.let { Json.encode(it) }
        EventField.value -> when (value) {
            is JsonObject -> (value as JsonObject).encode()
            is JsonArray -> (value as JsonArray).encode()
            else -> value
        }
        EventField.tags -> tags?.let { Json.encode(it) }
        EventField.elevation -> elevation
        EventField.geohash -> geohash
        EventField.dataset -> dataset
    }
}
