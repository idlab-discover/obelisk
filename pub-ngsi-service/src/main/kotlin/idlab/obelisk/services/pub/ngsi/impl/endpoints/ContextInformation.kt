package idlab.obelisk.services.pub.ngsi.impl.endpoints

import idlab.obelisk.definitions.PagedResult
import idlab.obelisk.definitions.catalog.AccessManager
import idlab.obelisk.services.pub.ngsi.Constants
import idlab.obelisk.services.pub.ngsi.impl.model.NgsiEntity
import idlab.obelisk.services.pub.ngsi.impl.state.NgsiStore
import idlab.obelisk.services.pub.ngsi.impl.state.ReadContext
import idlab.obelisk.services.pub.ngsi.impl.state.WriteContext
import idlab.obelisk.services.pub.ngsi.impl.state.WriteMode
import idlab.obelisk.services.pub.ngsi.impl.utils.*
import idlab.obelisk.utils.service.utils.applyToken
import io.reactivex.Flowable
import io.reactivex.Single
import io.reactivex.rxkotlin.subscribeBy
import io.vertx.core.json.JsonObject
import io.vertx.reactivex.ext.web.RoutingContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ContextInformation @Inject constructor(
    private val accessManager: AccessManager,
    private val ngsiStore: NgsiStore
) : AbstractEndpointsHandler() {

    fun getEntities(ctx: RoutingContext) {
        val tokenResult = getTokenWithReadPermission(accessManager, ctx).cache()
        tokenResult
            .flatMap { token ->
                val query = entitiesQuery(ctx).applyToken(token)
                val rc = ReadContext(token, ctx)
                ngsiStore.getEntities(rc, query, getLimit(ctx))
                    .flatMap {
                        if (rc.isEnableCount()) ngsiStore.countEntities(rc, query).toSingleDefault(it) else Single.just(
                            it
                        )
                    }
            }
            .flatMap { resultSet ->
                Flowable.fromIterable(resultSet.items)
                    .flatMapSingle { it.compact(ctx, NgsiOption.sysAttrs.hasOption(ctx), getSelectedAttributes(ctx)) }
                    .toList().map { PagedResult(it, resultSet.cursor) }

            }
            .subscribeBy(
                onSuccess = NgsiResponses.entitiesResult(ctx, NgsiOption.keyValues.hasOption(ctx)),
                onError = NgsiResponses.error(ctx)
            )
    }

    fun postEntity(ctx: RoutingContext) {
        val datasetId = ctx.pathParam(PATH_PARAM_DATASET_ID)
        getTokenWithWritePermission(accessManager, ctx)
            .flatMap { token ->
                val wc = WriteContext(datasetId, token, ctx, WriteMode.POST_ENTITY)
                NgsiEntity.fromJson(ctx.bodyAsJson, ctx, wc.getProducer())
                    .flatMap { ngsiStore.putEntity(wc, it) }
            }
            .subscribeBy(
                onSuccess = NgsiResponses.entityCreated(ctx),
                onError = NgsiResponses.error(ctx)
            )
    }

    fun getEntity(ctx: RoutingContext) {
        getTokenWithReadPermission(accessManager, ctx)
            .flatMap { token ->
                val query = entityQuery(ctx).applyToken(token)
                val rc = ReadContext(token, ctx)
                ngsiStore.getEntities(rc, query, 1).flattenAsFlowable { it.items }.singleOrError()
            }
            .flatMap { it.compact(ctx, NgsiOption.sysAttrs.hasOption(ctx), getSelectedAttributes(ctx)) }
            .subscribeBy(
                onSuccess = NgsiResponses.entityResult(ctx, NgsiOption.keyValues.hasOption(ctx)),
                onError = NgsiResponses.error(ctx)
            )
    }

    fun deleteEntity(ctx: RoutingContext) {
        getTokenWithWritePermission(accessManager, ctx)
            .map { token -> entityQuery(ctx).applyToken(token).restrictForDelete(token) }
            .flatMapCompletable { query ->
                ngsiStore.removeEntity(query, ctx.pathParam(PATH_PARAM_ENTITY_ID))
            }
            .subscribeBy(
                onComplete = NgsiResponses.noContent(ctx),
                onError = NgsiResponses.error(ctx)
            )
    }

    fun postEntityAttribute(ctx: RoutingContext) {
        val entityId = ctx.pathParam(PATH_PARAM_ENTITY_ID)
        val datasetId = ctx.pathParam(PATH_PARAM_DATASET_ID)
        val noOverwrite = !NgsiOption.noOverwrite.hasOption(ctx)
        getTokenWithWritePermission(accessManager, ctx)
            .flatMapCompletable { token ->
                val wc = WriteContext(
                    datasetId,
                    token,
                    ctx,
                    if (noOverwrite) WriteMode.POST_ATTRIBUTES_WITH_OVERRIDE else WriteMode.POST_ATTRIBUTES_WITH_OVERRIDE
                )
                val jsonEntity = ctx.bodyAsJson
                jsonEntity.put(Constants.FQ_ENTITY_ID, entityId)
                NgsiEntity.fromJson(jsonEntity, ctx, wc.getProducer())
                    .flatMapCompletable { ngsiStore.putEntity(wc, it).ignoreElement() }

            }
            .subscribeBy(
                onComplete = NgsiResponses.noContent(ctx),
                onError = NgsiResponses.error(ctx)
            )
    }

    fun patchEntityAttribute(ctx: RoutingContext) {
        val entityId = ctx.pathParam(PATH_PARAM_ENTITY_ID)
        val attrId: String? = ctx.pathParam(PATH_PARAM_ATTR_ID)
        val datasetId = ctx.pathParam(PATH_PARAM_DATASET_ID)
        getTokenWithWritePermission(accessManager, ctx)
            .flatMapCompletable { token ->
                val wc = WriteContext(datasetId, token, ctx, WriteMode.PATCH_ATTRIBUTES)
                val jsonEntity = attrId?.let { JsonObject().put(it, ctx.bodyAsJson) } ?: ctx.bodyAsJson
                jsonEntity.put(Constants.FQ_ENTITY_ID, entityId)

                NgsiEntity.fromJson(jsonEntity, ctx, wc.getProducer())
                    .flatMapCompletable { ngsiStore.putEntity(wc, it).ignoreElement() }

            }
            .subscribeBy(
                onComplete = NgsiResponses.noContent(ctx),
                onError = NgsiResponses.error(ctx)
            )
    }

    fun deleteEntityAttribute(ctx: RoutingContext) {
        getTokenWithWritePermission(accessManager, ctx)
            .map { token -> entityQuery(ctx).applyToken(token).restrictForDelete(token) }
            .flatMapCompletable { query ->
                ngsiStore.removeEntity(query, ctx.pathParam(PATH_PARAM_ENTITY_ID))
            }
            .subscribeBy(
                onComplete = NgsiResponses.noContent(ctx),
                onError = NgsiResponses.error(ctx)
            )
    }
}
