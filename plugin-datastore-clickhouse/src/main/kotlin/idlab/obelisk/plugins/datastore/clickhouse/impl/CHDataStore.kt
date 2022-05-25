package idlab.obelisk.plugins.datastore.clickhouse.impl

import idlab.obelisk.definitions.DataRange
import idlab.obelisk.definitions.DataStoreQueryTimeoutException
import idlab.obelisk.definitions.MetricType
import idlab.obelisk.definitions.PagedResult
import idlab.obelisk.definitions.data.*
import idlab.obelisk.definitions.framework.OblxConfig
import idlab.obelisk.plugins.datastore.clickhouse.impl.querywrappers.*
import idlab.obelisk.plugins.datastore.clickhouse.impl.querywrappers.extra.StorageInfoQueryWrapper
import idlab.obelisk.plugins.datastore.clickhouse.impl.utils.CHField
import idlab.obelisk.utils.service.utils.unpage
import io.reactivex.Completable
import io.reactivex.Single
import io.vertx.reactivex.jdbcclient.JDBCPool
import io.vertx.reactivex.sqlclient.Tuple
import ru.yandex.clickhouse.except.ClickHouseException
import java.net.SocketTimeoutException

class CHDataStore(private val jdbcClient: JDBCPool, private val config: OblxConfig) : DataStore {

    override fun getMetadata(query: MetaQuery): Single<PagedResult<MetaData>> =
        MetaQueryWrapper(query).execute(jdbcClient)

    override fun countMetadata(query: MetaQuery): Single<Long> =
        CountMetaQueryWrapper(query).execute(jdbcClient).map { it.items.first() }

    override fun getEvents(query: EventsQuery): Single<PagedResult<MetricEvent>> =
        EventsQueryWrapper(query, config).execute(jdbcClient).onErrorResumeNext { wrapTimeout(it) }

    override fun getLatestEvents(query: EventsQuery): Single<PagedResult<MetricEvent>> =
        LatestEventsQueryWrapper(query, config).execute(jdbcClient).onErrorResumeNext { wrapTimeout(it) }

    override fun getStats(query: StatsQuery): Single<PagedResult<MetricStat>> =
        StatsQueryWrapper(query, config).execute(jdbcClient).onErrorResumeNext { wrapTimeout(it) }

    override fun ingest(events: List<MetricEvent>): Single<List<WriteResult>> {
        val writeSet = events.map { event ->
            try {
                WriteSetEntry(eventToTuple(event), WriteResult.success())
            } catch (t: Throwable) {
                WriteSetEntry(writeResult = WriteResult.failure(t))
            }
        }

        return jdbcClient
            .preparedQuery(insertStatement)
            .rxExecuteBatch(writeSet.mapNotNull { it.tuple })
            .ignoreElement()
            .toSingleDefault(writeSet.map { it.writeResult })
    }

    override fun delete(query: EventsQuery): Completable =
        DeleteEventsQueryWrapper(query, config).executeNoResult(jdbcClient)

    override fun deleteLatest(query: EventsQuery): Completable =
        DeleteLatestEventsQueryWrapper(query, config).executeNoResult(jdbcClient)

    override fun getStorageInfo(compressed: Boolean): Single<StorageInfo> {
        return StorageInfoQueryWrapper(compressed, config.chClusterName).execute(jdbcClient)
            .flatMap { storagePerColumn ->
                val metaFieldSizeBytes =
                    storagePerColumn.items.filterNot { it.first.startsWith("value") }.sumOf { it.second }
                val storagePerTypeMap = storagePerColumn.items.filter { it.first.startsWith("value") }.associate {
                    Pair(
                        when (it.first) {
                            CHField.value_bool.toString() -> MetricType.BOOL
                            CHField.value_number.toString() -> MetricType.NUMBER
                            CHField.value_number_array.toString() -> MetricType.NUMBER_ARRAY
                            CHField.value_string.toString() -> MetricType.STRING
                            else -> throw IllegalArgumentException()
                        }, it.second
                    )
                }

                unpage { cursor ->
                    getMetadata(
                        MetaQuery(
                            dataRange = DataRange.allData,
                            fields = listOf(MetaField.metric_type, MetaField.count),
                            cursor = cursor
                        )
                    )
                }.toList().map { countPerType ->
                    val countPerTypeMap = countPerType.groupBy { it.metricType!! }
                        .mapValues { record -> record.value.sumOf { it.count!! } }
                    StorageInfo(metaFieldSizeBytes, MetricType.values().associate {
                        Pair(
                            it, TypeStorageInfo(
                                countPerTypeMap[it] ?: 0,
                                storagePerTypeMap[if (it == MetricType.JSON) MetricType.STRING else it] ?: 0
                            )
                        )
                    })
                }
            }
    }

    private fun <T> wrapTimeout(err: Throwable): Single<T> {
        return if ((err is ClickHouseException || err is com.clickhouse.client.ClickHouseException) && (err.cause is SocketTimeoutException || (err.message?.contains(
                "Estimated query execution time"
            ) == true))
        ) {
            Single.error(DataStoreQueryTimeoutException())
        } else {
            Single.error(err)
        }
    }
}

private class WriteSetEntry(
    val tuple: Tuple? = null,
    val writeResult: WriteResult
)
