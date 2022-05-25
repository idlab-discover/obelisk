import idlab.obelisk.definitions.PagedResult
import idlab.obelisk.utils.service.utils.pageAndProcess
import idlab.obelisk.utils.service.utils.unpage
import io.reactivex.Completable
import io.reactivex.Flowable
import io.reactivex.Single
import io.reactivex.rxkotlin.subscribeBy
import io.vertx.reactivex.core.Vertx
import mu.KotlinLogging
import java.util.concurrent.TimeUnit
import kotlin.random.Random
import kotlin.random.nextInt
import kotlin.system.exitProcess

const val pageSize = 25000
const val fetchSize = pageSize * 100L

val logger = KotlinLogging.logger {}

fun main() {
    val vertx = Vertx.vertx()
    vertx.createHttpServer().requestHandler { }.listen(8080)

    unpage { getBogusPage(it) }
        .take(fetchSize)
        .buffer(5000)
        .concatMapCompletable { writeBogusBuffer(it) }
        .subscribeBy(
            onComplete = { exitProcess(0) },
            onError = {
                it.printStackTrace()
                exitProcess(1)
            }
        )

    /*pageAndProcess(
        limit = fetchSize.toInt(),
        executor = { getBogusPage(it) },
        processor = { page ->
            Flowable.fromIterable(page.items).buffer(5000).concatMapCompletable { writeBogusBuffer(it) }
        }
    ).subscribeBy(
        onComplete = { exitProcess(0) },
        onError = {
            it.printStackTrace()
            exitProcess(1)
        }
    )*/
}

data class SomeRecord(val timestamp: Long, val metaProps: Map<String, String>, val value: Any?)

fun getBogusPage(cursor: String? = null): Single<PagedResult<SomeRecord>> {
    return Single.just(PagedResult((0 until pageSize).map {
        SomeRecord(
            System.currentTimeMillis(),
            mapOf(
                "source" to "source${Random.nextInt()}",
                "env" to "env${Random.nextInt()}"
            ),
            Random.nextDouble()
        )
    }, cursor?.toInt()?.inc()?.toString() ?: "1"))
        .delay(Random.nextInt(2..5).toLong(), TimeUnit.MILLISECONDS)
        .doOnSuccess { logger.warn { "Downloaded page ${it.cursor} (Memory usage: ${getMemUsage()} bytes)" } }
}

fun writeBogusBuffer(buffer: List<SomeRecord>): Completable {
    logger.warn { "Writing next ${buffer.size} items [${buffer.firstOrNull()?.timestamp}, ${buffer.lastOrNull()?.timestamp}] (Memory usage: ${getMemUsage()} bytes)" }
    return Completable.complete().delay(Random.nextInt(0, 2).toLong(), TimeUnit.MILLISECONDS)
}

fun getMemUsage(): Long {
    return Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()
}