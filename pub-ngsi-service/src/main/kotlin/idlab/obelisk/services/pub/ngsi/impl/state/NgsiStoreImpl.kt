package idlab.obelisk.services.pub.ngsi.impl.state

import com.github.davidmoten.rx2.flowable.Transformers
import idlab.obelisk.definitions.*
import idlab.obelisk.definitions.catalog.Token
import idlab.obelisk.definitions.catalog.UsageLimitId
import idlab.obelisk.definitions.data.*
import idlab.obelisk.definitions.framework.OblxConfig
import idlab.obelisk.definitions.messaging.MessageBroker
import idlab.obelisk.definitions.messaging.MessageProducer
import idlab.obelisk.definitions.messaging.ProducerMode
import idlab.obelisk.definitions.ratelimiting.RateLimiter
import idlab.obelisk.plugins.datastore.clickhouse.impl.utils.OptimizedCursor
import idlab.obelisk.services.pub.ngsi.Constants
import idlab.obelisk.services.pub.ngsi.impl.model.Attribute
import idlab.obelisk.services.pub.ngsi.impl.model.EntityType
import idlab.obelisk.services.pub.ngsi.impl.model.EntityTypeInfo
import idlab.obelisk.services.pub.ngsi.impl.model.NgsiEntity
import idlab.obelisk.services.pub.ngsi.impl.utils.*
import idlab.obelisk.utils.service.reactive.flatMap
import idlab.obelisk.utils.service.reactive.onErrorLogAndComplete
import idlab.obelisk.utils.service.utils.Base64.encodeAsBase64
import idlab.obelisk.utils.service.utils.applyToken
import idlab.obelisk.utils.service.utils.unpage
import io.reactivex.Completable
import io.reactivex.Flowable
import io.reactivex.Maybe
import io.reactivex.Single
import io.reactivex.rxkotlin.toFlowable
import io.reactivex.rxkotlin.zipWith
import io.vertx.core.json.JsonObject
import kotlinx.coroutines.rx2.rxCompletable
import mu.KotlinLogging
import java.lang.Integer.min

class NgsiStoreImpl(
    private val config: OblxConfig,
    private val entityContext: EntityContext,
    private val dataStore: DataStore,
    private val rateLimiter: RateLimiter,
    private val messageBroker: MessageBroker,
) : NgsiStore {

    private val logger = KotlinLogging.logger { }

    override fun getEntities(
        readContext: ReadContext,
        query: EventsQuery,
        limit: Int
    ): Single<PagedResult<NgsiEntity>> {
        return rateLimiter.apply(readContext.httpContext, readContext.token, UsageLimitId.maxHourlyComplexEventQueries)
            .flatMapPublisher {
                unpage { cursor ->
                    dataStore.getLatestEvents(query.copy(cursor = cursor))
                }
            }
            .compose(Transformers.toListWhile { current, event ->
                current.isEmpty() || current.last().source == event.source
            })
            .limit(limit.toLong() + 1) // Limit DB results (+1, because paging needs look ahead to work)
            .map { NgsiEntity.fromEvents(it) }
            .filter { it.attributes.isNotEmpty() }
            .toList()
            .zipWith(entityContext.findInCache(query, limit + 1).toList()) { dbResults, cacheResults ->
                val results = dbResults
                    // Remove duplicates (results from cache always hold the most recent data)
                    .filter { entity -> cacheResults.none { it.id == entity.id } }
                    .union(cacheResults) // Combine results
                    .sortedBy { it.id!! } // Sort by entity ID
                // Limit results again (as the merge could result in more items again)
                val limitedResults = results.take(limit)

                // Use latest entity ID (source) as cursor, continue next query after this source (if necessary)
                PagedResult(
                    limitedResults,
                    if (results.size > limit) limitedResults.last().id?.encodeAsBase64() else null
                )
            }
    }

    override fun countEntities(readContext: ReadContext, query: EventsQuery): Completable {
        // Count distinct entities in DataStore + recent entries in the cache (with a grace period of 5 seconds)
        return entityContext.findInCache(query.copy(from = System.currentTimeMillis() - 5000), Int.MAX_VALUE).count()
            .zipWith(dataStore.getStats(
                StatsQuery(
                    dataRange = query.dataRange,
                    fields = listOf(StatsField.countSources),
                    from = query.from,
                    to = query.to,
                    filter = query.filter
                )
            )
                .map { stats ->
                    stats.items.firstOrNull()?.count ?: 0L
                }) { nrOfRecentEntitiesInCache, nrOfEntitiesInStorage -> nrOfEntitiesInStorage + nrOfRecentEntitiesInCache }
            .doOnSuccess { count ->
                readContext.httpContext.response().putHeader(Constants.HTTP_HEADER_RESULT_COUNT, count.toString())
            }
            .ignoreElement()
            .onErrorLogAndComplete(logger)
    }

    override fun getTemporalEvolution(
        readContext: ReadContext,
        query: EventsQuery,
        limit: Int
    ): Single<PagedResult<NgsiEntity>> {
        val recordLimit = min(
            Constants.MAX_LIMIT,
            readContext.httpContext.request().getParam("recordLimit")?.toInt()
                ?: Constants.MAX_LIMIT
        )
        // TODO: Merge historical buffer cached results with DB results (cfr. getEntities with entityStore)
        // TODO: This implementation can be optimized by using a smaller page and checking how many sources are already detected! (in function of the limit param)
        return rateLimiter.apply(readContext.httpContext, readContext.token, UsageLimitId.maxHourlyComplexEventQueries)
            .flatMap {
                val lastN = getLastN(readContext.httpContext)
                if (lastN != null) {
                    unpage { cursor -> dataStore.getEvents(query.copy(cursor = cursor)) }
                        .compose(Transformers.toListWhile { current, event ->
                            current.isEmpty() || current.last().source == event.source
                        })
                        .take(limit.toLong() + 1) // Limit DB results (+1, because paging needs look ahead to work)
                        .toList()
                        .map { results ->
                            val mappedResults =
                                results.map { NgsiEntity.fromEvents(it, temporal = true) }.take(limit + 1)
                            if (mappedResults.size > limit) {
                                PagedResult(
                                    mappedResults.dropLast(1),
                                    mappedResults.lastOrNull()?.let { lastEntity ->
                                        // lastN results in a order by source, timestamp (desc)
                                        OptimizedCursor(listOf(lastEntity.id, lastEntity.getLastUpdateTS()))
                                    }?.encode()
                                )
                            } else {
                                PagedResult(mappedResults)
                            }
                        }
                } else {
                    dataStore.getEvents(
                        query.copy(
                            limit = recordLimit,
                            cursor = readContext.httpContext.request().getParam("cursor")
                        )
                    )
                        .map { results ->
                            val entityLimit = getTemporalEntityLimit(readContext.httpContext)
                            val sources = mutableSetOf<String>()
                            val limitedResults = results.items
                                .takeWhile {
                                    if (entityLimit != null) {
                                        val result = sources.size <= entityLimit
                                        sources.add(it.source!!)
                                        result
                                    } else {
                                        true
                                    }
                                }
                                .groupBy { it.source!! }
                                .map { groupedBySource ->
                                    NgsiEntity.fromEvents(
                                        groupedBySource.value,
                                        temporal = true
                                    )
                                }
                            if (entityLimit != null && limitedResults.size > entityLimit) {
                                PagedResult(
                                    limitedResults.dropLast(1),
                                    limitedResults.lastOrNull()?.let { lastEntity ->
                                        // Normal temporal query results in a order by timestamp, source
                                        OptimizedCursor(listOf(lastEntity.getOldestUpdateTS(), lastEntity.id))
                                    }?.encode()
                                )
                            } else {
                                PagedResult(limitedResults, results.cursor)
                            }
                        }
                }
            }
    }

    override fun countTemporalEvolution(readContext: ReadContext, query: EventsQuery): Completable {
        return dataStore.getStats(
            StatsQuery(
                dataRange = query.dataRange,
                fields = listOf(StatsField.count),
                from = query.from,
                to = query.to,
                filter = query.filter
            )
        )
            .map { stats ->
                stats.items.firstOrNull()?.count ?: 0L
            }
            .zipWith(dataStore.getStats(
                StatsQuery(
                    dataRange = query.dataRange,
                    fields = listOf(StatsField.countSources),
                    from = query.from,
                    to = query.to,
                    filter = query.filter
                )
            ).map { stats ->
                stats.items.firstOrNull()?.countSources ?: 0L
            }) { attrInstanceCount, entityCount ->
                readContext.httpContext.response()
                    .putHeader(Constants.HTTP_HEADER_ATTRIBUTES_COUNT, attrInstanceCount.toString())
                    .putHeader(Constants.HTTP_HEADER_RESULT_COUNT, entityCount.toString())
            }
            .ignoreElement()
            .onErrorLogAndComplete(logger)
    }

    override fun putEntity(writeContext: WriteContext, entity: NgsiEntity): Single<EntityOperationResult> {
        val insertFlow = when (writeContext.mode) {
            WriteMode.POST_ENTITY -> newEntityState(writeContext, entity)
            WriteMode.UPSERT_ENTITY -> upsertEntityState(writeContext, entity)
            WriteMode.PATCH_ATTRIBUTES, WriteMode.POST_ATTRIBUTES_NO_OVERRIDE, WriteMode.POST_ATTRIBUTES_WITH_OVERRIDE -> patchAttributes(
                writeContext,
                entity
            )
            WriteMode.TEMPORAL_POST_ENTITY -> addHistoricalEntityState(writeContext, entity)
            WriteMode.TEMPORAL_PATCH_ATTRIBUTES -> patchHistoricalAttributes(writeContext, entity)
        }.toSingle { EntityOperationResult(entity.id!!) }
        val eventsToWrite = entity.toEvents(false).size
        return rateLimiter.apply(
            writeContext.httpContext,
            writeContext.token,
            mapOf(
                UsageLimitId.maxHourlyComplexEventsStreamed to eventsToWrite,
                UsageLimitId.maxHourlyComplexEventsStored to eventsToWrite
            )
        )
            .flatMap {
                if (writeContext.containError) insertFlow.onErrorResumeNext { err ->
                    Single.just(
                        EntityOperationResult(
                            entity.id!!,
                            err
                        )
                    )
                } else insertFlow
            }
    }

    // For now only implemented optimization for batch create new entities
    override fun putEntities(
        writeContext: WriteContext,
        entities: List<NgsiEntity>
    ): Single<List<EntityOperationResult>> {
        return if (writeContext.mode == WriteMode.POST_ENTITY) {
            newEntitiesState(writeContext, entities)
        } else {
            entities.toFlowable().flatMapSingle { putEntity(writeContext, it) }.toList()
        }
    }

    override fun removeEntity(query: EventsQuery, entityId: String): Completable {
        return entityContext.getOrLoad(query.dataRange.datasets.first(), entityId).isEmpty()
            .flatMapCompletable { noMatch ->
                if (!noMatch) {
                    dataStore.deleteLatest(query)
                        .flatMap { entityContext.invalidate(query.dataRange.datasets.first(), entityId) }
                } else {
                    Completable.error(
                        ResourceNotFound(
                            "Not Found",
                            "The parameters for the delete don't match any data."
                        )
                    )
                }
            }
    }

    override fun removeEntities(query: EventsQuery, entityIds: List<String>): Completable {
        // Check if the entityCache or the DB has at least one result for the query...
        return hasItems(
            dataStore.getLatestEvents(query.copy(limit = 1)).flatMapPublisher { it.items.toFlowable() },
            entityContext.findInCache(query, 1)
        ).flatMapCompletable { matchExists ->
            if (matchExists) {
                // If so, continue with deleting entities using the query
                dataStore.deleteLatest(query)
                    .flatMap { entityContext.invalidate(query.dataRange.datasets.first(), *entityIds.toTypedArray()) }
            } else {
                Completable.error(ResourceNotFound("Not Found", "The parameters for the delete don't match any data."))
            }
        }
    }

    override fun removeTemporalEntity(query: EventsQuery, entityId: String): Completable {
        // Check if the historical buffer or the DB has at least one result for the query...
        return hasItems(
            dataStore.getEvents(query.copy(limit = 1)).flatMapPublisher { it.items.toFlowable() }//,
            //historicalBuffer.get(query, 1)
        ).flatMapCompletable { matchExists ->
            if (matchExists) {
                // If so, continue with deleting entities using the query
                dataStore.delete(query)
                //.flatMap { historicalBuffer.remove(query) }
            } else {
                Completable.error(ResourceNotFound("Not Found", "The parameters for the delete don't match any data."))
            }
        }
    }

    override fun getTypes(
        scope: EventsQuery,
        details: Boolean,
        jsonLdContext: JsonObject
    ): Single<PagedResult<EntityType>> {
        val fields = if (details) listOf(EventField.tags, EventField.metric) else listOf(EventField.tags)
        val indexFields = if (details) listOf(IndexField.tags, IndexField.metric) else listOf(IndexField.tags)
        val q = scope.copy(
            fields = fields,
            limitBy = LimitBy(fields = indexFields, limit = 1),
            orderBy = OrderBy(fields = indexFields)
        )
        return dataStore.getEvents(q)
            .map { results ->
                PagedResult(
                    items = if (details) {
                        results.items.groupBy { event -> event.tags ?: emptyList() }
                            .map { entry ->
                                val id = getTagValueByPrefix(entry.key, Constants.TYPE_TAG_PREFIX)
                                    ?: Constants.OBLX_GENERIC_TYPE_URN
                                EntityType(
                                    id = id,
                                    typeName = JsonLdUtils.compactFQType(id, jsonLdContext),
                                    attributeNames = entry.value.map {
                                        ParsedMetric.from(it.metric!!).getPropertyName()
                                    }
                                )
                            }
                    } else {
                        results.items.map { event ->
                            EntityType(
                                id = getTagValueByPrefix(
                                    event.tags ?: emptyList(),
                                    Constants.TYPE_TAG_PREFIX
                                ) ?: Constants.OBLX_GENERIC_TYPE_URN
                            )
                        }
                    },
                    cursor = results.cursor
                )
            }
    }

    override fun getType(scope: EventsQuery, type: String, jsonLdContext: JsonObject): Single<EntityTypeInfo> {
        val fqType = JsonLdUtils.getFQType(type, jsonLdContext)
        // 1. Get entityCount
        val entityCountQuery = StatsQuery(
            dataRange = scope.dataRange,
            fields = listOf(StatsField.countSources),
            filter = ngsiTypeFilter(listOf(type), jsonLdContext)
        )
        return dataStore.getStats(entityCountQuery)
            .flatMap { stats ->
                // 2. Find which attributes are available for the type
                val matchingAttrsQuery = scope.copy(
                    fields = listOf(EventField.metric),
                    limitBy = LimitBy(fields = listOf(IndexField.metric), limit = 1),
                    orderBy = OrderBy(fields = listOf(IndexField.metric)),
                    limit = Constants.DEFAULT_LIMIT,
                    cursor = null
                )
                // 3. Get attr details per matching attribute
                unpage { cursor -> dataStore.getEvents(matchingAttrsQuery.copy(cursor = cursor)) }
                    .flatMapSingle { attrEvent ->
                        getAttribute(scope, ParsedMetric.from(attrEvent.metric!!).getPropertyName(), jsonLdContext)
                    }
                    .toList()
                    .map { attrs ->
                        EntityTypeInfo(
                            id = fqType,
                            typeName = type,
                            entityCount = stats.items.firstOrNull()?.countSources ?: 0,
                            attributeDetails = attrs
                        )
                    }
            }
    }

    override fun getAttributes(
        scope: EventsQuery,
        details: Boolean,
        jsonLdContext: JsonObject
    ): Single<PagedResult<Attribute>> {
        val fields = if (details) listOf(EventField.metric, EventField.tags) else listOf(EventField.metric)
        val indexFields = if (details) listOf(IndexField.metric, IndexField.tags) else listOf(IndexField.metric)
        val q = scope.copy(
            fields = fields,
            limitBy = LimitBy(fields = indexFields, limit = 1),
            orderBy = OrderBy(fields = indexFields)
        )
        return dataStore.getEvents(q)
            .map { results ->
                PagedResult(
                    items = if (details) {
                        results.items.groupBy { event -> event.metric }
                            .map { entry ->
                                val id = ParsedMetric.from(entry.key!!).getPropertyName()
                                Attribute(
                                    id = id,
                                    attributeName = JsonLdUtils.compactFQAttribute(id, jsonLdContext),
                                    typeNames = entry.value.map {
                                        getTagValueByPrefix(it.tags ?: emptyList(), Constants.TYPE_TAG_PREFIX)
                                            ?: Constants.OBLX_GENERIC_TYPE_URN
                                    }
                                )
                            }
                    } else {
                        results.items.map { event ->
                            val id = ParsedMetric.from(event.metric!!).getPropertyName()
                            Attribute(
                                id = id,
                                attributeName = id
                            )
                        }
                    },
                    cursor = results.cursor
                )
            }
    }

    override fun getAttribute(scope: EventsQuery, attrId: String, jsonLdContext: JsonObject): Single<Attribute> {
        val fqAttrId = JsonLdUtils.getFQAttribute(attrId, jsonLdContext)
        // 1. Get attribute count
        val q = StatsQuery(
            dataRange = scope.dataRange,
            fields = listOf(StatsField.count),
            filter = StartsWith(StatsField.metric, fqAttrId)
        )
        return dataStore.getStats(q)
            .flatMap { stats ->
                // 2. Find which types use the attribute
                val matchingTypes = scope.copy(
                    fields = listOf(EventField.tags),
                    limitBy = LimitBy(listOf(IndexField.tags), 1),
                    orderBy = OrderBy(listOf(IndexField.tags)),
                    filter = StartsWith(StatsField.metric, fqAttrId),
                    limit = Constants.DEFAULT_LIMIT
                )
                unpage { cursor -> dataStore.getEvents(matchingTypes.copy(cursor = cursor)) }
                    .map {
                        getTagValueByPrefix(it.tags ?: emptyList(), Constants.TYPE_TAG_PREFIX)
                            ?: Constants.OBLX_GENERIC_TYPE_URN
                    }
                    .toList()
                    .map { types ->
                        Attribute(
                            id = fqAttrId,
                            attributeName = attrId,
                            attributeCount = stats.items.firstOrNull()?.count,
                            typeNames = types
                        )
                    }
            }
    }

    private fun errorIfExists(datasetId: String, entityId: String): Completable {
        return entityContext.getOrLoad(datasetId, entityId)
            .flatMap {
                // If entityContext returns a result, we throw an AlreadyExists exception
                Maybe.error<NgsiEntity> { AlreadyExists("Entity with id $entityId already exists!") }
            }
            .ignoreElement() // If no result was returned, we can continue using the provided entity
    }

    private fun hasItems(vararg publishers: Flowable<*>): Single<Boolean> {
        return Flowable.mergeArray(*publishers).isEmpty().map { !it }
    }

    private fun newEntityState(writeContext: WriteContext, entity: NgsiEntity): Completable {
        return errorIfExists(writeContext.datasetId, entity.id!!)
            .flatMap {
                entity.toEvents().toFlowable()
                    .flatMapCompletable {
                        rxCompletable {
                            // Persist to Obelisk...
                            loadEventProducer().send(it)
                        }
                    }
                    .flatMap { entityContext.cache(entity) }
            }
    }

    private fun newEntitiesState(
        writeContext: WriteContext,
        entities: List<NgsiEntity>
    ): Single<List<EntityOperationResult>> {
        val entityIds = entities.mapNotNull { it.id }.toSet()
        val q = EventsQuery(
            dataRange = DataRange.fromDatasetId(writeContext.datasetId),
            filter = In(EventField.source.toString(), entityIds)
        )
        return entityContext.findInCache(q, entityIds.size)
            .toList()
            .flatMap { existingEntities ->
                val existingEntityIds = existingEntities.mapNotNull { it.id }.toSet()
                entities.toFlowable()
                    .flatMapSingle { entity ->
                        if (existingEntityIds.contains(entity.id)) {
                            Single.just(
                                EntityOperationResult(
                                    entity.id!!,
                                    AlreadyExists("Entity with id ${entity.id} already exists!")
                                )
                            )
                        } else {
                            entity.toEvents().toFlowable()
                                .flatMapCompletable {
                                    rxCompletable {
                                        // Persist to Obelisk...
                                        loadEventProducer().send(it)
                                    }
                                }
                                .flatMap { entityContext.cache(entity) }
                                .toSingleDefault(EntityOperationResult(entity.id!!))
                        }
                    }
                    .toList()
            }
    }

    private fun upsertEntityState(writeContext: WriteContext, entity: NgsiEntity): Completable {
        return entityContext.getOrLoad(writeContext.datasetId, entity.id!!)
            .switchIfEmpty(
                entityContext.cache(entity).toSingleDefault(entity)
            ) // If no previous state, put the input entity as state
            .flatMap { previousState ->
                // Update previous state in entityContext if present
                entity.attributes.forEach { (attrName, attrVal) -> previousState.setAttribute(attrName, attrVal) }
                entityContext.cache(previousState).toSingleDefault(entity)
            }
            .flatMapCompletable {
                // Persist input entity converted events into Obelisk
                entity.toEvents().toFlowable()
                    .flatMapCompletable {
                        rxCompletable {
                            // Persist to Obelisk...
                            loadEventProducer().send(it)
                        }
                    }
                    .flatMap { entityContext.cache(entity) }
            }
    }

    private fun patchAttributes(writeContext: WriteContext, entity: NgsiEntity): Completable {
        // Get current Entity state to proceed processing the patch
        return entityContext.getOrLoad(writeContext.datasetId, entity.id!!)
            .switchIfEmpty(Single.error(BadRequestData("Entity with id ${entity.id} does not exists!")))
            .flatMapCompletable { previousState ->
                entity.attributes.entries.toFlowable()
                    .filter {
                        when (writeContext.mode) {
                            // Post with override -> always continue with setting the attribute
                            WriteMode.POST_ATTRIBUTES_WITH_OVERRIDE -> true
                            // Post without override -> only continue if there is no previous state for the attribute
                            WriteMode.POST_ATTRIBUTES_NO_OVERRIDE -> !previousState.attributes.containsKey(it.key)
                            // Patch -> only continue if there was a previous state
                            WriteMode.PATCH_ATTRIBUTES -> previousState.attributes.containsKey(it.key)
                            else -> throw IllegalArgumentException("Invalid write mode ${writeContext.mode}")
                        }
                    }
                    .doOnNext {
                        previousState.setAttribute(it.key, it.value)
                    }
                    .flatMapCompletable {
                        previousState.toEvents().toFlowable()
                            .flatMapCompletable {
                                rxCompletable {
                                    // Persist to Obelisk...
                                    loadEventProducer().send(it)
                                }
                            }
                            .flatMap { entityContext.cache(previousState) } //And then also update entityContext cache
                    }
            }
    }

    private fun addHistoricalEntityState(writeContext: WriteContext, entity: NgsiEntity): Completable {
        return entity.toEvents().toFlowable()
            .flatMapCompletable {
                rxCompletable {
                    // Persist to Obelisk...
                    loadEventProducer().send(it)
                }
            }
    }

    /**
     * NOTES:
     * Ok, historical patching is special in our implementation...
     * You can only patch historical attributes that were produced with the same account (makes sense -> no rewriting of history by other parties)
     * However:
     * - We need to produce clear exceptions when this happens
     * - What is the relation with 'addHistoricalEntityState'? Because that operations doesn't do any checking (so duplicates can occur, when users would expect rewrites...)
     */
    private fun patchHistoricalAttributes(writeContext: WriteContext, entity: NgsiEntity): Completable {
        // Get historical Entity state matching the instanceId ect.. to proceed processing the patch
        return getTemporalEvolution(
            writeContext.toReadContext(),
            entityQuery(writeContext.httpContext).applyToken(writeContext.token).restrictForUpdate(writeContext.token),
            1
        )
            .flattenAsFlowable { it.items }
            .singleOrError()
            .flatMapCompletable { previousState ->
                entity.attributes.entries.toFlowable()
                    .filter { previousState.attributes.containsKey(it.key) } // Only continue if there was a previous state
                    .doOnNext {
                        previousState.setAttribute(it.key, it.value)
                    }
                    .flatMapCompletable {
                        previousState.toEvents().toFlowable()
                            .flatMapCompletable {
                                rxCompletable {
                                    // Persist to Obelisk...
                                    loadEventProducer().send(it)
                                }
                            }
                            .flatMap { entityContext.cache(previousState) } //And then also update entityContext cache
                    }
            }
    }

    private suspend fun loadEventProducer(): MessageProducer<MetricEvent> {
        return messageBroker.getProducer(
            topicName = config.pulsarMetricEventsTopic,
            contentType = MetricEvent::class,
            senderName = "${config.hostname()}_ngsi",
            mode = ProducerMode.HIGH_THROUGHPUT
        )
    }
}

internal fun EventsQuery.restrictForUpdate(token: Token): EventsQuery {
    /**
     * Make sure only entities that were created through NGSI can be deleted (JSON type, entityType tag)
     * If the user created these his/herself!!
     */
    return this.copy(
        dataRange = DataRange(this.dataRange.datasets, listOf(MetricName("*", MetricType.JSON))),
        filter = And(
            this.filter,
            Eq("${EventField.producer}.userId", token.user.id!!),
            HasTag(Constants.NGSI_MARKER_TAG)
        )
    )
}
