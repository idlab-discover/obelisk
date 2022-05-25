package idlab.obelisk.definitions.data

import com.fasterxml.jackson.annotation.JsonInclude
import idlab.obelisk.definitions.*
import io.reactivex.Completable
import io.reactivex.Single
import java.lang.Exception
import java.util.concurrent.TimeUnit

interface DataStore {
    fun getMetadata(query: MetaQuery): Single<PagedResult<MetaData>> // Get metrics, sources or producers for a certain data range
    fun countMetadata(query: MetaQuery): Single<Long> // Counts the unique combination of entries for the supplied MetaQuery
    fun getEvents(query: EventsQuery): Single<PagedResult<MetricEvent>>
    fun getLatestEvents(query: EventsQuery): Single<PagedResult<MetricEvent>>
    fun getStats(query: StatsQuery): Single<PagedResult<MetricStat>>
    fun ingest(events: List<MetricEvent>): Single<List<WriteResult>>
    fun delete(query: EventsQuery): Completable
    fun deleteLatest(query: EventsQuery): Completable

    fun getStorageInfo(compressed: Boolean = false): Single<StorageInfo>
}

class WriteResult private constructor(
    val success: Boolean,
    val exception: Throwable? = null
) {
    companion object {

        fun success(): WriteResult {
            return WriteResult(success = true)
        }

        fun failure(exception: Throwable): WriteResult {
            return WriteResult(success = false, exception = exception)
        }

    }
}

data class StorageInfo(
    val metaFieldSizeBytes: Long,
    val typeStorageInfo: Map<MetricType, TypeStorageInfo>
) {
    val totalBytes = metaFieldSizeBytes + typeStorageInfo.map { it.value.byteCount }.sum()
    val totalEvents = typeStorageInfo.map { it.value.eventCount }.sum()

    fun averageSizeForMetricType(type: MetricType): Long {
        return typeStorageInfo[type]?.let { if (it.eventCount > 0) it.byteCount / it.eventCount else 0 } ?: 0
    }

    fun averageSizeForMetaFields(): Long {
        return if (totalEvents > 0) metaFieldSizeBytes / totalEvents else 0
    }
}

data class TypeStorageInfo(
    val eventCount: Long,
    val byteCount: Long
)

interface MetricRecord {
    val timestamp: Long?
    val dataset: String?
    val metric: MetricName?
    val producer: Producer?
    val source: String?
    val geohash: String?
}

@JsonInclude(JsonInclude.Include.NON_EMPTY)
data class MetricEvent(
    override val timestamp: Long,
    override val dataset: String? = null,
    override val metric: MetricName? = null,
    val value: Any? = null,
    override val producer: Producer? = null,
    override val source: String? = null,
    val tags: List<String>? = null,
    val location: Location? = null,
    override val geohash: String? = null,
    val elevation: Double? = null,
    val tsReceived: Long? = null
) : MetricRecord {
    private constructor() : this(timestamp = System.currentTimeMillis()) // Hack to enable Pulsar Jackson shaded instance to work...
}

@JsonInclude(JsonInclude.Include.NON_EMPTY)
data class MetricStat(
    override val timestamp: Long? = null,
    override val dataset: String? = null,
    override val metric: MetricName? = null,
    override val producer: Producer? = null,
    override val source: String? = null,
    override val geohash: String? = null,
    val min: Double? = null,
    val max: Double? = null,
    val mean: Double? = null,
    val stddev: Double? = null,
    val count: Long? = null,
    val countSources: Long? = null,
    val sum: Double? = null
) : MetricRecord

// TODO: Use this class to represent MetricInfo maybe?? More generic system => no need for extras in QueryWrappers? (and MetricInfo type see above) REFACTOR
data class MetaData(
    val dataset: String? = null,
    val metricId: String? = null,
    val metricType: MetricType? = null,
    val producer: Producer? = null,
    val source: String? = null,
    val started: Long? = null,
    val lastUpdate: Long? = null,
    val count: Long? = null,
    val avgIngestRate: Double? = null
) {
    fun metricName(): MetricName? {
        return metricType?.let {
            if (metricId != null) MetricName(metricId, it) else MetricName.wildcard(it).first()
        }
    }
}

data class Location(val lat: Double, val lng: Double) {
    private constructor() : this(0.0, 0.0) // Hack to enable Pulsar Jackson shaded instance to work...
}

data class Producer(val userId: String, val clientId: String? = null) {
    private constructor() : this("") // Hack to enable Pulsar Jackson shaded instance to work...
}

interface Query : DataRequest {
    val limit: Int
    val cursor: String?
}

interface DataQuery : Query {
    val timestampPrecision: TimestampPrecision
    val from: Long?
    val to: Long?
    val orderBy: OrderBy
}

data class EventsQuery(
    override val dataRange: DataRange,
    override val timestampPrecision: TimestampPrecision = TimestampPrecision.milliseconds,
    val fields: List<EventField> = listOf(EventField.metric, EventField.source, EventField.value),
    override val from: Long? = null,
    override val to: Long? = null,
    override val orderBy: OrderBy = OrderBy(listOf(IndexField.timestamp), Ordering.asc),
    override var filter: FilterExpression = SELECT_ALL,
    override val limit: Int = 2500,
    val limitBy: LimitBy? = null, // Maybe we should not expose this for the external API (now I'm using this to implement NGSI)
    override val cursor: String? = null
) : DataQuery {
    init {
        require(fields.isNotEmpty())
    }
}

data class StatsQuery(
    override val dataRange: DataRange,
    override val timestampPrecision: TimestampPrecision = TimestampPrecision.milliseconds,
    val fields: List<StatsField> = listOf(StatsField.mean, StatsField.count),
    override val from: Long? = null,
    override val to: Long? = null,
    override val orderBy: OrderBy = OrderBy(listOf(IndexField.timestamp), Ordering.asc),
    override var filter: FilterExpression = SELECT_ALL,
    val groupBy: GroupBy? = null,
    override val limit: Int = 60,
    override val cursor: String? = null
) : DataQuery {
    init {
        require(fields.isNotEmpty()) { "You should query at least one field!" }
    }
}

data class MetaQuery(
    override val dataRange: DataRange,
    val fields: List<MetaField>,
    val orderBy: MetaDataOrderBy = MetaDataOrderBy(emptyList()),
    override var filter: FilterExpression = SELECT_ALL,
    override val limit: Int = 100,
    override val cursor: String? = null
) : Query {
    init {
        require(fields.isNotEmpty()) { "Specify at least one field!" }
    }
}

enum class StatsField {
    dataset, metric, producer, source, geohash, min, max, mean, stddev, count, countSources, sum
}

enum class IndexField {
    timestamp, dataset, metric, producer, source, geohash, tags
}

enum class MetaField {
    dataset, metric, metric_id, metric_type, producer, source, started, lastUpdate, count, avgIngestRate
}

data class OrderBy(val fields: List<IndexField> = listOf(IndexField.timestamp), val ordering: Ordering = Ordering.asc)
data class MetaDataOrderBy(val fields: List<MetaField>, val ordering: Ordering = Ordering.asc)
data class LimitBy(val fields: List<IndexField> = listOf(), val limit: Int, val offset: Int = 0)

data class GroupBy(
    val time: GroupByTime? = null,
    val fields: List<IndexField> = listOf(),
    val geohashPrecision: Short = 4
)

data class GroupByTime(
    val interval: Int = 1,
    val intervalUnit: DurationUnit = DurationUnit.days,
    val offset: Int = 0,
    val offsetUnit: DurationUnit = DurationUnit.seconds
)

enum class DurationUnit(val unit: TimeUnit) {
    seconds(TimeUnit.SECONDS), minutes(TimeUnit.MINUTES), hours(TimeUnit.HOURS), days(TimeUnit.DAYS)
}
