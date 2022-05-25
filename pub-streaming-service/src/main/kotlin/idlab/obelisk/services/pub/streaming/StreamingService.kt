package idlab.obelisk.services.pub.streaming

import idlab.obelisk.definitions.catalog.*
import idlab.obelisk.definitions.control.ControlChannels
import idlab.obelisk.definitions.control.DataStreamEvent
import idlab.obelisk.definitions.framework.OblxConfig
import idlab.obelisk.definitions.framework.OblxService
import idlab.obelisk.plugins.accessmanager.basic.BasicAccessManagerModule
import idlab.obelisk.plugins.metastore.mongo.MongoDBMetaStoreModule
import idlab.obelisk.pulsar.utils.PulsarModule
import idlab.obelisk.pulsar.utils.rxAcknowledge
import idlab.obelisk.pulsar.utils.rxSubscribeAsFlowable
import idlab.obelisk.utils.service.OblxBaseModule
import idlab.obelisk.utils.service.OblxLauncher
import idlab.obelisk.utils.service.http.AuthorizationException
import idlab.obelisk.utils.service.http.HttpError
import idlab.obelisk.utils.service.http.writeHttpError
import idlab.obelisk.utils.service.instrumentation.IdToNameMap
import idlab.obelisk.utils.service.instrumentation.TagTemplate
import idlab.obelisk.utils.service.instrumentation.TargetType
import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MultiGauge
import io.reactivex.Completable
import io.reactivex.rxkotlin.subscribeBy
import io.vertx.core.http.HttpHeaders
import io.vertx.core.json.Json
import io.vertx.micrometer.backends.BackendRegistries
import io.vertx.reactivex.core.Vertx
import io.vertx.reactivex.ext.web.Router
import mu.KotlinLogging
import org.apache.pulsar.client.api.PulsarClient
import org.apache.pulsar.client.api.Schema
import org.apache.pulsar.client.api.SubscriptionInitialPosition
import org.apache.pulsar.client.api.SubscriptionType
import org.redisson.api.RedissonClient
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton

const val STREAM_ID_PARAM = "streamId"
const val DEFAULT_SSE_ENDPOINT = "/data/streams"
const val DATA_STREAM_CONTROL_CONSUMER = "pub-streaming-service"

fun main(args: Array<String>) {
    OblxLauncher.with(
        OblxBaseModule(),
        PulsarModule(),
        BasicAccessManagerModule(),
        MongoDBMetaStoreModule()
    ).bootstrap(StreamingService::class.java)
}

@Singleton
class StreamingService @Inject constructor(
    private val vertx: Vertx,
    private val router: Router,
    private val config: OblxConfig,
    private val metaStore: MetaStore,
    private val accessManager: AccessManager,
    private val pulsarClient: PulsarClient,
    private val redissonClient: RedissonClient
) : OblxService {
    companion object {

        val microMeterRegistry = BackendRegistries.getDefaultNow()!!

        // Number of active datastreams (a Pulsar consumer is actively writing events to a HTTP Response stream)
        val sseActiveStreamsGlobal = microMeterRegistry.gauge("oblx.sse.global.active.streams", AtomicInteger(0))!!
        val sseActiveStreamsPerDataset = ActiveStreamTracker(microMeterRegistry)
        val sseAckFailures = Counter
            .builder("oblx.sse.ack.failures")
            .description("Counts number of times Pulsar acks resulted in a failure.")
            .register(microMeterRegistry)!!
    }

    private val logger = KotlinLogging.logger { }
    private val datasetIdToNameMap = IdToNameMap(metaStore, TargetType.DATASET)

    private val streamCoordConsumerBuilder = pulsarClient.newConsumer(Schema.JSON(DataStreamEvent::class.java))
        .subscriptionName(DATA_STREAM_CONTROL_CONSUMER)
        .subscriptionType(SubscriptionType.Shared)
        .subscriptionInitialPosition(SubscriptionInitialPosition.Latest)
        .topic(ControlChannels.DATA_STREAM_EVENTS_TOPIC)

    override fun start(): Completable {
        val basePath = config.getString(OblxConfig.HTTP_BASE_PATH_PROP, DEFAULT_SSE_ENDPOINT)

        router.get("$basePath/open/:$STREAM_ID_PARAM").handler { ctx ->
            val streamId = ctx.pathParam(STREAM_ID_PARAM)
            metaStore.getDataStream(streamId).map { dataStream ->
                AltStreamingSession(pulsarClient, dataStream, ctx, config, datasetIdToNameMap, redissonClient)
            }.toSingle().subscribeBy(
                onSuccess = { it.start() },
                onError = writeHttpError(ctx)
            )
        }

        router.get("$basePath/:$STREAM_ID_PARAM").handler { ctx ->
            val accept = ctx.request().getHeader(HttpHeaders.ACCEPT)
            if (accept == null || !accept.contains("text/event-stream")) {
                HttpError(
                    406,
                    "To start an SSE stream, you must specify 'Accept: text/event-stream' as a request header."
                ).writeResponse(ctx)
            } else {
                val streamId = ctx.pathParam(STREAM_ID_PARAM)
                accessManager.getToken(ctx.request())
                    .flatMap { token ->
                        metaStore.getDataStream(streamId).toSingle().map { dataStream ->
                            checkAccess(token, dataStream)
                            AltStreamingSession(
                                pulsarClient,
                                dataStream,
                                ctx,
                                config,
                                datasetIdToNameMap,
                                redissonClient
                            )
                        }
                    }
                    .subscribeBy(
                        onSuccess = { it.start() },
                        onError = writeHttpError(ctx)
                    )
            }
        }

        // This Pulsar consumer flow listens for events on the specified control topic and then uses the Vert.x EventBus to broadcast the message to all streams active for this instance.
        streamCoordConsumerBuilder.rxSubscribeAsFlowable()
            .flatMapCompletable { (consumer, event) ->
                vertx.eventBus().publish(ControlChannels.DATA_STREAM_EVENTS_TOPIC, Json.encode(event.value))
                consumer.rxAcknowledge(event)
            }
            .subscribeBy(onError = { logger.error(it) { "Something went wrong while receiving DataStream control events form ${ControlChannels.DATA_STREAM_EVENTS_TOPIC}!" } })

        return datasetIdToNameMap.init()
    }

    private fun checkAccess(token: Token, dataStream: DataStream) {
        if (token.user.id != dataStream.userId && (dataStream.teamId == null || token.user.teamMemberships.none { it.teamId == dataStream.teamId })) {
            throw AuthorizationException()
        }

        if (token.client != null && !token.client!!.scope.contains(Permission.READ)) {
            throw AuthorizationException("Client is not authorized to stream (requires READ in scope!)")
        }

        // TODO: is dit nodig?
        for (datasetId in dataStream.dataRange.datasets) {
            val grant = token.grants[datasetId]
            if (!token.user.platformManager && grant?.permissions?.contains(Permission.READ) != true) {
                throw AuthorizationException("No permission to access dataset $datasetId")
            }
        }
    }

}