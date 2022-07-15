package idlab.obelisk.plugins.datastore.clickhouse

import ch.hsr.geohash.GeoHash
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.github.davidmoten.rx2.RetryWhen
import idlab.obelisk.definitions.*
import idlab.obelisk.definitions.data.*
import idlab.obelisk.definitions.framework.OblxConfig
import idlab.obelisk.plugins.datastore.clickhouse.impl.utils.toCH
import idlab.obelisk.plugins.datastore.clickhouse.impl.utils.toCHDateTimeMicroseconds
import idlab.obelisk.utils.service.reactive.flatMap
import idlab.obelisk.utils.service.reactive.flatMapSingle
import idlab.obelisk.utils.service.utils.toMs
import idlab.obelisk.utils.service.utils.toMus
import idlab.obelisk.utils.service.utils.unpage
import idlab.obelisk.utils.test.Generator
import idlab.obelisk.utils.test.MergedData
import idlab.obelisk.utils.test.rg.DeterministicRG
import idlab.obelisk.utils.test.rg.Time
import idlab.obelisk.utils.test.rg.Values
import io.reactivex.Completable
import io.reactivex.Single
import io.reactivex.rxkotlin.subscribeBy
import io.reactivex.rxkotlin.toObservable
import io.vertx.core.json.JsonObject
import io.vertx.core.json.jackson.DatabindCodec
import io.vertx.junit5.VertxExtension
import io.vertx.junit5.VertxTestContext
import io.vertx.kotlin.core.json.array
import io.vertx.kotlin.core.json.json
import io.vertx.kotlin.core.json.obj
import io.vertx.reactivex.core.Vertx
import mu.KotlinLogging
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.RepeatedTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.Comparator
import kotlin.math.abs
import kotlin.random.Random

@ExtendWith(VertxExtension::class)
class CHTest {
    private val logger = KotlinLogging.logger {}

    private companion object {
        const val REPEATS = 3
        const val EVENTS_PER_SERIES = 50_000
        val datastore = ClickhouseDataStoreModule().dataStore(Vertx.vertx(), OblxConfig())
        val timeRange = Time.Range(1546297200000L, 1577833200000L)
        const val dataset1 = "test_airquality"
        const val dataset2 = "test_airquality.ngsi"
        const val dataset3 = "test_mbrain"
        const val dataset4 = "test_deletes"
        val metricNo2 = MetricName("test_airquality.no2::number")
        val metricCo2 = MetricName("test_airquality.co2::number")
        val metricAQO = MetricName("test_AirQualityObserved::json")
        val metricAccel = MetricName("test_accelerometer::number[]")
        val airqualityNo2Data = Generator.events(dataset1, timeRange, metricNo2, EVENTS_PER_SERIES)
        val airqualityData = MergedData(
            airqualityNo2Data,
            Generator.events(dataset1, timeRange, metricCo2, EVENTS_PER_SERIES)
        )
        val airqualityObservedData = Generator.events(dataset2, timeRange, metricAQO, EVENTS_PER_SERIES) {
            json {
                obj(
                    "refDevice" to Values.stringBetween(4, 12),
                    "airQualityIndex" to Values.floatBetween(100.0, 500.0),
                    "properties" to obj(
                        "observedAt" to Instant.now().toCH(),
                        "state" to array(Values.intBetween(0, 9), Values.intBetween(0, 9))
                    )
                )
            }
        }
        val data = MergedData(airqualityData, airqualityObservedData)

        @JvmStatic
        @BeforeAll
        fun initDB(context: VertxTestContext) {
            DatabindCodec.mapper().registerKotlinModule()
            DatabindCodec.prettyMapper().registerKotlinModule()

            datastore.delete(
                EventsQuery(
                    dataRange = DataRange(
                        datasets = listOf(
                            dataset1,
                            dataset2,
                            dataset3,
                            dataset4
                        )
                    )
                )
            )
                .andThen(Completable.defer {
                    data.events().toObservable()
                        .buffer(5000)
                        .concatMapCompletable { datastore.ingest(it).ignoreElement() }
                })
                .subscribeBy(onComplete = context::completeNow, onError = context::failNow)
        }
    }

    @RepeatedTest(10)
    fun testPagingWithMultiIndexAsc(context: VertxTestContext) {
        val query = EventsQuery(
            dataRange = DataRange(listOf(dataset1), listOf(metricNo2, metricCo2)),
            fields = EventField.values().toList(),
            timestampPrecision = TimestampPrecision.microseconds,
            orderBy = OrderBy(listOf(IndexField.metric, IndexField.source, IndexField.timestamp)),
            limit = 10000
        )
        val expectedResults = airqualityData.events()
            .filter { it.dataset == dataset1 && (it.metric == metricNo2 || it.metric == metricCo2) }
            .sortedWith(Comparator.comparing<MetricEvent, String?> { it!!.metric!!.getFullyQualifiedId() }
                .thenBy { it.source }.thenBy { it.timestamp })
        unpage { cursor -> datastore.getEvents(query.copy(cursor = cursor)) }.toList().subscribeBy(
            onSuccess = { context.verify { assertEventsEquals(expectedResults, it) }.completeNow() },
            onError = context::failNow
        )
    }

    @Test
    fun testPagingWithMultiIndexDesc(context: VertxTestContext) {
        val query = EventsQuery(
            dataRange = DataRange(listOf(dataset1), listOf(metricNo2, metricCo2)),
            fields = EventField.values().toList(),
            timestampPrecision = TimestampPrecision.microseconds,
            orderBy = OrderBy(listOf(IndexField.metric, IndexField.source, IndexField.timestamp), Ordering.desc),
            limit = 10000
        )
        val expectedResults = airqualityData.events()
            .filter { it.dataset == dataset1 && (it.metric == metricNo2 || it.metric == metricCo2) }
            .sortedWith(Comparator.comparing<MetricEvent, String?> { it!!.metric!!.getFullyQualifiedId() }
                .thenBy { it.source }.thenBy { it.timestamp }.reversed())
        unpage { cursor -> datastore.getEvents(query.copy(cursor = cursor)) }.toList().subscribeBy(
            onSuccess = { context.verify { assertEventsEquals(expectedResults, it) }.completeNow() },
            onError = context::failNow
        )
    }


    @RepeatedTest(REPEATS)
    fun testGetNumberEvents(context: VertxTestContext) {
        val range = airqualityData.randomTimeRange()
        val query = EventsQuery(
            dataRange = DataRange(listOf(dataset1), listOf(metricNo2, metricCo2)),
            from = range.fromMs,
            to = range.toMs,
            fields = EventField.values().toList(),
            timestampPrecision = TimestampPrecision.microseconds
        )
        val expectedResults = airqualityData.events(range)
        unpage { cursor -> datastore.getEvents(query.copy(cursor = cursor)) }.toList().subscribeBy(
            onSuccess = { context.verify { assertEventsEquals(expectedResults, it) }.completeNow() },
            onError = context::failNow
        )
    }

    @RepeatedTest(REPEATS)
    fun testGetNumberEventsMsPrecision(context: VertxTestContext) {
        val range = airqualityData.randomTimeRange()
        val query = EventsQuery(
            dataRange = DataRange(listOf(dataset1), listOf(metricNo2, metricCo2)),
            from = range.fromMs,
            to = range.toMs,
            fields = EventField.values().toList(),
            timestampPrecision = TimestampPrecision.milliseconds
        )
        val expectedResults = airqualityData.events(range).map { it.copy(timestamp = it.timestamp.toMs()) }
        unpage { cursor -> datastore.getEvents(query.copy(cursor = cursor)) }.toList().subscribeBy(
            onSuccess = { context.verify { assertEventsEquals(expectedResults, it) }.completeNow() },
            onError = context::failNow
        )
    }

    @Test
    fun testGetEventsByMetricPrefix(context: VertxTestContext) {
        val metricPrefix = "test_airquality"
        val limit = 500
        val query = EventsQuery(
            dataRange = DataRange(listOf(dataset1), listOf(metricNo2, metricCo2)),
            fields = EventField.values().toList(),
            timestampPrecision = TimestampPrecision.microseconds,
            filter = StartsWith("metric", metricPrefix),
            limit = limit
        )
        val expectedResults = airqualityData.events().filter { it.metric!!.name.startsWith(metricPrefix) }.take(limit)
        datastore.getEvents(query).map { it.items }.subscribeBy(
            onSuccess = { context.verify { assertEventsEquals(expectedResults, it) }.completeNow() },
            onError = context::failNow
        )
    }

    @RepeatedTest(REPEATS)
    fun testGetEventsByTags(context: VertxTestContext) {
        val range = airqualityData.randomTimeRange()
        val tag = Generator.tagSets.random(DeterministicRG.ktInstance).random(DeterministicRG.ktInstance);
        val query = EventsQuery(
            dataRange = DataRange(listOf(dataset1), listOf(metricNo2, metricCo2)),
            from = range.fromMs,
            to = range.toMs,
            fields = EventField.values().toList(),
            timestampPrecision = TimestampPrecision.microseconds,
            filter = HasTag(tag)
        )
        val expectedResults = airqualityData.events(range).filter { it.tags?.contains(tag) ?: false }
        unpage { cursor -> datastore.getEvents(query.copy(cursor = cursor)) }.toList().subscribeBy(
            onSuccess = { context.verify { assertEventsEquals(expectedResults, it) }.completeNow() },
            onError = context::failNow
        )
    }

    @RepeatedTest(REPEATS)
    fun testGetJsonEvents(context: VertxTestContext) {
        val range = airqualityObservedData.randomTimeRange()
        val query = EventsQuery(
            dataRange = DataRange(listOf(dataset2), listOf(metricAQO)),
            from = range.fromMs,
            to = range.toMs,
            fields = EventField.values().toList(),
            timestampPrecision = TimestampPrecision.microseconds
        )
        val expectedResults = airqualityObservedData.events(range)
        unpage { cursor -> datastore.getEvents(query.copy(cursor = cursor)) }.toList().subscribeBy(
            onSuccess = { context.verify { assertEventsEquals(expectedResults, it) }.completeNow() },
            onError = context::failNow
        )
    }

    @Test
    fun testGetSources(context: VertxTestContext) {
        val dataRange = DataRange(listOf(dataset1), listOf(metricNo2))
        // Querying sources for metric airquality.no2 in dataset airquality
        val query = MetaQuery(
            dataRange = dataRange,
            fields = listOf(MetaField.source),
            orderBy = MetaDataOrderBy(listOf(MetaField.source))
        )
        val expectedResults = airqualityData.sources(dataRange)
        unpage { cursor -> datastore.getMetadata(query.copy(cursor = cursor)) }.toList().subscribeBy(
            onSuccess = { context.verify { assertEquals(expectedResults, it.map { it.source }) }.completeNow() },
            onError = context::failNow
        )
    }

    // Same as testGetSources, but with readFilter that includes filter on attribute not present in metadata table.
    // This filter should be ignored and all sources should be returned
    @Test
    fun testGetSourcesUnkownFilter(context: VertxTestContext) {
        val dataRange = DataRange(listOf(dataset1), listOf(metricNo2))
        // Querying sources for metric airquality.no2 in dataset airquality
        val query = MetaQuery(
            dataRange = dataRange,
            fields = listOf(MetaField.source),
            orderBy = MetaDataOrderBy(listOf(MetaField.source)),
            filter = HasTag("someTag")
        )
        val expectedResults = airqualityData.sources(dataRange)
        unpage { cursor -> datastore.getMetadata(query.copy(cursor = cursor)) }.toList().subscribeBy(
            onSuccess = { context.verify { assertEquals(expectedResults, it.map { it.source }) }.completeNow() },
            onError = context::failNow
        )
    }

    // Same as testGetSources, but with readFilter that includes filter on attribute not present in metadata table.
    // This filter should be ignored and all sources should be returned
    @Test
    fun testGetSourcesReadFilter(context: VertxTestContext) {
        val dataRange = DataRange(listOf(dataset1), listOf(metricNo2))
        val selectedSources = airqualityData.sources(dataRange).shuffled().take(2).toSet()
        // Querying sources for metric airquality.no2 in dataset airquality
        val query = MetaQuery(
            dataRange = dataRange,
            fields = listOf(MetaField.source),
            orderBy = MetaDataOrderBy(listOf(MetaField.source)),
            filter = In(MetaField.source, selectedSources)
        )
        unpage { cursor -> datastore.getMetadata(query.copy(cursor = cursor)) }.toList().subscribeBy(
            onSuccess = {
                context.verify { assertEquals(selectedSources, it.map { it.source }.toSet()) }.completeNow()
            },
            onError = context::failNow
        )
    }

    @Test
    fun testGetMetrics(context: VertxTestContext) {
        val dataRange = DataRange(listOf(dataset1, dataset2))
        // Querying all metrics (should result in no2, co2 and airqualityobserved)
        val query = MetaQuery(
            dataRange = dataRange,
            fields = listOf(MetaField.metric),
            orderBy = MetaDataOrderBy(listOf(MetaField.metric))
        )
        val expectedResults =
            listOf(metricAQO, metricNo2, metricCo2).sortedBy(MetricName::getFullyQualifiedId)
        unpage { cursor -> datastore.getMetadata(query.copy(cursor = cursor)) }.toList().subscribeBy(
            onSuccess = { context.verify { assertEquals(expectedResults, it.map { it.metricName() }) }.completeNow() },
            onError = context::failNow
        )
    }

    // Test EventsQuery combining ::json and ::number metric types
    @Test
    fun testGetCombinedEvents(context: VertxTestContext) {
        val rangeNumbers = airqualityNo2Data.randomTimeRange()
        val rangeJson = airqualityObservedData.randomTimeRange()
        val timeFilter = Or(
            And(
                Gte("timestamp", toCHDateTimeMicroseconds(rangeNumbers.fromMs.toMus())),
                Lt("timestamp", toCHDateTimeMicroseconds(rangeNumbers.toMs.toMus()))
            ),
            And(
                Gte("timestamp", toCHDateTimeMicroseconds(rangeJson.fromMs.toMus())),
                Lt("timestamp", toCHDateTimeMicroseconds(rangeJson.toMs.toMus()))
            )
        )
        val query = EventsQuery(
            dataRange = DataRange(
                listOf(dataset1, dataset2),
                listOf(metricCo2, MetricName(metricAQO.getFullyQualifiedId() + "/airQualityIndex::number"))
            ),
            fields = listOf(EventField.timestamp, EventField.value, EventField.source),
            timestampPrecision = TimestampPrecision.microseconds,
            filter = timeFilter
        )
        val expectedResults = data.events(Time.Range(0, Long.MAX_VALUE))
            .filter { (rangeNumbers.contains(it.timestamp.toMs()) || rangeJson.contains(it.timestamp.toMs())) && (it.metric!! == metricCo2 || it.metric!! == metricAQO) }
            .map { if (it.value is JsonObject) it.copy(value = (it.value as JsonObject).getDouble("airQualityIndex")) else it }
        unpage { cursor -> datastore.getEvents(query.copy(cursor = cursor)) }.toList().subscribeBy(
            onSuccess = { context.verify { assertEventsEquals(expectedResults, it) }.completeNow() },
            onError = context::failNow
        )
    }

    @Test
    fun testGetEventsLatest(context: VertxTestContext) {
        val query = EventsQuery(
            dataRange = DataRange(listOf(dataset1), listOf(metricNo2)),
            fields = listOf(EventField.timestamp, EventField.value, EventField.source),
            timestampPrecision = TimestampPrecision.microseconds,
            orderBy = OrderBy(listOf(IndexField.timestamp), Ordering.desc)
        )
        val expectedResults = data.events(Time.Range(0, Long.MAX_VALUE))
            .filter { it.dataset == dataset1 && it.metric!! == metricNo2 }
            .groupBy(keySelector = MetricEvent::source)
            .map { it.value.maxByOrNull(MetricEvent::timestamp)!! }
            .sortedByDescending { it.timestamp }
        unpage { cursor -> datastore.getLatestEvents(query.copy(cursor = cursor)) }.toList().subscribeBy(
            onSuccess = { context.verify { assertEventsEquals(expectedResults, it) }.completeNow() },
            onError = context::failNow
        )
    }

    @Test
    fun testGetStats(context: VertxTestContext) {
        val range = airqualityNo2Data.randomTimeRange()
        val query = StatsQuery(
            dataRange = DataRange(listOf(dataset1), listOf(metricNo2)),
            fields = listOf(StatsField.mean, StatsField.count),
            from = range.fromMs,
            to = range.toMs
        )
        val rawResults = data.events(range)
            .filter { it.dataset == dataset1 && it.metric!! == metricNo2 }
        val expectedResult = listOf(
            MetricStat(
                mean = rawResults.map { it.value as Double? }.filterNotNull().average(),
                count = rawResults.size.toLong()
            )
        )
        datastore.getStats(query).map { it.items }.subscribeBy(
            onSuccess = { context.verify { assertStatsEquals(expectedResult, it) }.completeNow() },
            onError = context::failNow
        )
    }

    @RepeatedTest(REPEATS)
    fun testGetStatsGroupByTime(context: VertxTestContext) {
        val range = airqualityNo2Data.randomTimeRange()
        val query = StatsQuery(
            dataRange = DataRange(listOf(dataset1), listOf(metricNo2)),
            fields = listOf(StatsField.mean, StatsField.count),
            from = range.fromMs,
            to = range.toMs,
            groupBy = GroupBy(time = GroupByTime(1, DurationUnit.seconds)),
            timestampPrecision = TimestampPrecision.microseconds,
            limit = 5
        )
        val expectedResults = data.events(range)
            .filter { it.dataset == dataset1 && it.metric!! == metricNo2 }
            .groupBy { Instant.ofEpochMilli(it.timestamp.toMs()).truncatedTo(ChronoUnit.SECONDS) }
            .map {
                MetricStat(
                    timestamp = it.key.toEpochMilli().toMus(),
                    mean = it.value.map { it.value as Double? }.filterNotNull().average(),
                    count = it.value.size.toLong()
                )
            }
        unpage { cursor -> datastore.getStats(query.copy(cursor = cursor)) }.toList().subscribeBy(
            onSuccess = { context.verify { assertStatsEquals(expectedResults, it) }.completeNow() },
            onError = context::failNow
        )
    }

    @Test
    fun testGetStatsGroupedBySource(context: VertxTestContext) {
        val query = StatsQuery(
            dataRange = DataRange(listOf(dataset1), listOf(MetricName("*::number"))),
            fields = listOf(StatsField.source, StatsField.mean, StatsField.count),
            groupBy = GroupBy(fields = listOf(IndexField.source)),
            orderBy = OrderBy(fields = listOf(IndexField.source))
        )
        var expectedResults = data.events(Time.Range(0, Long.MAX_VALUE))
            .filter { it.dataset == dataset1 }
            .groupBy { it.source }
            .map {
                MetricStat(
                    source = it.key,
                    mean = it.value.map { it.value as Double? }.filterNotNull().average(),
                    count = it.value.size.toLong()
                )
            }
            .sortedBy { it.source }
        unpage { cursor -> datastore.getStats(query.copy(cursor = cursor)) }.toList().subscribeBy(
            onSuccess = { context.verify { assertStatsEquals(expectedResults, it) }.completeNow() },
            onError = context::failNow
        )
    }

    @Test
    fun testGetStatsGroupByGeohash(context: VertxTestContext) {
        val geohashPrecision = 4
        val query = StatsQuery(
            dataRange = DataRange(listOf(dataset1), listOf(MetricName("*::number"))),
            fields = listOf(StatsField.geohash, StatsField.mean, StatsField.count),
            groupBy = GroupBy(fields = listOf(IndexField.geohash), geohashPrecision = geohashPrecision.toShort()),
            orderBy = OrderBy(fields = listOf(IndexField.geohash))
        )
        var expectedResults = data.events(Time.Range(0, Long.MAX_VALUE))
            .filter { it.dataset == dataset1 }
            .filter { it.location != null }
            .map {
                it.copy(
                    geohash = GeoHash.geoHashStringWithCharacterPrecision(
                        it.location!!.lat,
                        it.location!!.lng,
                        geohashPrecision
                    )
                )
            }
            .groupBy { it.geohash }
            .map {
                MetricStat(
                    geohash = it.key,
                    mean = it.value.map { it.value as Double? }.filterNotNull().average(),
                    count = it.value.size.toLong()
                )
            }
            .sortedBy { it.geohash }
        unpage { cursor -> datastore.getStats(query.copy(cursor = cursor)) }.toList().subscribeBy(
            onSuccess = { context.verify { assertStatsEquals(expectedResults, it) }.completeNow() },
            onError = context::failNow
        )
    }

    @Test
    fun testGetCombinedStats(context: VertxTestContext) {
        val rangeNumbers = airqualityNo2Data.randomTimeRange()
        val rangeJson = airqualityObservedData.randomTimeRange()
        val timeFilter = Or(
            And(
                Gte("timestamp", toCHDateTimeMicroseconds(rangeNumbers.fromMs.toMus())),
                Lt("timestamp", toCHDateTimeMicroseconds(rangeNumbers.toMs.toMus()))
            ),
            And(
                Gte("timestamp", toCHDateTimeMicroseconds(rangeJson.fromMs.toMus())),
                Lt("timestamp", toCHDateTimeMicroseconds(rangeJson.toMs.toMus()))
            )
        )
        val query = StatsQuery(
            dataRange = DataRange(
                listOf(dataset1, dataset2),
                listOf(metricCo2, MetricName(metricAQO.getFullyQualifiedId() + "/airQualityIndex::number"))
            ),
            fields = listOf(StatsField.mean, StatsField.count),
            timestampPrecision = TimestampPrecision.microseconds,
            filter = timeFilter
        )
        val expectedResults = data.events(Time.Range(0, Long.MAX_VALUE))
            .filter { (rangeNumbers.contains(it.timestamp.toMs()) || rangeJson.contains(it.timestamp.toMs())) && (it.metric!! == metricCo2 || it.metric!! == metricAQO) }
            .map { if (it.value is JsonObject) it.copy(value = (it.value as JsonObject).getDouble("airQualityIndex")) else it }
            .let {
                listOf(
                    MetricStat(
                        mean = it.map { it.value as Double? }.filterNotNull().average(),
                        count = it.size.toLong()
                    )
                )
            }
        unpage { cursor -> datastore.getStats(query.copy(cursor = cursor)) }.toList().subscribeBy(
            onSuccess = { context.verify { assertStatsEquals(expectedResults, it) }.completeNow() },
            onError = context::failNow
        )
    }

    @Test
    fun testCountNonNumberRange(context: VertxTestContext) {
        val query = StatsQuery(
            dataRange = DataRange(listOf(dataset2), listOf(metricAQO)),
            fields = listOf(StatsField.count),
            timestampPrecision = TimestampPrecision.microseconds
        )

        val expectedResults = data.events(Time.Range(0, Long.MAX_VALUE))
            .filter { it.dataset == dataset2 && it.metric == metricAQO }
            .count()
            .let { listOf(MetricStat(count = it.toLong())) }

        unpage { cursor -> datastore.getStats(query.copy(cursor = cursor)) }.toList().subscribeBy(
            onSuccess = { context.verify { assertStatsEquals(expectedResults, it) }.completeNow() },
            onError = context::failNow
        )
    }

    //TODO: vertx jdbc client bug must be fixed first: https://github.com/vert-x3/vertx-jdbc-client/issues/195
    // Temp workaround using Strings, but this leads to loss of precision...
    @Test
    fun testNumberArray(context: VertxTestContext) {
        val acceleroMeterData = (0..10).map {
            MetricEvent(
                timestamp = timeRange.fromMs.toMus() + it,
                dataset = dataset3,
                metric = metricAccel,
                producer = Producer("system", "test"),
                value = (0 until 3).map { Random.nextDouble() }
            )
        }

        datastore.ingest(acceleroMeterData)
            .ignoreElement()
            .flatMapSingle {
                datastore.getEvents(
                    EventsQuery(
                        dataRange = DataRange(datasets = listOf(dataset3), metrics = listOf(metricAccel)),
                        timestampPrecision = TimestampPrecision.microseconds,
                        fields = listOf(EventField.timestamp, EventField.value, EventField.source)
                    )
                )
            }
            .subscribeBy(
                onSuccess = { result ->
                    context.verify { assertEventsEquals(acceleroMeterData, result.items) }.completeNow()
                },
                onError = context::failNow
            )
    }

    // Test deleting specific events
    @Test
    fun testDelete(context: VertxTestContext) {
        val metric = MetricName("test.randomDouble::number")
        val sources = (0..4).map { "source$it" }
        val data = (0..100).map {
            MetricEvent(
                timestamp = timeRange.fromMs.toMus() + it,
                dataset = dataset4,
                metric = metric,
                producer = Producer("system", "test"),
                source = sources.random(),
                value = Random.nextDouble()
            )
        }

        val sourceToDelete = sources.random()
        println("Deleting source $sourceToDelete")
        datastore.ingest(data)
            .ignoreElement()
            .flatMap {
                datastore.delete(
                    EventsQuery(
                        dataRange = DataRange(datasets = listOf(dataset4), metrics = listOf(metric)),
                        filter = Eq(EventField.source, sourceToDelete)
                    )
                )
            }
            .flatMapSingle {
                datastore.getEvents(
                    EventsQuery(
                        dataRange = DataRange(datasets = listOf(dataset4), metrics = listOf(metric)),
                        timestampPrecision = TimestampPrecision.microseconds
                    )
                )
                    .flatMap { result ->
                        try {
                            assertEventsEquals(data.filter { it.source != sourceToDelete }, result.items)
                            Single.just(result)
                        } catch (at: AssertionError) {
                            Single.error(at)
                        }
                    }
                    .retryWhen(RetryWhen.exponentialBackoff(100, TimeUnit.MILLISECONDS).maxRetries(10).build())
            }
            .subscribeBy(
                onSuccess = { result ->
                    context.verify { assertEventsEquals(data.filter { it.source != sourceToDelete }, result.items) }
                        .completeNow()
                },
                onError = context::failNow
            )
    }

    // Test deleting latest events for a specific series
    @Test
    fun testDeleteLatest(context: VertxTestContext) {
        val metric = MetricName("test.randomDouble2::number")
        val timestamps = (0..10).map { timeRange.fromMs.toMus() + it }
        val data = (0..4).flatMap { sourceId -> timestamps.map { Pair("source$sourceId", it) } }.map { (source, ts) ->
            MetricEvent(
                timestamp = ts,
                dataset = dataset4,
                metric = metric,
                producer = Producer("system", "test"),
                source = source,
                value = Random.nextDouble()
            )
        }
        val lastTs = timestamps.last()
        println("Last TS: $lastTs")

        val eventComparator = Comparator.comparing<MetricEvent, Long?> { it.timestamp }
            .then(Comparator.comparing<MetricEvent, String?> { it.source!! })

        datastore.ingest(data)
            .ignoreElement()
            .flatMap {
                datastore.deleteLatest(
                    EventsQuery(
                        dataRange = DataRange(datasets = listOf(dataset4), metrics = listOf(metric))
                    )
                )
            }
            .flatMapSingle {
                datastore.getEvents(
                    EventsQuery(
                        dataRange = DataRange(datasets = listOf(dataset4), metrics = listOf(metric)),
                        timestampPrecision = TimestampPrecision.microseconds
                    )
                )
                    .flatMap { result ->
                        try {
                            assertEventsEquals(
                                data.filter { it.timestamp != lastTs }.sortedWith(eventComparator),
                                result.items.sortedWith(eventComparator)
                            )
                            Single.just(result)
                        } catch (at: AssertionError) {
                            Single.error(at)
                        }
                    }
                    .retryWhen(RetryWhen.exponentialBackoff(100, TimeUnit.MILLISECONDS).maxRetries(10).build())
            }
            .subscribeBy(
                onSuccess = { result ->
                    context.verify {
                        assertEventsEquals(
                            data.filter { it.timestamp != lastTs }.sortedWith(eventComparator),
                            result.items.sortedWith(eventComparator)
                        )
                    }
                        .completeNow()
                },
                onError = context::failNow
            )
    }

    private fun assertEventsEquals(expected: List<MetricEvent>, actual: List<MetricEvent>) {
        if (expected.isEmpty()) {
            logger.warn { "Expected resultset for test is empty, check the validity of the test!" }
        }
        assertEquals(expected.size, actual.size, "Query result size does not match the expected result!")
        expected.zip(actual).forEach {
            assertEquals(it.first.timestamp, it.second.timestamp, "Wrong timestamp for event!")
            assertEquals(it.first.source ?: "", it.second.source, "Wrong source for event!")
            if (it.second.value is Double) {
                assertDoubleAboutEquals(it.first.value as Double, it.second.value as Double, "Wrong value for event!")
            } else if (it.second.value is List<*> && it.first.value is List<*>) {
                (it.first.value as List<*>).zip(it.second.value as List<*>).forEach { numberArrPair ->
                    if (numberArrPair.second is Double) {
                        assertDoubleAboutEquals(
                            numberArrPair.first as Double,
                            numberArrPair.second as Double,
                            "Wrong value for event!"
                        )
                    } else {
                        assertEquals(numberArrPair.first, numberArrPair.second, "Wrong value for event!")
                    }
                }
            } else {
                assertEquals(it.first.value, it.second.value, "Wrong value for event!")
            }
        }
    }

    private fun assertStatsEquals(expected: List<MetricStat>, actual: List<MetricStat>) {
        if (expected.isEmpty()) {
            logger.warn { "Expected resultset for test is empty, check the validity of the test!" }
        }
        assertEquals(expected.size, actual.size, "Query result size does not match the expected result!")
        expected.zip(actual).forEach {
            assertEquals(it.first.timestamp, it.second.timestamp, "Wrong timestamp for stat!")
            if (it.first.mean != null) {
                assertDoubleAboutEquals(it.first.mean!!, it.second.mean!!, "Wrong mean value for stat!")
            }
            if (it.first.count != null) {
                assertEquals(it.first.count!!, it.second.count!!, "Wrong count value for stat!")
            }
        }
    }

    private fun assertDoubleAboutEquals(expected: Double, actual: Double, msg: String) {
        assertTrue(abs(expected - actual) < 0.0001, "$msg (expected $expected, got $actual)")
    }
}
