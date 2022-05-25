package idlab.obelisk.services.pub.streaming

import idlab.obelisk.definitions.EventField
import idlab.obelisk.definitions.catalog.DataStream
import idlab.obelisk.definitions.catalog.MetaStore
import idlab.obelisk.definitions.catalog.codegen.DataStreamUpdate
import idlab.obelisk.definitions.control.ControlChannels
import idlab.obelisk.definitions.control.DataStreamEvent
import idlab.obelisk.definitions.control.DataStreamEventType
import idlab.obelisk.definitions.data.MetricEvent
import idlab.obelisk.definitions.framework.OblxConfig
import idlab.obelisk.pulsar.utils.rxAcknowledgeCumulative
import idlab.obelisk.pulsar.utils.rxClose
import idlab.obelisk.pulsar.utils.rxSubscribe
import idlab.obelisk.pulsar.utils.toFlowable
import idlab.obelisk.utils.service.http.HttpError
import idlab.obelisk.utils.service.instrumentation.IdToNameMap
import idlab.obelisk.utils.service.instrumentation.TagTemplate
import idlab.obelisk.utils.service.reactive.flatMap
import idlab.obelisk.utils.service.utils.matches
import io.reactivex.BackpressureStrategy
import io.reactivex.Completable
import io.reactivex.Flowable
import io.reactivex.rxkotlin.subscribeBy
import io.vertx.core.json.Json
import io.vertx.core.json.JsonObject
import io.vertx.reactivex.core.Vertx
import io.vertx.reactivex.ext.web.RoutingContext
import mu.KotlinLogging
import org.apache.pulsar.client.api.*
import java.nio.channels.ClosedChannelException
import java.util.concurrent.CompletionException
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit

const val SUBSCRIBER_PREFIX = "sse_fo"
const val HEARTBEAT_PERIOD_SECONDS = 5L

enum class SessionState {
    INIT, STREAMING, TERMINATE_REQUESTED, TERMINATED_FROM_API, TERMINATED_BY_CLIENT
}

private val datasetIdAndNameTag = TagTemplate("datasetId", "datasetName")

class StreamingSession(
    private val vertx: Vertx,
    private val pulsarClient: PulsarClient,
    private val metaStore: MetaStore,
    private val metadata: DataStream,
    private val routingContext: RoutingContext,
    private val config: OblxConfig,
    private val datasetIdToNameMap: IdToNameMap
) {
    private val logger = KotlinLogging.logger { }
    private var state = SessionState.INIT
    private var activeConsumer: Consumer<MetricEvent>? = null

    fun start() {
        logger.info { "Opening stream ${metadata.id} for user ${metadata.userId}" }

        // Attach to Vertx eventbus to receive remote stream close events
        vertx.eventBus().localConsumer<String>(ControlChannels.DATA_STREAM_EVENTS_TOPIC).toFlowable()
            .map { Json.decodeValue(it.body(), DataStreamEvent::class.java) }
            .filter { it.streamId == metadata.id }
            .flatMapCompletable { event ->
                when (event.type) {
                    DataStreamEventType.STOP -> endSSE()
                    else -> Completable.complete() // Nothing to do
                }
            }
            .subscribeBy(onError = { logger.warn(it) { "Error while receiving stream control events from local eventbus!" } })

        pulsarClient.newConsumer(Schema.JSON(MetricEvent::class.java))
            .subscriptionName("${SUBSCRIBER_PREFIX}_${metadata.id}")
            .subscriptionType(SubscriptionType.Exclusive)
            .subscriptionInitialPosition(SubscriptionInitialPosition.Latest)
            .topics(metadata.dataRange.datasets.map { config.pulsarDatasetTopic(it) })
            .acknowledgmentGroupTime(500, TimeUnit.MILLISECONDS)
            .rxSubscribe()
            .flatMapCompletable { consumer ->
                try {
                    activeConsumer = consumer
                    prepareHttpResponse()
                    sendPreamble()
                    state = SessionState.STREAMING
                    StreamingService.sseActiveStreamsGlobal.incrementAndGet()
                    metadata.dataRange.datasets.forEach {
                        StreamingService.sseActiveStreamsPerDataset.streamStarted(
                            it,
                            datasetIdToNameMap
                        )
                    }

                    metaStore.updateDataStream(metadata.id!!, DataStreamUpdate(clientConnected = true))
                        .flatMap { Completable.mergeArray(heartBeatWriter(), eventWriter()) }
                } catch (t: Throwable) {
                    Completable.error(t)
                }
            }
            .onErrorResumeNext {
                if (it is IllegalStateException || it is ClosedChannelException) {
                    state =
                        if (state == SessionState.TERMINATE_REQUESTED) SessionState.TERMINATED_FROM_API else SessionState.TERMINATED_BY_CLIENT
                    Completable.complete()
                } else if (it is PulsarClientException.ConsumerBusyException || (it is CompletionException && it.cause is PulsarClientException.ConsumerBusyException) || (it is ExecutionException && it.cause is PulsarClientException.ConsumerBusyException)) {
                    logger.info { "Stream ${metadata.id} aborted (already being used elsewhere!)" }
                    Completable.error(HttpError(409, "This stream is busy (another instance is already connected!)"))
                } else {
                    // An unexpected error occurred, propagate downstream
                    Completable.error(it)
                }
            }
            .flatMap {
                StreamingService.sseActiveStreamsGlobal.decrementAndGet()
                metadata.dataRange.datasets.forEach { StreamingService.sseActiveStreamsPerDataset.streamStopped(it) }

                metaStore.updateDataStream(metadata.id!!, DataStreamUpdate(clientConnected = false))
                    .flatMap {
                        // When this point is reach, try to close the Pulsar consumer (cleanup)
                        activeConsumer?.rxClose()
                            ?.doOnComplete {
                                logger.info { "Stream ${metadata.id} closed on server!" }
                                activeConsumer = null
                            }
                            ?.onErrorComplete {
                                logger.warn { "Error while closing consumer for ${metadata.id}" }
                                true
                            }
                            ?: Completable.complete()
                    }
            }
            .doFinally {
                // Safety routine: if activeConsumer is not null => try closing it a final time
                try {
                    activeConsumer?.close()
                } catch (t: Throwable) {
                    logger.warn { "Error while trying to close consumer a final time for ${metadata.id}" }
                }
            }
            .subscribeBy(
                onComplete = {
                    when (state) {
                        SessionState.TERMINATED_BY_CLIENT -> logger.info { "Stream ${metadata.id} was closed by client." }
                        SessionState.TERMINATED_FROM_API -> logger.info { "Stream ${metadata.id} was closed from API." }
                        else -> logger.warn { "Unexpected end condition for stream ${metadata.id}... (state: $state)" }
                    }
                },
                onError = {
                    tryWriteError(it)
                }
            )
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

    private fun heartBeatWriter(): Completable {
        return Flowable.interval(HEARTBEAT_PERIOD_SECONDS, HEARTBEAT_PERIOD_SECONDS, TimeUnit.SECONDS)
            .concatMapCompletable {
                sendComment("heartbeat")
            }
    }

    private fun eventWriter(): Completable {
        return activeConsumer!!.toFlowable(BackpressureStrategy.BUFFER)
            .filter { it.second.value.let { event -> matchesRange(event) && event.matches(metadata.filter) } }
            .concatMapCompletable { (consumer, event) ->
                StreamingService.microMeterRegistry.counter(
                    "oblx.sse.events.streamed",
                    datasetIdAndNameTag.instantiate(
                        event.value.dataset!!,
                        event.value.dataset?.let { datasetIdToNameMap.getName(it) } ?: ""
                    )
                ).increment()
                sendData(event.value)
                    .flatMap {
                        consumer.rxAcknowledgeCumulative(event)
                            .doOnError { StreamingService.sseAckFailures.increment() }
                            .onErrorComplete()
                    }
            }
    }

    private fun endSSE(): Completable {
        state = SessionState.TERMINATE_REQUESTED
        return sendComment("Terminating the stream (triggered by remote process).")
            .flatMap { routingContext.rxEnd() }
            .onErrorComplete()
    }

    private fun tryWriteError(err: Throwable) {
        try {
            if (state == SessionState.INIT) {
                (if (err is HttpError) err else HttpError(500, err.message, err)).writeResponse(routingContext)
            } else {
                sendComment("An error occurred: ${err.message}")
                    .flatMap { routingContext.rxEnd() }
                    .blockingAwait()
            }
        } catch (t: Throwable) {
            // Nothing
        }
    }

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
}