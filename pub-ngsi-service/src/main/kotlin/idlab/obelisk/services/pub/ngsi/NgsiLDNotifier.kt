package idlab.obelisk.services.pub.ngsi

import idlab.obelisk.definitions.framework.OblxService
import idlab.obelisk.pulsar.utils.rxAcknowledge
import idlab.obelisk.pulsar.utils.rxSubscribeAsFlowable
import idlab.obelisk.services.pub.ngsi.impl.state.EntityContext
import idlab.obelisk.services.pub.ngsi.impl.model.Notification
import idlab.obelisk.utils.service.reactive.flatMap
import io.reactivex.Completable
import io.reactivex.Single
import io.reactivex.rxkotlin.subscribeBy
import io.reactivex.rxkotlin.toFlowable
import io.vertx.core.json.JsonArray
import io.vertx.core.json.JsonObject
import io.vertx.reactivex.core.Vertx
import io.vertx.reactivex.ext.web.client.WebClient
import mu.KotlinLogging
import org.apache.pulsar.client.api.*
import java.lang.RuntimeException
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.system.exitProcess

const val NOTIFICATION_TIMEOUT_MS = 5000L;

@Singleton
class NgsiLDNotifier @Inject constructor(
    pulsarClient: PulsarClient,
    private val vertx: Vertx,
    private val entityContext: EntityContext
) : OblxService {
    private val logger = KotlinLogging.logger { }

    private val webClient = WebClient.create(vertx)
    private val defaultLDCOntext = JsonObject().put(Constants.LD_CONTEXT, Constants.DEFAULT_LD_CONTEXT_URI)

    private val consumerBuilder = pulsarClient.newConsumer(Schema.JSON(Notification::class.java))
        .subscriptionName(Constants.PULSAR_NOTIFIER_SUBSCRIPTION)
        .subscriptionType(SubscriptionType.Failover)
        .subscriptionInitialPosition(SubscriptionInitialPosition.Latest)
        .topic(Constants.PULSAR_NOTIFICATION_TOPIC)

    override fun start(): Completable {
        consumerBuilder.rxSubscribeAsFlowable()
            .concatMapCompletable { (consumer, message) ->
                createNotificationBody(message)
                    .flatMapCompletable { body ->
                        webClient.postAbs(message.value.endpoint.uri)
                            .timeout(NOTIFICATION_TIMEOUT_MS)
                            .rxSendJsonObject(body)
                            .flatMapCompletable {
                                if (it.statusCode() !in 200..399) {
                                    Completable.error(RuntimeException("Notification endpoint ${message.value.endpoint.uri} for subscription ${message.value.subscriptionId} returned an error statuscode: ${it.statusCode()} (${it.bodyAsString()})"))
                                } else {
                                    Completable.complete()
                                }
                            }
                            .doOnError { err -> logger.warn(err) { "Could not complete NGSI-LD notification!" } }
                            .onErrorComplete()
                    }
                    .flatMap { consumer.rxAcknowledge(message) }
            }
            .subscribeBy(
                onError = {
                    logger.error(it) { "A fatal error occurred in the NGSI Notification flow! Shutting down..." }
                    exitProcess(1)
                }
            )

        return Completable.complete()
    }

    private fun createNotificationBody(message: Message<Notification>): Single<JsonObject> {
        val notif = message.value
        return notif.entityIds.toFlowable()
            .flatMapMaybe { entityId -> entityContext.getOrLoad(notif.datasetId, entityId) }
            .flatMapSingle { entity -> entity.compact(defaultLDCOntext, true, notif.attributes ?: listOf()) }
            .toList()
            .map { entities ->
                JsonObject()
                    .put("id", "${Constants.OBLX_NOTIFICATION_URN_BASE}${message.sequenceId}")
                    .put("type", "Notification")
                    .put("subscriptionId", notif.subscriptionId)
                    .put("notifiedAt", Instant.now().atOffset(ZoneOffset.UTC).format(DateTimeFormatter.ISO_DATE_TIME))
                    .put("data", JsonArray(entities))
            }
    }
}