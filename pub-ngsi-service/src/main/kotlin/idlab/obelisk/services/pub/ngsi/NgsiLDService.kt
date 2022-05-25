package idlab.obelisk.services.pub.ngsi

import idlab.obelisk.definitions.framework.OblxConfig
import idlab.obelisk.definitions.framework.OblxService
import idlab.obelisk.services.pub.ngsi.impl.endpoints.BatchOperations
import idlab.obelisk.services.pub.ngsi.impl.endpoints.ContextInformation
import idlab.obelisk.services.pub.ngsi.impl.endpoints.ContextSubscription
import idlab.obelisk.services.pub.ngsi.impl.endpoints.Metadata
import idlab.obelisk.services.pub.ngsi.impl.endpoints.TemporalEvolution
import idlab.obelisk.services.pub.ngsi.impl.utils.NgsiResponses
import idlab.obelisk.utils.service.http.patchWithBody
import idlab.obelisk.utils.service.http.postWithBody
import io.reactivex.Completable
import io.vertx.reactivex.core.Vertx
import io.vertx.reactivex.ext.web.Route
import io.vertx.reactivex.ext.web.Router
import io.vertx.reactivex.ext.web.handler.LoggerHandler
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NgsiLDService @Inject constructor(
    val config: OblxConfig,
    val vertx: Vertx,
    val router: Router,
    private val contextInformation: ContextInformation,
    private val temporalEvolution: TemporalEvolution,
    private val batchOperations: BatchOperations,
    private val contextSubscription: ContextSubscription,
    private val metadata: Metadata
) : OblxService {

    // Maar een klein beetje kotsen...
    companion object {
        var basePath: String = ""
        var baseUri: String = ""
        var vertx: Vertx? = null
    }

    override fun start(): Completable {
        basePath = "${config.getString(OblxConfig.HTTP_BASE_PATH_PROP, "/ext/ngsi")}/:datasetId/ngsi-ld/v1"
        baseUri = config.authPublicUri
        NgsiLDService.vertx = vertx

        router.route().handler(LoggerHandler.create())

        // Context Information Endpoints
        val contextInfoPath = "$basePath/entities/"
        router.get(contextInfoPath).producesJsonLd().handler(contextInformation::getEntities)
        router.get(contextInfoPath.trimEnd('/')).producesJsonLd()
            .handler(contextInformation::getEntities) // Alias for /entities/
        router.postWithBody(contextInfoPath).handler(contextInformation::postEntity)
        router.get("$contextInfoPath:entityId").producesJsonLd().handler(contextInformation::getEntity)
        router.delete("$contextInfoPath:entityId").handler(contextInformation::deleteEntity)
        router.postWithBody("$contextInfoPath:entityId/attrs/").handler(contextInformation::postEntityAttribute)
        router.patchWithBody("$contextInfoPath:entityId/attrs/").handler(contextInformation::patchEntityAttribute)
        router.patchWithBody("$contextInfoPath:entityId/attrs/:attrId")
            .handler(contextInformation::patchEntityAttribute)
        router.delete("$contextInfoPath:entityId/attrs/:attrId").handler(contextInformation::deleteEntityAttribute)

        router.get("$basePath/subscriptions/").producesJsonLd().handler(contextSubscription::getSubscriptions)
        router.get("$basePath/subscriptions").producesJsonLd()
            .handler(contextSubscription::getSubscriptions) // Alias for /subscriptions/
        router.postWithBody("$basePath/subscriptions/").handler(contextSubscription::postSubscription)
        router.get("$basePath/subscriptions/:subscriptionId").producesJsonLd()
            .handler(contextSubscription::getSubscription)
        router.patchWithBody("$basePath/subscriptions/:subscriptionId").handler(contextSubscription::patchSubscription)
        router.delete("$basePath/subscriptions/:subscriptionId").handler(contextSubscription::deleteSubscription)

        // Context Sources Endpoints
        router.route("$basePath/*csource*").handler { ctx ->
            NgsiResponses.writeError(
                ctx,
                UnsupportedOperationException("Obelisk does not support NGSI-LD Context Source operations!")
            )
        }

        // Batch Operation Endpoints
        router.postWithBody("$basePath/entityOperations/create").handler(batchOperations::createEntities)
        router.postWithBody("$basePath/entityOperations/update").handler(batchOperations::updateEntities)
        router.postWithBody("$basePath/entityOperations/upsert").handler(batchOperations::upsertEntities)
        router.postWithBody("$basePath/entityOperations/delete").handler(batchOperations::deleteEntities)

        // Temporal Evolution Endpoints
        router.get("$basePath/temporal/entities/").producesJsonLd().handler(temporalEvolution::getEntities)
        router.get("$basePath/temporal/entities").producesJsonLd()
            .handler(temporalEvolution::getEntities) // Alias for /entities/
        router.postWithBody("$basePath/temporal/entities/").handler(temporalEvolution::postTemporalEntity)
        router.get("$basePath/temporal/entities/:entityId").producesJsonLd().handler(temporalEvolution::getEntity)
        // Delete any attribute (and all its instances) found for the specified Entity (produced in this dataset, by the requesting user)
        router.delete("$basePath/temporal/entities/:entityId").handler(temporalEvolution::deleteTemporalEntity)
        router.postWithBody("$basePath/temporal/entities/:entityId/attrs/")
            .handler(temporalEvolution::postTemporalEntity)
        // Delete all instances of the specified attribute for the specified Entity (produced in this dataset, by the requesting user)
        router.delete("$basePath/temporal/entities/:entityId/attrs/:attrId")
            .handler(temporalEvolution::deleteTemporalEntity)
        router.patchWithBody("$basePath/temporal/entities/:entityId/attrs/:attrId/:instanceId")
            .handler(temporalEvolution::patchTemporalAttribute)
        // Delete the specified attribute instance for the the specified Entity and Attribute (produced in this dataset, by the requesting user)
        router.delete("$basePath/temporal/entities/:entityId/attrs/:attrId/:instanceId")
            .handler(temporalEvolution::deleteTemporalEntity)

        router.get("$basePath/types/").producesJsonLd().handler(metadata::getTypes)
        router.get("$basePath/types").producesJsonLd().handler(metadata::getTypes)
        router.get("$basePath/types/:type").producesJsonLd().handler(metadata::getType)
        router.get("$basePath/attributes/").producesJsonLd().handler(metadata::getAttributes)
        router.get("$basePath/attributes").producesJsonLd().handler(metadata::getAttributes)
        router.get("$basePath/attributes/:attrId").producesJsonLd().handler(metadata::getAttribute)

        return Completable.complete()
    }
}

internal fun Route.producesJsonLd(): Route {
    return this.produces("application/json").produces(Constants.JSON_LD_CONTENT_TYPE)
}
