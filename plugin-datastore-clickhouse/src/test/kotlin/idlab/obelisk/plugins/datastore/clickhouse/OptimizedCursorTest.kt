package idlab.obelisk.plugins.datastore.clickhouse

import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import idlab.obelisk.definitions.DataRange
import idlab.obelisk.definitions.MetricName
import idlab.obelisk.definitions.data.EventsQuery
import idlab.obelisk.definitions.framework.OblxConfig
import idlab.obelisk.utils.service.reactive.flatMap
import idlab.obelisk.utils.service.utils.unpage
import idlab.obelisk.utils.test.Generator
import idlab.obelisk.utils.test.MergedData
import idlab.obelisk.utils.test.rg.Time
import io.reactivex.Completable
import io.reactivex.rxkotlin.subscribeBy
import io.reactivex.rxkotlin.toObservable
import io.vertx.core.json.jackson.DatabindCodec
import io.vertx.reactivex.core.Vertx

fun main() {
    DatabindCodec.mapper().registerKotlinModule()
    DatabindCodec.prettyMapper().registerKotlinModule()
    val datastore = ClickhouseDataStoreModule().dataStore(Vertx.vertx(), OblxConfig())

    val populate = false
    val data = MergedData((0..9).map {
        Generator.events(
            "test-ds-oc",
            Time.Range(1609455600000, 1625004000000),
            MetricName("test_rg_double::number"),
            1000000
        )
    })

    val targetTimeRange = Time.Range(1624140000000, Long.MAX_VALUE)

    fun generateData(): Completable {
        return if (populate) {
            data.events().toObservable()
                .buffer(5000)
                .concatMapCompletable { datastore.ingest(it).ignoreElement() }
        } else {
            Completable.complete()
        }
    }

    // 1. Store data
    generateData()
        .flatMap {
            // 2. Query time range using paging
            val start = System.currentTimeMillis()
            unpage { cursor ->
                datastore.getEvents(
                    EventsQuery(
                        dataRange = DataRange(data.datasets().toList()),
                        from = targetTimeRange.fromMs,
                        limit = 25000,
                        cursor = cursor
                    )
                )
            }.toList().doOnSuccess { results ->
                println("Query took ${System.currentTimeMillis() - start} ms")
                println("Resultsize: ${results.size}")
            }.ignoreElement()
        }
        .subscribeBy(
            onError = { it.printStackTrace() }
        )
}
