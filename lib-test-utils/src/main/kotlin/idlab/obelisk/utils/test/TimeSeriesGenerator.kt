package idlab.obelisk.utils.test

import idlab.obelisk.definitions.DataRange
import idlab.obelisk.definitions.MetricName
import idlab.obelisk.definitions.MetricType
import idlab.obelisk.definitions.data.Location
import idlab.obelisk.definitions.data.MetricEvent
import idlab.obelisk.definitions.data.Producer
import idlab.obelisk.utils.service.utils.toMs
import idlab.obelisk.utils.service.utils.toMus
import idlab.obelisk.utils.test.rg.DeterministicRG
import idlab.obelisk.utils.test.rg.Locations
import idlab.obelisk.utils.test.rg.Time
import idlab.obelisk.utils.test.rg.Values
import io.vertx.kotlin.core.json.json
import io.vertx.kotlin.core.json.obj
import kotlin.random.asKotlinRandom

object Generator {
    private val GENERATOR_PRODUCER = Producer("system", "test")

    private val rg = DeterministicRG.instance
    val sources = (1 until 250).map { "test_source_$it" }
    val tagSets = (0..30).map { (1..4).map { Values.stringBetween(4, 16) } }
    // Create a limited set of locations
    val locations = (0..100).map { Location(Locations.latitude(), Locations.longitude()) }

    fun events(dataset: String, timeRange: Time.Range, metric: MetricName, nrOfEvents: Int, valueGenerator: (MetricType) -> Any = Generator::defaultValueGenerator): Data {
        return GeneratedData(Time.timestampSequenceIn(timeRange, nrOfEvents).map { timestamp ->
            val value = valueGenerator.invoke(metric.type)
            MetricEvent(
                    timestamp = timestamp.toMus(),
                    tsReceived = System.currentTimeMillis(),
                    producer = GENERATOR_PRODUCER,
                    elevation = if (rg.nextDouble() < 0.8) Values.floatBetween(-20.0, 200.0) else null,
                    location = if (rg.nextDouble() < 0.8) locations.random(rg.asKotlinRandom()) else null,
                    source = if (rg.nextDouble() < 0.8) sources.random(rg.asKotlinRandom()) else null,
                    tags = if (rg.nextDouble() < 0.8) tagSets.random(rg.asKotlinRandom()) else null,
                    value = value,
                    dataset = dataset,
                    metric = metric
            )
        })
    }

    private fun defaultValueGenerator(type: MetricType): Any {
        return when (type) {
            MetricType.NUMBER -> Values.floatBetween(-1000.0, 5000.0)
            MetricType.BOOL -> rg.nextBoolean()
            MetricType.STRING -> Values.someString(1024)
            MetricType.JSON -> json {
                obj {
                    "r" to Values.intBetween(0, 255)
                    "g" to Values.intBetween(0, 255)
                    "b" to Values.intBetween(0, 255)
                }
            }
            MetricType.NUMBER_ARRAY -> Values.floatArray(3)
        }
    }
}

interface Data {
    // Extract datasets present in the data
    fun datasets(): Set<String>

    // Returns raw metric events in time range (sorted by time asc)
    fun events(timeRange: Time.Range? = null): List<MetricEvent>

    // Get random time range for which there should be data
    fun randomTimeRange(): Time.Range

    fun sources(dataRange: DataRange): List<String>
}

data class GeneratedData(val events: List<MetricEvent>) : Data {

    override fun events(timeRange: Time.Range?): List<MetricEvent> {
        return events.filter { timeRange?.contains(it.timestamp.toMs()) ?: true }
    }

    override fun datasets(): Set<String> {
        return events.map { it.dataset!! }.toSet()
    }

    override fun randomTimeRange(): Time.Range {
        return Time.Range(events.first().timestamp.toMs(), events.last().timestamp.toMs()).randomSubRange()
    }

    override fun sources(dataRange: DataRange): List<String> {
        return events.asSequence().filter { dataRange.datasets.contains(it.dataset) && dataRange.metrics?.contains(it.metric!!) ?: true }.map { it.source }.filterNotNull().distinct().sorted().toList()
    }
}

class MergedData(private val sources: List<Data>) : Data {
    constructor(vararg sources: Data) : this(sources.toList())

    override fun events(timeRange: Time.Range?): List<MetricEvent> {
        return sources.flatMap { it.events(timeRange) }.sortedBy { it.timestamp }
    }

    override fun randomTimeRange(): Time.Range {
        return sources.map { it.randomTimeRange() }.random(DeterministicRG.ktInstance)
    }

    override fun datasets(): Set<String> {
        return sources.flatMap { it.datasets() }.toSet()
    }

    override fun sources(dataRange: DataRange): List<String> {
        return sources.flatMap { it.sources(dataRange) }.distinct().sorted()
    }

}