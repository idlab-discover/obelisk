package idlab.obelisk.services.pub.ngsi

import idlab.obelisk.definitions.framework.OblxService
import idlab.obelisk.definitions.messaging.Message
import idlab.obelisk.definitions.messaging.MessageBroker
import idlab.obelisk.definitions.messaging.MessagingMode
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
import kotlinx.coroutines.rx2.asFlowable
import kotlinx.coroutines.rx2.rxCompletable
import mu.KotlinLogging
import java.lang.RuntimeException
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.system.exitProcess

const val NOTIFICATION_TIMEOUT_MS = 5000L

@Singleton
class NgsiLDNotifier @Inject constructor(
    vertx: Vertx,
    private val entityContext: EntityContext,
    private val messageBroker: MessageBroker
) : OblxService {
    private val logger = KotlinLogging.logger { }

    private val webClient = WebClient.create(vertx)
    private val defaultLDCOntext = JsonObject().put(Constants.LD_CONTEXT, Constants.DEFAULT_LD_CONTEXT_URI)

    override fun start(): Completable = rxCompletable {
        val consumer = messageBroker.createConsumer(
            topicName = Constants.PULSAR_NOTIFICATION_TOPIC,
            subscriptionName = Constants.PULSAR_NOTIFIER_SUBSCRIPTION,
            contentType = Notification::class,
            mode = MessagingMode.STREAMING
        )

        consumer.receive().asFlowable()
            .concatMapCompletable { message ->
                createNotificationBody(message)
                    .flatMapCompletable { body ->
                        webClient.postAbs(message.content.endpoint.uri)
                            .timeout(NOTIFICATION_TIMEOUT_MS)
                            .rxSendJsonObject(body)
                            .flatMapCompletable {
                                if (it.statusCode() !in 200..399) {
                                    Completable.error(RuntimeException("Notification endpoint ${message.content.endpoint.uri} for subscription ${message.content.subscriptionId} returned an error statuscode: ${it.statusCode()} (${it.bodyAsString()})"))
                                } else {
                                    Completable.complete()
                                }
                            }
                            .doOnError { err -> logger.warn(err) { "Could not complete NGSI-LD notification!" } }
                            .onErrorComplete()
                    }
                    .flatMap { rxCompletable { consumer.acknowledge(message.messageId) } }
            }
            .subscribeBy(
                onError = {
                    logger.error(it) { "A fatal error occurred in the NGSI Notification flow! Shutting down..." }
                    exitProcess(1)
                }
            )
    }

    private fun createNotificationBody(message: Message<Notification>): Single<JsonObject> {
        val notification = message.content
        return notification.entityIds.toFlowable()
            .flatMapMaybe { entityId -> entityContext.getOrLoad(notification.datasetId, entityId) }
            .flatMapSingle { entity -> entity.compact(defaultLDCOntext, true, notification.attributes ?: listOf()) }
            .toList()
            .map { entities ->
                JsonObject()
                    .put("id", "${Constants.OBLX_NOTIFICATION_URN_BASE}${message.messageId}")
                    .put("type", "Notification")
                    .put("subscriptionId", notification.subscriptionId)
                    .put("notifiedAt", Instant.now().atOffset(ZoneOffset.UTC).format(DateTimeFormatter.ISO_DATE_TIME))
                    .put("data", JsonArray(entities))
            }
    }
}
