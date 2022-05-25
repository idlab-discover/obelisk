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
import io.vertx.reactivex.ext.web.RoutingContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TemporalEvolution @Inject constructor(
    private val accessManager: AccessManager,
    private val ngsiStore: NgsiStore
) : AbstractEndpointsHandler() {

    fun getEntities(ctx: RoutingContext) {
        getTokenWithReadPermission(accessManager, ctx)
            .flatMap { token ->
                val query = entitiesQuery(ctx, true).applyToken(token)
                val rc = ReadContext(token, ctx)
                ngsiStore.getTemporalEvolution(rc, query, getLimit(ctx))
                    .flatMap {
                        if (rc.isEnableCount()) ngsiStore.countTemporalEvolution(rc, query)
                            .toSingleDefault(it) else Single.just(
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

    fun postTemporalEntity(ctx: RoutingContext) {
        val datasetId = ctx.pathParam(PATH_PARAM_DATASET_ID)
        getTokenWithWritePermission(accessManager, ctx)
            .flatMap { token ->
                val wc = WriteContext(datasetId, token, ctx, WriteMode.TEMPORAL_POST_ENTITY)
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
                ngsiStore.getTemporalEvolution(rc, query, 1).flattenAsFlowable { it.items }.singleOrError()
            }
            .flatMap { it.compact(ctx, NgsiOption.sysAttrs.hasOption(ctx), getSelectedAttributes(ctx)) }
            .subscribeBy(
                onSuccess = NgsiResponses.entityResult(ctx, NgsiOption.keyValues.hasOption(ctx)),
                onError = NgsiResponses.error(ctx)
            )
    }

    fun deleteTemporalEntity(ctx: RoutingContext) {
        getTokenWithWritePermission(accessManager, ctx)
            .map { token -> entityQuery(ctx).applyToken(token).restrictForDelete(token) }
            .flatMapCompletable { query ->
                ngsiStore.removeTemporalEntity(query, ctx.pathParam(PATH_PARAM_ENTITY_ID))
            }
            .subscribeBy(
                onComplete = NgsiResponses.noContent(ctx),
                onError = NgsiResponses.error(ctx)
            )
    }

    fun patchTemporalAttribute(ctx: RoutingContext) {
        val entityId = ctx.pathParam(PATH_PARAM_ENTITY_ID)
        val datasetId = ctx.pathParam(PATH_PARAM_DATASET_ID)
        getTokenWithWritePermission(accessManager, ctx)
            .flatMapCompletable { token ->
                val wc = WriteContext(datasetId, token, ctx, WriteMode.TEMPORAL_PATCH_ATTRIBUTES)
                val jsonEntity = ctx.bodyAsJson
                if (!jsonEntity.containsKey(Constants.FQ_ENTITY_ID)) {
                    jsonEntity.put(Constants.FQ_ENTITY_ID, entityId)
                }
                NgsiEntity.fromJson(ctx.bodyAsJson, ctx, wc.getProducer())
                    .flatMap { ngsiStore.putEntity(wc, it) }
                    .ignoreElement()
            }
            .subscribeBy(
                onComplete = NgsiResponses.noContent(ctx),
                onError = NgsiResponses.error(ctx)
            )
    }

}
