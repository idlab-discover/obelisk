package idlab.obelisk.services.pub.streaming

import com.github.davidmoten.rx2.RetryWhen
import com.github.davidmoten.rx2.flowable.Transformers
import idlab.obelisk.definitions.EventField
import idlab.obelisk.definitions.catalog.DataStream
import idlab.obelisk.definitions.data.MetricEvent
import idlab.obelisk.definitions.framework.OblxConfig
import idlab.obelisk.pulsar.utils.rxAcknowledgeCumulative
import idlab.obelisk.pulsar.utils.rxSeekToLatest
import idlab.obelisk.pulsar.utils.rxSubscribe
import idlab.obelisk.pulsar.utils.toFlowable
import idlab.obelisk.utils.service.instrumentation.IdToNameMap
import idlab.obelisk.utils.service.instrumentation.TagTemplate
import idlab.obelisk.utils.service.reactive.flatMap
import idlab.obelisk.utils.service.streaming.StreamingSessions
import idlab.obelisk.utils.service.utils.matches
import io.reactivex.BackpressureStrategy
import io.reactivex.Completable
import io.reactivex.Flowable
import io.reactivex.rxkotlin.subscribeBy
import io.vertx.core.json.JsonObject
import io.vertx.reactivex.ext.web.RoutingContext
import mu.KotlinLogging
import org.apache.pulsar.client.api.*
import org.redisson.api.RedissonClient
import java.nio.channels.ClosedChannelException
import java.util.concurrent.TimeUnit

private const val RECEIVE_BACKLOG_QUERY_PARAM = "receiveBacklog"
private const val COMMIT_PERIOD_MS = 2000L
private val datasetIdAndNameTag = TagTemplate("datasetId", "datasetName")

class AltStreamingSession(
    private val pulsarClient: PulsarClient,
    private val metadata: DataStream,
    private val routingContext: RoutingContext,
    private val config: OblxConfig,
    private val datasetIdToNameMap: IdToNameMap,
    redissonClient: RedissonClient
) {
    private val logger = KotlinLogging.logger { }
    private val streamingSessions = StreamingSessions(redissonClient)
    private var sessionId: String? = null
    private var activeConsumer: Consumer<MetricEvent>? = null


    fun start() {

        prepareHttpResponse()
        sendPreamble()

        streamingSessions.start(metadata.id!!)
            .doOnSuccess {
                sessionId = it
                logger.info { "[$sessionId] Opening new session on stream ${metadata.id} for user ${metadata.userId}" }
            }
            .flatMapCompletable { sessionId ->
                pulsarClient.newConsumer(Schema.JSON(MetricEvent::class.java))
                    .subscriptionName("${SUBSCRIBER_PREFIX}_${metadata.id}")
                    .subscriptionType(SubscriptionType.Failover)
                    .subscriptionInitialPosition(SubscriptionInitialPosition.Latest)
                    .topics(metadata.dataRange.datasets.map { config.pulsarDatasetTopic(it) })
                    .rxSubscribe()
                    .flatMapCompletable { consumer ->
                        activeConsumer = consumer

                        // Set streaming position based on request param
                        if (receiveBacklog()) Completable.complete() else consumer.rxSeekToLatest()
                    }
                    .flatMap {
                        try {
                            StreamingService.sseActiveStreamsGlobal.incrementAndGet()
                            metadata.dataRange.datasets.forEach {
                                StreamingService.sseActiveStreamsPerDataset.streamStarted(
                                    it,
                                    datasetIdToNameMap
                                )
                            }

                            Completable.mergeArray(heartBeatWriter(sessionId), eventWriter(sessionId))
                        } catch (t: Throwable) {
                            Completable.error(t)
                        }
                    }
            }
            .doFinally {
                trySignalEndOfSession()
                try {
                    activeConsumer?.close()
                } catch (t: Throwable) {
                    logger.warn { "[$sessionId] Error while trying to close Pulsar consumer for stream ${metadata.id}" }
                }

                StreamingService.sseActiveStreamsGlobal.decrementAndGet()
                metadata.dataRange.datasets.forEach { StreamingService.sseActiveStreamsPerDataset.streamStopped(it) }
            }
            .subscribeBy(
                onComplete = { logger.warn { "[$sessionId] Unexpected end condition for stream ${metadata.id}..." } },
                onError = { err ->
                    when (err) {
                        is IllegalStateException, is ClosedChannelException -> {
                            logger.info { "[$sessionId] Stream ${metadata.id} was closed by client." }
                        }
                        is SessionClosedByFramework -> {
                            trySendErrorToClient(
                                err,
                                "The stream was closed using the API or has been replaced with a new session."
                            )
                            logger.info { "[$sessionId] Stream ${metadata.id} was closed from API or has been replaced with a new session..." }
                        }
                        else -> {
                            trySendErrorToClient(err)
                            logger.warn(err) { "[$sessionId] An unexpected error has occurred while streaming for stream ${metadata.id}!" }
                        }
                    }
                }
            )
    }

    private fun receiveBacklog(): Boolean {
        return routingContext.request().getParam(RECEIVE_BACKLOG_QUERY_PARAM)?.toBooleanStrictOrNull() ?: false
    }

    private fun prepareHttpResponse() {
        routingContext.response().apply {
            isChunked = true
            statusCode = 200
            putHeader("X-Accel-Buffering", "no")
            putHeader("Content-Type", "text/event-stream")
            putHeader("Cache-Control", "no-cache")
            putHeader("Connection", "keep-alive")
        }
    }

    private fun sendPreamble() {
        routingContext.response().write(":${CharArray(512) { ' ' }}\n\n", "UTF-8")
    }

    private fun heartBeatWriter(sessionId: String): Completable {
        return Flowable.interval(HEARTBEAT_PERIOD_SECONDS, HEARTBEAT_PERIOD_SECONDS, TimeUnit.SECONDS)
            .concatMapCompletable {
                sendComment("heartbeat")
            }
    }

    private fun eventWriter(sessionId: String): Completable {
        return activeConsumer!!.toFlowable(BackpressureStrategy.BUFFER)
            .filter { it.second.value.let { event -> matchesRange(event) && event.matches(metadata.filter) } }
            .concatMapSingle { (_, event) ->
                StreamingService.microMeterRegistry.counter(
                    "oblx.sse.events.streamed",
                    datasetIdAndNameTag.instantiate(
                        event.value.dataset!!,
                        event.value.dataset?.let { datasetIdToNameMap.getName(it) } ?: ""
                    )
                ).increment()
                sendData(event.value).toSingleDefault(event.messageId)
            }
            .sample(COMMIT_PERIOD_MS / 4, TimeUnit.MILLISECONDS)
            .buffer(COMMIT_PERIOD_MS, TimeUnit.MILLISECONDS)
            .flatMapCompletable { lastMessageIds ->
                (if (lastMessageIds.isNotEmpty()) {
                    activeConsumer!!.rxAcknowledgeCumulative(lastMessageIds.last())
                        .doOnError { StreamingService.sseAckFailures.increment() }
                        .onErrorComplete()
                } else {
                    Completable.complete()
                }).flatMap {
                    streamingSessions.shouldExist(metadata.id!!, sessionId)
                        .flatMapCompletable { shouldExist ->
                            if (shouldExist) {
                                Completable.complete()
                            } else {
                                // Close the stream by throwing an exception
                                Completable.error(SessionClosedByFramework())
                            }
                        }
                }
            }
    }

    private data class MessageIdContainer(val item: MessageId? = null)

    private fun sendComment(comment: String): Completable {
        return routingContext.response().rxWrite("comment: ${comment}\n\n", "UTF-8")
    }

    private fun sendData(event: MetricEvent): Completable {
        val json = JsonObject.mapFrom(event)
        json.put("timestamp", metadata.timestampPrecision.unit.convert(event.timestamp, TimeUnit.MICROSECONDS))
        json.removeAll { !metadata.fields.contains(EventField.valueOf(it.key)) }
        return routingContext.response().rxWrite("data: ${json.encode()}\n\n", "UTF-8")
    }

    private fun matchesRange(event: MetricEvent): Boolean {
        return metadata.dataRange.metrics.any { it == event.metric || (it.isWildcard() && it.type == event.metric?.type) }
    }

    private fun trySendErrorToClient(err: Throwable, msg: String? = null) {
        try {
            sendComment("An error occurred: ${msg ?: err.message}")
                .flatMap { routingContext.rxEnd() }
                .blockingAwait()
        } catch (t: Throwable) {
            // Nothing
        }
    }

    private fun trySignalEndOfSession() {
        try {
            streamingSessions.stop(metadata.id!!, sessionId!!)
                .retryWhen(RetryWhen.exponentialBackoff(100, TimeUnit.MILLISECONDS).maxRetries(15).build())
                .subscribeBy(
                    onComplete = {},
                    onError = { logger.warn(it) { "[$sessionId] Could not notify end of session... (Redis error?)" } }
                )
        } catch (t: Throwable) {
            // Nothing
        }
    }


}

class SessionClosedByFramework : RuntimeException()