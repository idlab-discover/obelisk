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
import idlab.obelisk.definitions.messaging.*
import idlab.obelisk.services.pub.ngsi.impl.model.Notification
import idlab.obelisk.services.pub.ngsi.impl.model.Subscription
import idlab.obelisk.utils.service.reactive.flatMap
import idlab.obelisk.utils.service.utils.matches
import idlab.obelisk.utils.service.utils.unpage
import io.reactivex.Completable
import io.reactivex.rxkotlin.subscribeBy
import io.reactivex.rxkotlin.toFlowable
import kotlinx.coroutines.reactive.asFlow
import kotlinx.coroutines.rx2.asFlowable
import kotlinx.coroutines.rx2.await
import kotlinx.coroutines.rx2.rxCompletable
import mu.KotlinLogging
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.system.exitProcess

@Singleton
class NgsiLDSubscriptionMatcher @Inject constructor(
    private val config: OblxConfig,
    private val metaStore: MetaStore,
    private val messageBroker: MessageBroker
) : OblxService {

    private val logger = KotlinLogging.logger { }

    private lateinit var metricEventsConsumer: MessageConsumer<MetricEvent>
    private lateinit var dataStreamEventsConsumer: MessageConsumer<DataStreamEvent>
    private lateinit var notificationProducer: MessageProducer<Notification>

    // Mapping of: datasetId (String) -> dataStreamId (String) -> DataStream
    private val dataStreams = mutableMapOf<String, MutableMap<String, DataStream>>()

    override fun start(): Completable = rxCompletable {
        metricEventsConsumer = messageBroker.createConsumer(
            topicName = config.pulsarMetricEventsTopic,
            subscriptionName = Constants.PULSAR_METRICS_SUBSCRIPTION,
            contentType = MetricEvent::class,
            mode = MessagingMode.STREAMING
        )
        dataStreamEventsConsumer = messageBroker.createConsumer(
            topicName = ControlChannels.DATA_STREAM_EVENTS_TOPIC,
            subscriptionName = Constants.PULSAR_DATASTREAM_CONTROL_SUBSCRIPTION,
            contentType = DataStreamEvent::class
        )
        dataStreamEventsConsumer.seekToLatest()
        notificationProducer = messageBroker.createProducer(
            topicName = Constants.PULSAR_NOTIFICATION_TOPIC,
            senderName = "${config.hostname()}_matcher",
            contentType = Notification::class,
            mode = ProducerMode.HIGH_THROUGHPUT
        )

        // Find all NGSI-LD related datastreams
        unpage { cursor ->
            metaStore.queryDataStreams(
                cursor = cursor,
                filter = Eq(Field("properties", "type"), "Subscription")
            )
        }.asFlow().collect { ds ->
            // Populate our in-memory view of applicable Datastreams
            // Datastreams for NGSI-LD integrations, will always have one dataset in their dataRange (by design)
            dataStreams.computeIfAbsent(ds.dataRange.datasets.first()) { mutableMapOf() }[ds.id!!] = ds
        }

        // Create control event listeners (allows syncing the in-memory view with what happens in other parts of the system)
        dataStreamEventsConsumer.receive().asFlowable()
            .flatMapCompletable { handleDataStreamEvent(it) }
            .subscribeBy(onError = {
                logger.error(it) { "A fatal error occurred in the Datastream Events control logic. Shutting down..." }
                exitProcess(1)
            })

        // Create subscription matching flow
        metricEventsConsumer.receive().asFlowable()
            .flatMapCompletable { match(it) }
            .subscribeBy(onError = {
                logger.error(it) { "A fatal error occurred in the Subscription Matching logic. Shutting down..." }
                exitProcess(1)
            })
    }

    private fun handleDataStreamEvent(
        event: Message<DataStreamEvent>
    ): Completable {
        return (when (event.content.type) {
            DataStreamEventType.INIT -> {
                // Fetch the Datastream definition and add it to the in-memory view
                metaStore.getDataStream(event.content.streamId)
                    .doOnSuccess { ds ->
                        if (ds.properties.containsKey("type") && ds.properties["type"] == "Subscription") {
                            dataStreams.computeIfAbsent(ds.dataRange.datasets.first()) { mutableMapOf() }[ds.id!!] = ds
                        }
                    }
                    .ignoreElement()
            }
            DataStreamEventType.STOP -> {
                // Remove the Datastream from the in-memory view (as it has been deleted)
                dataStreams.values.forEach { it.remove(event.content.streamId) }
                Completable.complete()
            }
            else -> Completable.complete()
        }).flatMap {
            rxCompletable {
                // Ack the event
                dataStreamEventsConsumer.acknowledge(event.messageId)
            }
        }
    }

    private fun match(event: Message<MetricEvent>): Completable {
        // Lookup compatible subscriptions (applicable to the dataset of the event)
        val metricEvent = event.content
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
            .flatMapCompletable { rxCompletable { notificationProducer.send(it) } }
            .flatMap { rxCompletable { metricEventsConsumer.acknowledge(event.messageId) } }
    }
}
