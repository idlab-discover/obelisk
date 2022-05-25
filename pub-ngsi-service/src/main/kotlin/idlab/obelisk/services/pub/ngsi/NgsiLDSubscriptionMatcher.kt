package idlab.obelisk.services.pub.ngsi

import idlab.obelisk.definitions.Eq
import idlab.obelisk.definitions.Field
import idlab.obelisk.definitions.catalog.DataStream
import idlab.obelisk.definitions.catalog.MetaStore
import idlab.obelisk.definitions.control.ControlChannels
import idlab.obelisk.definitions.control.DataStreamEvent
import idlab.obelisk.definitions.control.DataStreamEventType
import idlab.obelisk.definitions.data.MetricEvent
import idlab.obelisk.definitions.framework.OblxConfig
import idlab.obelisk.definitions.framework.OblxService
import idlab.obelisk.pulsar.utils.rxAcknowledge
import idlab.obelisk.pulsar.utils.rxSend
import idlab.obelisk.pulsar.utils.rxSubscribeAsFlowable
import idlab.obelisk.services.pub.ngsi.impl.model.Notification
import idlab.obelisk.services.pub.ngsi.impl.model.Subscription
import idlab.obelisk.utils.service.reactive.flatMap
import idlab.obelisk.utils.service.utils.matches
import idlab.obelisk.utils.service.utils.unpage
import io.reactivex.Completable
import io.reactivex.rxkotlin.subscribeBy
import io.reactivex.rxkotlin.toFlowable
import io.vertx.reactivex.core.Vertx
import mu.KotlinLogging
import org.apache.pulsar.client.api.*
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.system.exitProcess

@Singleton
class NgsiLDSubscriptionMatcher @Inject constructor(
    pulsarClient: PulsarClient,
    config: OblxConfig,
    private val metaStore: MetaStore,
    private val vertx: Vertx
) : OblxService {

    private val logger = KotlinLogging.logger { }

    private val metricEventsBuilder = pulsarClient.newConsumer(Schema.JSON(MetricEvent::class.java))
        .subscriptionName(Constants.PULSAR_METRICS_SUBSCRIPTION)
        .subscriptionType(SubscriptionType.Failover)
        .subscriptionInitialPosition(SubscriptionInitialPosition.Earliest)
        .topic(config.pulsarMetricEventsTopic)

    private val dataStreamEventsBuilder = pulsarClient.newConsumer(Schema.JSON(DataStreamEvent::class.java))
        .subscriptionName(Constants.PULSAR_DATASTREAM_CONTROL_SUBSCRIPTION)
        .subscriptionType(SubscriptionType.Shared)
        .subscriptionInitialPosition(SubscriptionInitialPosition.Latest)
        .topic(ControlChannels.DATA_STREAM_EVENTS_TOPIC)

    private val notificationProducer = pulsarClient.newProducer(Schema.JSON(Notification::class.java))
        .producerName("${config.hostname()}_matcher")
        .blockIfQueueFull(true)
        .topic(Constants.PULSAR_NOTIFICATION_TOPIC).create()

    // Mapping of: datasetId (String) -> dataStreamId (String) -> DataStream
    private val dataStreams = mutableMapOf<String, MutableMap<String, DataStream>>()

    override fun start(): Completable {
        // Find all NGSI-LD related datastreams
        return unpage { cursor ->
            metaStore.queryDataStreams(
                cursor = cursor,
                filter = Eq(Field("properties", "type"), "Subscription")
            )
        }
            .doOnNext { ds ->
                // Populate our in-memory view of applicable Datastreams
                // Datastreams for NGSI-LD integrations, will always have one dataset in their dataRange (by design)
                dataStreams.computeIfAbsent(ds.dataRange.datasets.first()) { mutableMapOf() }[ds.id!!] = ds
            }
            .doOnComplete {
                // Create control event listeners (allows syncing the in-memory view with what happens in other parts of the system)
                dataStreamEventsBuilder.rxSubscribeAsFlowable()
                    .flatMapCompletable { handleDataStreamEvent(it.first, it.second) }
                    .subscribeBy(onError = {
                        logger.error(it) { "A fatal error occurred in the Datastream Events control logic. Shutting down..." }
                        exitProcess(1)
                    })

                // Create subscription matching flow
                metricEventsBuilder.rxSubscribeAsFlowable()
                    .flatMapCompletable { match(it.first, it.second) }
                    .subscribeBy(onError = {
                        logger.error(it) { "A fatal error occurred in the Subscription Matching logic. Shutting down..." }
                        exitProcess(1)
                    })
            }
            .ignoreElements()
    }

    private fun handleDataStreamEvent(
        consumer: Consumer<DataStreamEvent>,
        event: Message<DataStreamEvent>
    ): Completable {
        return (when (event.value.type) {
            DataStreamEventType.INIT -> {
                // Fetch the Datastream definition and add it to the in-memory view
                metaStore.getDataStream(event.value.streamId)
                    .doOnSuccess { ds ->
                        if (ds.properties.containsKey("type") && ds.properties["type"] == "Subscription") {
                            dataStreams.computeIfAbsent(ds.dataRange.datasets.first()) { mutableMapOf() }[ds.id!!] = ds
                        }
                    }
                    .ignoreElement()
            }
            DataStreamEventType.STOP -> {
                // Remove the Datastream from the in-memory view (as it has been deleted)
                dataStreams.values.forEach { it.remove(event.value.streamId) }
                Completable.complete()
            }
            else -> Completable.complete()
        }).flatMap {
            // Ack the event
            consumer.rxAcknowledge(event)
        }
    }

    private fun match(consumer: Consumer<MetricEvent>, event: Message<MetricEvent>): Completable {
        // Lookup compatible subscriptions (applicable to the dataset of the event)
        val metricEvent = event.value
        return dataStreams[metricEvent.dataset!!]?.values
            ?.filter { metricEvent.matches(it.filter) }
            ?.map {
                val subscription = Subscription.fromDataStream(it)
                Notification(
                    subscriptionId = subscription.id,
                    datasetId = metricEvent.dataset!!,
                    entityIds = listOf(metricEvent.source!!),
                    attributes = subscription.notification.attributes,
                    endpoint = subscription.notification.endpoint
                )
            }
            .orEmpty()
            .toFlowable()
            .flatMapCompletable { notificationProducer.rxSend(it).ignoreElement() }
            .flatMap { consumer.rxAcknowledge(event) }
    }
}