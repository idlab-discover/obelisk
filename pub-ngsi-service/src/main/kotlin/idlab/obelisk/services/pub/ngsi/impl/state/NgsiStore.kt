package idlab.obelisk.services.pub.ngsi.impl.state

import idlab.obelisk.definitions.PagedResult
import idlab.obelisk.definitions.catalog.Token
import idlab.obelisk.definitions.data.EventsQuery
import idlab.obelisk.definitions.data.Producer
import idlab.obelisk.services.pub.ngsi.impl.model.*
import io.reactivex.Completable
import io.reactivex.Single
import io.vertx.core.json.JsonObject
import io.vertx.reactivex.ext.web.RoutingContext

/**
 * Acts as a Gateway between the NGSI-LD API and Obelisk.
 * Entity data posted to this Store are kept in memory (using EntityContext) and can be returned in follow-up queries,
 * while the NgsiStore will also make sure that the converted data is ingested into Obelisk.
 */
interface NgsiStore {

    /**
     * Retrieves the current state of Entities matching the specified query
     */
    fun getEntities(readContext: ReadContext, query: EventsQuery, limit: Int): Single<PagedResult<NgsiEntity>>

    /**
     * Counts the entities matching the specified query (and write the result as an HTTP response header)
     */
    fun countEntities(readContext: ReadContext, query: EventsQuery): Completable

    /**
     * Retrieves the historical state of Entities matching the specified query
     */
    fun getTemporalEvolution(readContext: ReadContext, query: EventsQuery, limit: Int): Single<PagedResult<NgsiEntity>>

    /**
     * Counts the number of historical states for the Entities matching the specified query (and write the result as an HTTP response header)
     */
    fun countTemporalEvolution(readContext: ReadContext, query: EventsQuery): Completable

    /**
     * Updates the state by adding the specified Entity
     */
    fun putEntity(writeContext: WriteContext, entity: NgsiEntity): Single<EntityOperationResult>

    /**
     * Updates the state by adding a batch of Entity instances
     */
    fun putEntities(writeContext: WriteContext, entities: List<NgsiEntity>): Single<List<EntityOperationResult>>

    /**
     * Removes the latest entity or entity attribute state based on the provided query (matches the first entity that is found).
     * The second argument entityId is used to invalidate the broker cache
     */
    fun removeEntity(query: EventsQuery, entityId: String): Completable

    /**
     * Removes the latest state for entities or entity attributes based on the provided query.
     * The second argument entityIds is used to invalidate the broker cache
     */
    fun removeEntities(query: EventsQuery, entityIds: List<String>): Completable

    /**
     * Removes (parts of) the temporal state of the Entity with the specified id.
     * The provided EventsQuery functions as a context for the request.
     * If the entity doesn't match the query, it will not be deleted!
     */
    fun removeTemporalEntity(query: EventsQuery, entityId: String): Completable

    fun getTypes(scope: EventsQuery, details: Boolean = false, jsonLdContext: JsonObject): Single<PagedResult<EntityType>>

    fun getType(scope: EventsQuery, type: String, jsonLdContext: JsonObject): Single<EntityTypeInfo>

    fun getAttributes(scope: EventsQuery, details: Boolean = false, jsonLdContext: JsonObject): Single<PagedResult<Attribute>>

    fun getAttribute(scope: EventsQuery, attrId: String, jsonLdContext: JsonObject): Single<Attribute>

}

data class ReadContext(val token: Token, val httpContext: RoutingContext) {

    fun isEnableCount(): Boolean {
        return httpContext.request().getParam("count")?.toBooleanStrictOrNull() ?: false
    }

}

data class WriteContext(
    val datasetId: String,
    val token: Token,
    val httpContext: RoutingContext,
    val mode: WriteMode = WriteMode.POST_ENTITY,
    val containError: Boolean = false
) {

    fun getProducer(): Producer {
        return Producer(token.user.id!!, token.client?.id)
    }

    fun toReadContext(): ReadContext {
        return ReadContext(token, httpContext)
    }

}

data class EntityOperationResult(val entityId: String, val error: Throwable? = null)

enum class WriteMode {
    POST_ENTITY, UPSERT_ENTITY, POST_ATTRIBUTES_WITH_OVERRIDE, POST_ATTRIBUTES_NO_OVERRIDE, PATCH_ATTRIBUTES, TEMPORAL_POST_ENTITY, TEMPORAL_PATCH_ATTRIBUTES
}
