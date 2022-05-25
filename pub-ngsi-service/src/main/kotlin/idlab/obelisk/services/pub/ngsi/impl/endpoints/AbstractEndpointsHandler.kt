package idlab.obelisk.services.pub.ngsi.impl.endpoints

import idlab.obelisk.definitions.catalog.*
import idlab.obelisk.services.pub.ngsi.impl.utils.JsonLdUtils
import idlab.obelisk.services.pub.ngsi.impl.utils.NotAuthorized
import io.reactivex.Single
import io.vertx.reactivex.ext.web.RoutingContext

abstract class AbstractEndpointsHandler {

    companion object {
        const val PATH_PARAM_DATASET_ID = "datasetId"
        const val PATH_PARAM_ENTITY_ID = "entityId"
        const val PATH_PARAM_ATTR_ID = "attrId"
        const val PATH_PARAM_SUBSCRIPTION_ID = "subscriptionId"
    }

    protected fun getTokenWithWritePermission(accessManager: AccessManager, ctx: RoutingContext): Single<Token> {
        val datasetId = ctx.pathParam(PATH_PARAM_DATASET_ID)
        return accessManager.getToken(ctx.request()).flatMap { token ->
            if (token.user.platformManager || token.grants[datasetId]?.permissions?.contains(Permission.WRITE) == true) {
                Single.just(token)
            } else {
                Single.error { NotAuthorized("No write permissions on Dataset with id $datasetId!") }
            }
        }
    }

    protected fun getTokenWithReadPermission(accessManager: AccessManager, ctx: RoutingContext): Single<Token> {
            val datasetId = ctx.pathParam(PATH_PARAM_DATASET_ID)
            return accessManager.getToken(ctx.request()).flatMap { token ->
                if (token.user.platformManager || token.grants[datasetId]?.permissions?.contains(Permission.READ) == true) {
                    Single.just(token)
                } else {
                    Single.error { NotAuthorized("No read permissions on Dataset with id $datasetId!") }
                }
            }
    }

    protected fun getSelectedAttributes(ctx: RoutingContext): List<String> {
        return ctx.request().getParam("attrs")?.split(",")?.map { JsonLdUtils.getFQAttribute(it, ctx) } ?: emptyList()
    }

}