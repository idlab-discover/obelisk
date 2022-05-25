package idlab.obelisk.utils.service.instrumentation

import idlab.obelisk.definitions.catalog.MetaStore
import idlab.obelisk.utils.service.utils.unpage
import io.reactivex.Completable
import io.reactivex.Flowable
import mu.KotlinLogging
import java.util.concurrent.TimeUnit

enum class TargetType(val initializer: (metaStore: MetaStore) -> Flowable<IdToName>) {
    DATASET({ metaStore ->
        unpage { cursor ->
            metaStore.queryDatasets(
                filter = idlab.obelisk.definitions.SELECT_ALL,
                cursor = cursor
            )
        }.map { IdToName(it.id!!, it.name) }
    });
}

data class IdToName(val id: String, val name: String)

private const val updateIntervalMs = 24 * 60 * 60 * 1000L // DAILY

class IdToNameMap(
    private val metaStore: MetaStore,
    private val targetType: TargetType
) {

    private val logger = KotlinLogging.logger { }
    private val updateFlow = Flowable.interval(updateIntervalMs, TimeUnit.MILLISECONDS)
        .onBackpressureDrop()
        .flatMap { targetType.initializer.invoke(metaStore) }
        .doOnNext { mappings[it.id] = it.name }
        .ignoreElements()
        .onErrorComplete {
            logger.warn { "An error occurred while updating the id->name mapping for $targetType" }
            true
        }

    private val mappings = mutableMapOf<String, String>()

    fun init(): Completable {
        return targetType.initializer.invoke(metaStore)
            .doOnNext { mappings[it.id] = it.name }
            .ignoreElements()
            .doOnComplete {
                updateFlow.subscribe()
            }
    }

    fun getName(id: String): String? {
        return mappings[id]
    }

}