package idlab.obelisk.services.pub.ngsi.impl.endpoints

import idlab.obelisk.definitions.And
import idlab.obelisk.definitions.Eq
import idlab.obelisk.definitions.Field
import idlab.obelisk.definitions.catalog.AccessManager
import idlab.obelisk.definitions.catalog.DataStream
import idlab.obelisk.definitions.catalog.MetaStore
import idlab.obelisk.definitions.catalog.Token
import idlab.obelisk.definitions.catalog.codegen.DataStreamField
import idlab.obelisk.definitions.control.ControlChannels
import idlab.obelisk.definitions.control.DataStreamEvent
import idlab.obelisk.definitions.control.DataStreamEventType
import idlab.obelisk.definitions.framework.OblxConfig
import idlab.obelisk.pulsar.utils.rxSend
import idlab.obelisk.services.pub.ngsi.Constants
import idlab.obelisk.services.pub.ngsi.impl.model.Notification
import idlab.obelisk.services.pub.ngsi.impl.model.Subscription
import idlab.obelisk.services.pub.ngsi.impl.state.NgsiStore
import idlab.obelisk.services.pub.ngsi.impl.state.ReadContext
import idlab.obelisk.services.pub.ngsi.impl.utils.*
import idlab.obelisk.utils.service.reactive.flatMap
import io.reactivex.Single
import io.reactivex.rxkotlin.subscribeBy
import io.reactivex.rxkotlin.zipWith
import io.vertx.core.json.JsonArray
import io.vertx.core.json.JsonObject
import io.vertx.reactivex.core.Vertx
import io.vertx.reactivex.ext.web.RoutingContext
import org.apache.pulsar.client.api.PulsarClient
import org.apache.pulsar.client.api.Schema
import javax.inject.Inject
import javax.inject.Singleton

const val SENSIBLE_NOTIFICATION_LIMIT = 5000

@Singleton
class ContextSubscription @Inject constructor(
    private val vertx: Vertx,
    private val accessManager: AccessManager,
    private val metaStore: MetaStore,
    pulsarClient: PulsarClient,
    private val ngsiStore: NgsiStore,
    config: OblxConfig
) : AbstractEndpointsHandler() {

    private val dataStreamChanges = pulsarClient.newProducer(Schema.JSON(DataStreamEvent::class.java))
        .producerName("${config.hostname()}_ngsi").topic(ControlChannels.DATA_STREAM_EVENTS_TOPIC)
        .blockIfQueueFull(true).create()
    private val notificationProducer =
        pulsarClient.newProducer(Schema.JSON(Notification::class.java)).producerName("${config.hostname()}_api")
            .topic(Constants.PULSAR_NOTIFICATION_TOPIC).blockIfQueueFull(true).create()

    fun postSubscription(ctx: RoutingContext) {
        val datasetId = ctx.pathParam(PATH_PARAM_DATASET_ID)
        getTokenWithWritePermission(accessManager, ctx)
            .zipWith(parseSubscriptionJson(ctx)) { t, j -> Pair(t, j) }
            .flatMap { (token, json) ->
                val subscription = json.mapTo(Subscription::class.java)
                val oblxStream = subscription.getDataStream(datasetId, token, JsonLdUtils.getLDContext(ctx))
                metaStore.createDataStream(oblxStream)
                    .flatMapCompletable {
                        // Notify the creation of the new Datastream (triggers the subscription matching process)
                        dataStreamChanges.rxSend(DataStreamEvent(DataStreamEventType.INIT, it)).ignoreElement()
                    }
                    .flatMap {
                        val rc = ReadContext(token, ctx)
                        // Send the initial notification (by looking up entities and creating a notification)
                        ngsiStore.getEntities(rc, oblxStream.toQuery(), SENSIBLE_NOTIFICATION_LIMIT)
                            .map { entities ->
                                Notification(
                                    subscriptionId = subscription.id,
                                    datasetId = datasetId,
                                    entityIds = entities.items.map { it.id!! },
                                    attributes = subscription.notification.attributes,
                                    endpoint = subscription.notification.endpoint
                                )
                            }
                            .flatMapCompletable { notificationProducer.rxSend(it).ignoreElement() }
                    }
                    .toSingleDefault(subscription.id)
            }
            .subscribeBy(
                onSuccess = NgsiResponses.subscriptionCreated(ctx),
                onError = NgsiResponses.error(ctx)
            )
    }

    fun getSubscriptions(ctx: RoutingContext) {
        getTokenWithReadPermission(accessManager, ctx)
            .flatMap { token ->
                metaStore.queryDataStreams(Eq(DataStreamField.USER_ID, token.user.id!!))
                    .flattenAsFlowable { it.items }
                    .map { Subscription.fromDataStream(it) }
                    .toList()
            }
            .subscribeBy(
                onSuccess = NgsiResponses.subscriptionResults(ctx),
                onError = NgsiResponses.error(ctx)
            )
    }

    fun getSubscription(ctx: RoutingContext) {
        val subscriptionId = ctx.pathParam(PATH_PARAM_SUBSCRIPTION_ID)
        getTokenWithReadPermission(accessManager, ctx)
            .flatMap { token ->
                getDataStreamBySubscriptionId(token, subscriptionId)
                    .map { Subscription.fromDataStream(it) }
            }
            .subscribeBy(
                onSuccess = NgsiResponses.subscriptionResult(ctx),
                onError = NgsiResponses.error(ctx)
            )
    }

    fun patchSubscription(ctx: RoutingContext) {
        val datasetId = ctx.pathParam(PATH_PARAM_DATASET_ID)
        val subscriptionId = ctx.pathParam(PATH_PARAM_SUBSCRIPTION_ID)
        getTokenWithWritePermission(accessManager, ctx)
            .zipWith(parseSubscriptionJson(ctx)) { t, j -> Pair(t, j) }
            .flatMapCompletable { (token, json) ->
                getDataStreamBySubscriptionId(token, subscriptionId)
                    .flatMap { ds ->
                        val newState = JsonObject.mapFrom(Subscription.fromDataStream(ds)).mergeIn(json)
                            .mapTo(Subscription::class.java)
                        metaStore.createDataStream(
                            newState.getDataStream(
                                datasetId,
                                token,
                                JsonLdUtils.getLDContext(ctx)
                            )
                        )
                    }.ignoreElement()
            }
            .subscribeBy(
                onComplete = NgsiResponses.noContent(ctx),
                onError = NgsiResponses.error(ctx)
            )
    }

    fun deleteSubscription(ctx: RoutingContext) {
        val subscriptionId = ctx.pathParam(PATH_PARAM_SUBSCRIPTION_ID)
        getTokenWithWritePermission(accessManager, ctx)
            .flatMapCompletable { token ->
                getDataStreamBySubscriptionId(token, subscriptionId)
                    .flatMapCompletable { metaStore.removeDataStream(it.id!!) }
            }
            .subscribeBy(
                onComplete = NgsiResponses.noContent(ctx),
                onError = NgsiResponses.error(ctx)
            )
    }


    private fun getDataStreamBySubscriptionId(token: Token, subscriptionId: String): Single<DataStream> {
        return metaStore.queryDataStreams(
            filter = And(
                Eq(DataStreamField.USER_ID, token.user.id!!),
                Eq(Field(DataStreamField.PROPERTIES.toString(), "id"), subscriptionId)
            )
        )
            .map {
                it.items.firstOrNull()
                    ?: throw ResourceNotFound(
                        "Subscription not found!",
                        "Subscription with id $subscriptionId could not be found!"
                    )
            }
    }

    /**
     * The subscription body is expanded using the user provided context.
     * Then we compact the expanded form using only the default NGSI context.
     * This way we can easily deserialize the JSON into a Subscription instance,
     * while retaining the fully qualified references to e.g. entity types, attributes, ...
     */
    private fun parseSubscriptionJson(ctx: RoutingContext): Single<JsonObject> {
        return JsonLdUtils.expandJsonLDEntity(ctx.bodyAsJson, JsonLdUtils.getLDContext(ctx))
            .flatMap { expandedForm -> JsonLdUtils.compactJsonLDEntity(expandedForm, Constants.DEFAULT_LD_CONTEXT) }
            .map { json ->
                expandPropToArray("entities", json)
                expandPropToArray("watchedAttributes", json)
                expandPropToArray("attributes", json.getJsonObject("notification"))
                json
            }
    }

    private fun expandPropToArray(prop: String, json: JsonObject) {
        val propVal = json.getValue(prop)
        if (propVal != null && propVal !is JsonArray) {
            json.put(prop, JsonArray().add(propVal))
        }
    }
}