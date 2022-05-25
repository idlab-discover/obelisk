package idlab.obelisk.services.pub.ngsi.impl.endpoints

import idlab.obelisk.definitions.catalog.AccessManager
import idlab.obelisk.services.pub.ngsi.Constants
import idlab.obelisk.services.pub.ngsi.impl.model.NgsiEntity
import idlab.obelisk.services.pub.ngsi.impl.state.EntityOperationResult
import idlab.obelisk.services.pub.ngsi.impl.state.NgsiStore
import idlab.obelisk.services.pub.ngsi.impl.state.WriteContext
import idlab.obelisk.services.pub.ngsi.impl.state.WriteMode
import idlab.obelisk.services.pub.ngsi.impl.utils.*
import idlab.obelisk.utils.service.utils.applyToken
import io.reactivex.rxkotlin.subscribeBy
import io.reactivex.rxkotlin.toFlowable
import io.vertx.core.json.JsonObject
import io.vertx.reactivex.ext.web.RoutingContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * TODO: optimize batch operations -> actual batch implementation instead of relying on individual operations
 */
@Singleton
class BatchOperations @Inject constructor(private val accessManager: AccessManager, private val ngsiStore: NgsiStore) :
    AbstractEndpointsHandler() {

    fun createEntities(ctx: RoutingContext) {
        batchIngestEntitiesHandler(ctx, WriteMode.POST_ENTITY)
    }

    fun upsertEntities(ctx: RoutingContext) {
        batchIngestEntitiesHandler(ctx, WriteMode.UPSERT_ENTITY)
    }

    fun updateEntities(ctx: RoutingContext) {
        batchIngestEntitiesHandler(
            ctx,
            if (NgsiOption.noOverwrite.hasOption(ctx)) WriteMode.POST_ATTRIBUTES_NO_OVERRIDE else WriteMode.POST_ATTRIBUTES_WITH_OVERRIDE
        )
    }

    private fun batchIngestEntitiesHandler(ctx: RoutingContext, writeMode: WriteMode) {
        val datasetId = ctx.pathParam(PATH_PARAM_DATASET_ID)
        getTokenWithWritePermission(accessManager, ctx)
            .flatMap { token ->
                val wc = WriteContext(datasetId, token, ctx, writeMode, true)
                ctx.bodyAsJsonArray.toFlowable().cast(JsonObject::class.java).flatMapSingle { entityJson ->
                    val context =
                        if (entityJson.containsKey(Constants.LD_CONTEXT)) JsonLdUtils.getLDContextFromObject(entityJson) else JsonLdUtils.getLDContextFromLinkHeader(
                            ctx
                        )
                    NgsiEntity.fromJson(entityJson, context, wc.datasetId, wc.getProducer())
                }.toList()
                    .flatMap { ngsiStore.putEntities(wc, it) }
            }
            .subscribeBy(
                onSuccess = NgsiResponses.entityOperationsResult(ctx),
                onError = NgsiResponses.error(ctx)
            )
    }

    fun deleteEntities(ctx: RoutingContext) {
        getTokenWithWritePermission(accessManager, ctx)
            .flatMap { token ->
                val entityIds = ctx.bodyAsJsonArray.map {
                    if (isValidUriLink(it as String)) it else throw BadRequestData(
                        "Invalid URI",
                        "One of the entity IDs is not a valid URI!"
                    )
                }
                val query = batchDeleteQuery(ctx, entityIds).applyToken(token).restrictForDelete(token)
                ngsiStore.removeEntities(query, entityIds)
                    .toSingleDefault(ctx.bodyAsJsonArray.map { EntityOperationResult(it as String) })
            }
            .subscribeBy(
                onSuccess = NgsiResponses.entityOperationsResult(ctx),
                onError = NgsiResponses.error(ctx)
            )
    }

}
