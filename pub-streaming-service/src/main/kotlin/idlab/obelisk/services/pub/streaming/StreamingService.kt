package idlab.obelisk.services.pub.streaming

import idlab.obelisk.definitions.catalog.*
import idlab.obelisk.definitions.framework.OblxConfig
import idlab.obelisk.definitions.framework.OblxService
import idlab.obelisk.definitions.messaging.MessageBroker
import idlab.obelisk.plugins.accessmanager.basic.BasicAccessManagerModule
import idlab.obelisk.plugins.metastore.mongo.MongoDBMetaStoreModule
import idlab.obelisk.pulsar.utils.PulsarModule
import idlab.obelisk.utils.service.OblxBaseModule
import idlab.obelisk.utils.service.OblxLauncher
import idlab.obelisk.utils.service.http.AuthorizationException
import idlab.obelisk.utils.service.http.HttpError
import idlab.obelisk.utils.service.http.writeHttpError
import idlab.obelisk.utils.service.instrumentation.IdToNameMap
import idlab.obelisk.utils.service.instrumentation.TargetType
import io.micrometer.core.instrument.Counter
import io.reactivex.Completable
import io.reactivex.rxkotlin.subscribeBy
import io.vertx.core.http.HttpHeaders
import io.vertx.micrometer.backends.BackendRegistries
import io.vertx.reactivex.ext.web.Router
import kotlinx.coroutines.rx2.rxCompletable
import org.apache.pulsar.client.api.PulsarClient
import org.redisson.api.RedissonClient
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton

const val STREAM_ID_PARAM = "streamId"
const val DEFAULT_SSE_ENDPOINT = "/data/streams"

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
    private val router: Router,
    private val config: OblxConfig,
    private val metaStore: MetaStore,
    private val accessManager: AccessManager,
    private val messageBroker: MessageBroker,
    private val redissonClient: RedissonClient
) : OblxService {
    companion object {

        val microMeterRegistry = BackendRegistries.getDefaultNow()!!

        // Number of active datastreams (a Pulsar consumer is actively writing events to a HTTP Response stream)
        val sseActiveStreamsGlobal = microMeterRegistry.gauge("oblx.sse.global.active.streams", AtomicInteger(0))!!
        val sseActiveStreamsPerDataset = ActiveStreamTracker(microMeterRegistry)
        val sseAckFailures: Counter = Counter
            .builder("oblx.sse.ack.failures")
            .description("Counts number of times Pulsar acks resulted in a failure.")
            .register(microMeterRegistry)
    }

    private val datasetIdToNameMap = IdToNameMap(metaStore, TargetType.DATASET)

    override fun start(): Completable {
        val basePath = config.getString(OblxConfig.HTTP_BASE_PATH_PROP, DEFAULT_SSE_ENDPOINT)

        router.get("$basePath/open/:$STREAM_ID_PARAM").handler { ctx ->
            val streamId = ctx.pathParam(STREAM_ID_PARAM)
            metaStore.getDataStream(streamId).map { dataStream ->
                StreamingSession(messageBroker, dataStream, ctx, config, datasetIdToNameMap, redissonClient)
            }.toSingle().flatMapCompletable { session ->
                rxCompletable {
                    session.start()
                }
            }.subscribeBy(
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
                            StreamingSession(
                                messageBroker,
                                dataStream,
                                ctx,
                                config,
                                datasetIdToNameMap,
                                redissonClient
                            )
                        }
                    }
                    .flatMapCompletable { session ->
                        rxCompletable {
                            session.start()
                        }
                    }
                    .subscribeBy(
                        onError = writeHttpError(ctx)
                    )
            }
        }
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
