package idlab.obelisk.services.pub.ngsi.impl.state

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import idlab.obelisk.definitions.*
import idlab.obelisk.definitions.data.DataStore
import idlab.obelisk.definitions.data.EventsQuery
import idlab.obelisk.definitions.data.IndexField
import idlab.obelisk.definitions.data.OrderBy
import idlab.obelisk.services.pub.ngsi.impl.model.NgsiEntity
import idlab.obelisk.utils.service.utils.matches
import io.reactivex.Completable
import io.reactivex.Flowable
import io.reactivex.Maybe

private const val MAX_CACHED_ENTITIES_PER_DATASET = 10000L

/**
 * Simple in-memory implementation. Later to be replaced with e.g. Redis to allow multi-instanced deployments of NGSI service
 */
class SimpleEntityContext(private val dataStore: DataStore) : EntityContext {

    // Main data structure: maps datasetIds to the respective entity caches for those datasets.
    private val caches = mutableMapOf<String, Cache<String, NgsiEntity>>()

    override fun getOrLoad(datasetId: String, entityId: String): Maybe<NgsiEntity> {
        return forDataset(datasetId).getIfPresent(entityId)
                ?.let { Maybe.just(it) }
                ?: dataStore.getLatestEvents(
                        EventsQuery(
                                dataRange = DataRange(datasets = listOf(datasetId), metrics = MetricName.wildcard()),
                                fields = listOf(EventField.dataset, EventField.metric, EventField.value, EventField.source, EventField.tags, EventField.tsReceived, EventField.location, EventField.producer),
                                filter = Eq(EventField.source.toString(), entityId),
                                orderBy = OrderBy(listOf(IndexField.source, IndexField.timestamp))
                        )
                )
                        .flatMapMaybe { result ->
                            if (result.items.isEmpty()) {
                                Maybe.empty()
                            } else {
                                Maybe.just(NgsiEntity.fromEvents(result.items))
                            }
                        }

    }

    override fun findInCache(query: EventsQuery, limit: Int): Flowable<NgsiEntity> {
        val result = forDataset(query.dataRange.datasets.first()).asMap().values
                .sortedBy { it.id }
                .map { entity -> entity.toEvents().filter { it.matches(query.filter) }.let { NgsiEntity.fromEvents(it) } }
                .filterNot { it.attributes.isEmpty() }
                .take(limit)
        return Flowable.fromIterable(result)
    }

    override fun cache(entity: NgsiEntity): Completable {
        forDataset(entity.datasetId!!).put(entity.id!!, entity)
        return Completable.complete()
    }

    override fun invalidate(datasetId: String, vararg entityIds: String): Completable {
        forDataset(datasetId).invalidateAll(entityIds.toList())
        return Completable.complete()
    }

    private fun forDataset(datasetId: String): Cache<String, NgsiEntity> {
        return caches.computeIfAbsent(datasetId) {
            Caffeine.newBuilder().maximumSize(MAX_CACHED_ENTITIES_PER_DATASET).build()
        }
    }

}