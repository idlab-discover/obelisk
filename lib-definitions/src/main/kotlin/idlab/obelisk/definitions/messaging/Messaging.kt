package idlab.obelisk.definitions.messaging

import kotlinx.coroutines.flow.Flow
import kotlin.reflect.KClass

/**
 * Defines an Obelisk message.
 */
data class Message<T>(
    // The content of the message
    val content: T,
    // A message identifier
    val messageId: MessageId
)

/**
 * Defines MessageId
 * The actual representation of this id is determined by the underlying implementation.
 */
interface MessageId : Comparable<MessageId>

/**
 * Defines a generic message broker component, used by Obelisk for various messaging purposes (pub-sub, queueing, etc).
 */
interface MessageBroker {

    /**
     * Create a new Obelisk-specific message producer.
     *
     * @param topicName The name of the topic the messages are sent to.
     * @param contentType The type of data that is sent.
     * @param senderName An optional name for this specific sender.
     * @param mode Allows configuring the producer mode: normal or high throughput (defaults to normal).
     */
    suspend fun <T : Any> createProducer(
        topicName: String,
        contentType: KClass<T>,
        senderName: String? = null,
        mode: ProducerMode = ProducerMode.NORMAL
    ): MessageProducer<T>

    /**
     * Similar to 'createProducer', but the underlying implementation should try to re-use (cached) producer instances
     * that may already have been created. This is useful for components that need to send to lots of
     * different topics (e.g. dataset-specific topics are not bounded).
     *
     * @param topicName The name of the topic the messages are sent to.
     * @param contentType The type of data that is sent.
     * @param senderName An optional name for this specific sender.
     * @param mode Allows configuring the producer mode: normal or high throughput (defaults to normal).
     */
    suspend fun <T : Any> getProducer(
        topicName: String,
        contentType: KClass<T>,
        senderName: String? = null,
        mode: ProducerMode = ProducerMode.NORMAL
    ): MessageProducer<T>

    /**
     * Create a new Obelisk-specific message consumer for a single topic.
     *
     * @param topicName The name of the topic to subscribe to.
     * @param subscriptionName The name for this subscription.
     * @param contentType The type of data that is received.
     * @param mode The Obelisk messaging mode to be used.
     */
    suspend fun <T : Any> createConsumer(
        topicName: String,
        subscriptionName: String,
        contentType: KClass<T>,
        mode: MessagingMode = MessagingMode.SIGNALING
    ): MessageConsumer<T>

    /**
     * Create a new Obelisk-specific message consumer that subscribes to multiple topics.
     *
     * @param topicNames The names of the topic to subscribe to.
     * @param subscriptionName The name for this subscription.
     * @param contentType The type of data that is received.
     * @param mode The Obelisk messaging mode to be used.
     */
    suspend fun <T : Any> createConsumer(
        topicNames: List<String>,
        subscriptionName: String,
        contentType: KClass<T>,
        mode: MessagingMode = MessagingMode.SIGNALING
    ): MessageConsumer<T>

}

/**
 * Defines the mode a message producer should operate in.
 */
enum class ProducerMode {
    /**
     * Use the default settings of the underlying technology.
     */
    NORMAL,

    /**
     * The underlying technology is configured to optimize for transferring large volumes of high-frequent data.
     */
    HIGH_THROUGHPUT
}

/**
 * Defines the various messaging modes required by Obelisk.
 */
enum class MessagingMode {
    /**
     * The messaging system is configured for transmitting control signals.
     * - Each message will be delivered to all active consumers (having the same subscriptionName).
     * - No special scalability or reliability measures are applied.
     * - A topic with one partition can be used (recommended).
     */
    SIGNALING,

    /**
     * The messaging system is configured for transferring (large) batches of data between two services.
     * Use this mode when ordering is not important, and you want to maximise throughput.
     * - The number of active concurrent consumers can be greater than the number of topic partitions.
     * - Each message is only delivered to a single consumer in the pool (of consumers having the same subscriptionName).
     * - Message ordering is not guaranteed.
     */
    QUEUEING,

    /**
     * The messaging system is configured for streaming (large volumes of) data between two services.
     * Use this mode when the receiving side should process messages in order (per partition).
     * - For each topic partition, a master consumer is elected, which receives the partition messages in order.
     * - If the master consumer becomes unreachable, a new master is automatically elected.
     * - There are never more active consumers than the number of topic partitions.
     * - Each message is delivered to a single active consumer for the partition it belongs to.
     *
     * It is recommended to use this mode with topics that have a time-restricted retention policy,
     * so old messages are discarded automatically by the backend technology (e.g. Pulsar, Kafka).
     */
    STREAMING
}

/**
 * Defines a message producer.
 */
interface MessageProducer<T> : AutoCloseable {

    /**
     * Send the given content as a message.
     */
    suspend fun send(content: T): MessageId

}

/**
 * Defines a message consumer.
 */
interface MessageConsumer<T> : AutoCloseable {

    /**
     * Receive messages for this consumer as a Kotlin reactive flow.
     */
    suspend fun receive(): Flow<Message<T>>

    /**
     * Acknowledge a specific message by id. Depending on the underlying implementation, this operation may be
     * equivalent to 'acknowledgeCumulative'.
     * Check the documentation of the plugin that is used!
     */
    suspend fun acknowledge(messageId: MessageId)

    /**
     * Acknowledge specific messages by id. Depending on the underlying implementation, this operation may be
     * equivalent to 'acknowledgeCumulative' with the latest message id of the given list.
     * Check the documentation of the plugin that is used!
     */
    suspend fun acknowledge(messageIds: Collection<MessageId>)

    /**
     * Acknowledges the given messageId and implicitly all messages that came before it.
     */
    suspend fun acknowledgeCumulative(messageId: MessageId)

    /**
     * Resets the consumer to the given messageId.
     */
    suspend fun seek(messageId: MessageId)

    /**
     * The consumer will continue receiving messages from (and including) the last message on the topic.
     */
    suspend fun seekToLatest()

}
