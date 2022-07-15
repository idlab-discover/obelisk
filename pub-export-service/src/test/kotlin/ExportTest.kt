import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import idlab.obelisk.definitions.*
import idlab.obelisk.definitions.catalog.DataExport
import idlab.obelisk.definitions.catalog.ExportStatus
import idlab.obelisk.definitions.catalog.MetaStore
import idlab.obelisk.definitions.control.ControlChannels
import idlab.obelisk.definitions.control.ExportEvent
import idlab.obelisk.definitions.data.DataStore
import idlab.obelisk.definitions.data.EventsQuery
import idlab.obelisk.definitions.data.MetricEvent
import idlab.obelisk.definitions.framework.OblxConfig
import idlab.obelisk.definitions.messaging.MessageBroker
import idlab.obelisk.definitions.messaging.MessageProducer
import idlab.obelisk.plugins.datastore.clickhouse.ClickhouseDataStoreModule
import idlab.obelisk.plugins.messagebroker.pulsar.PulsarMessageBrokerModule
import idlab.obelisk.services.pub.export.DEFAULT_EXPORTS_DIR
import idlab.obelisk.services.pub.export.ExportRunner
import idlab.obelisk.utils.service.OblxBaseModule
import idlab.obelisk.utils.service.OblxLauncher
import idlab.obelisk.utils.service.reactive.flatMap
import idlab.obelisk.utils.test.Generator
import idlab.obelisk.utils.test.MergedData
import idlab.obelisk.utils.test.rg.Time
import idlab.obelisk.utils.test.rg.Values
import io.reactivex.rxkotlin.toObservable
import io.vertx.core.json.JsonObject
import io.vertx.core.json.jackson.DatabindCodec
import io.vertx.junit5.Timeout
import io.vertx.junit5.VertxExtension
import io.vertx.kotlin.core.json.json
import io.vertx.kotlin.core.json.obj
import io.vertx.reactivex.core.Vertx
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.rx2.await
import kotlinx.coroutines.rx2.awaitSingleOrNull
import mu.KotlinLogging
import net.lingala.zip4j.ZipFile
import org.apache.commons.csv.CSVFormat
import org.apache.commons.lang.StringEscapeUtils
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import java.io.FileReader
import java.util.concurrent.TimeUnit

@ExtendWith(VertxExtension::class)
class ExportTest {
    private val logger = KotlinLogging.logger { }

    private companion object {
        private lateinit var exportRunner: ExportRunner
        private lateinit var jobTriggerProducer: MessageProducer<ExportEvent>
        private lateinit var metaStore: MetaStore

        const val EVENTS_PER_SERIES = 50000
        val timeRange = Time.Range(1546297200000L, 1577833200000L)
        const val dataset1 = "test_airquality_export"
        const val dataset2 = "test_airquality_export.ngsi"
        val metricNo2 = MetricName("test_airquality.no2::number")
        val metricCo2 = MetricName("test_airquality.co2::number")
        val metricAQO = MetricName("test_AirQualityObserved::json")
        val airqualityNo2Data = Generator.events(dataset1, timeRange, metricNo2, EVENTS_PER_SERIES)
        val airqualityCo2Data = Generator.events(dataset1, timeRange, metricCo2, EVENTS_PER_SERIES)
        val airqualityObservedData = Generator.events(dataset2, timeRange, metricAQO, EVENTS_PER_SERIES) {
            json {
                obj(
                    "refDevice" to Values.stringBetween(4, 12),
                    "airQualityIndex" to Values.floatBetween(100.0, 500.0)
                )
            }
        }
        val data = MergedData(airqualityNo2Data, airqualityCo2Data, airqualityObservedData)

        @JvmStatic
        @BeforeAll
        @Timeout(value = 10, timeUnit = TimeUnit.MINUTES)
        fun init(vertx: Vertx) = runBlocking {
            DatabindCodec.mapper().registerKotlinModule()
            DatabindCodec.prettyMapper().registerKotlinModule()

            val launcher =
                OblxLauncher.with(
                    OblxBaseModule(),
                    PulsarMessageBrokerModule(),
                    MockMetaStoreModule(),
                    ClickhouseDataStoreModule()
                )
            val messageBroker = launcher.getInstance(MessageBroker::class.java)
            metaStore = launcher.getInstance(MetaStore::class.java)
            val dataStore = launcher.getInstance(DataStore::class.java)
            exportRunner = ExportRunner(
                metaStore,
                launcher.getInstance(DataStore::class.java),
                messageBroker,
                launcher.getInstance(Vertx::class.java),
                launcher.getInstance(OblxConfig::class.java)
            )
            jobTriggerProducer = messageBroker.createProducer(
                topicName = ControlChannels.EXPORT_EVENT_TOPIC,
                contentType = ExportEvent::class
            )

            exportRunner.start(jobTriggerProducer)

            // Remove all existing exports
            vertx.fileSystem().rxDeleteRecursive(DEFAULT_EXPORTS_DIR, true)
                .onErrorComplete() // If this fails, the folder did not exist, so we continue!
                .flatMap { dataStore.delete(EventsQuery(dataRange = DataRange(datasets = data.datasets().toList()))) }
                .flatMap {
                    // Populate DB
                    data.events().toObservable()
                        .buffer(10000)
                        .concatMapCompletable { dataStore.ingest(it).ignoreElement() }
                }.await()
        }
    }

    @Test
    @Timeout(value = 10, timeUnit = TimeUnit.MINUTES)
    fun testExportAll(vertx: Vertx) = runBlocking {
        exportTestHelper(vertx, "test-export-all")
    }


    @Test
    @Timeout(value = 10, timeUnit = TimeUnit.MINUTES)
    fun testExportLimit(vertx: Vertx) = runBlocking {
        exportTestHelper(vertx, "test-export-limited", 50000)
    }

    private suspend fun exportTestHelper(vertx: Vertx, id: String, limit: Int? = null) {
        // Generate and validate the CSV export
        val exportRequest = DataExport(
            id = id,
            userId = "",
            dataRange = DataRange(
                datasets = data.datasets().toList(),
                metrics = listOf(MetricName("*::number"), MetricName("*::json"))
            ),
            fields = listOf(EventField.timestamp, EventField.dataset, EventField.metric, EventField.value),
            timestampPrecision = TimestampPrecision.microseconds,
            limit = limit
        )

        val exportId = metaStore.createDataExport(exportRequest).await()
        jobTriggerProducer.send(ExportEvent(exportId))
        var export: DataExport
        do {
            export = metaStore.getDataExport(exportId).awaitSingleOrNull()!!
            when (export.status.status) {
                ExportStatus.QUEUING, ExportStatus.GENERATING -> delay(2000)
                ExportStatus.CANCELLED, ExportStatus.FAILED -> RuntimeException("Job Failed!!")
                else -> {}
            }
        } while (export.status.status != ExportStatus.COMPLETED)

        ZipFile("$DEFAULT_EXPORTS_DIR/${export.id}.zip").extractAll("$DEFAULT_EXPORTS_DIR/${export.id}")
        val events = FileReader("$DEFAULT_EXPORTS_DIR/${export.id}/events.csv").use { csv ->
            CSVFormat.DEFAULT.parse(csv)
                .filter { it.size() > 0 }
                .map { record ->
                    val metric = MetricName(record[2])
                    MetricEvent(
                        timestamp = record[0].toLong(),
                        dataset = record[1],
                        metric = metric,
                        value = when (metric.type) {
                            MetricType.JSON -> JsonObject(StringEscapeUtils.unescapeCsv(record[3]))
                            MetricType.NUMBER -> record[3].toDouble()
                            MetricType.STRING -> record[3]
                            MetricType.BOOL -> record[3].toBoolean()
                            MetricType.NUMBER_ARRAY -> record[3]
                        }
                    )
                }
        }

        val expected = limit?.let { l -> data.events().sortedBy { it.timestamp }.take(l) } ?: data.events()
            .sortedBy { it.timestamp }
        assertMetricsEquals(expected, events)
    }

    private fun assertMetricsEquals(expected: List<MetricEvent>, actual: List<MetricEvent>) {
        assertEquals(expected.size, actual.size)
        expected.zip(actual).forEach { (expectedEvent, actualEvent) ->
            assertEquals(expectedEvent.timestamp, actualEvent.timestamp)
            assertEquals(expectedEvent.dataset, actualEvent.dataset)
            assertEquals(expectedEvent.metric, actualEvent.metric)
            if (expectedEvent.value is Double) {
                assertAboutEquals(expectedEvent.value as Double, actualEvent.value as Double, "Wrong value for event!")
            } else {
                assertEquals(expectedEvent.value, actualEvent.value, "Wrong value for event!")
            }
        }
    }

    private fun assertAboutEquals(expected: Double, actual: Double, msg: String) {
        Assertions.assertTrue((expected - actual) < 0.00001, msg)
    }
}
