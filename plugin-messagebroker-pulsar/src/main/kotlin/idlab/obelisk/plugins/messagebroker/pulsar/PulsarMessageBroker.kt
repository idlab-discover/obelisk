package idlab.obelisk.plugins.messagebroker.pulsar

import com.github.benmanes.caffeine.cache.Caffeine
import com.github.benmanes.caffeine.cache.LoadingCache
import idlab.obelisk.definitions.framework.OblxConfig
import idlab.obelisk.definitions.messaging.*
import idlab.obelisk.definitions.messaging.Message
import idlab.obelisk.definitions.messaging.MessageId
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.future.await
import kotlinx.coroutines.runBlocking
import org.apache.pulsar.client.api.*
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.reflect.KClass

// ENV VARS
const val ENV_PRODUCER_CACHE_MAX_SIZE = "PRODUCER_CACHE_MAX_SIZE"
const val DEFAULT_PRODUCER_CACHE_MAX_SIZE = 200L
const val ENV_PRODUCER_CACHE_EXPIRE_AFTER_SECONDS = "PRODUCER_CACHE_EXPIRE_AFTER_SECONDS"
const val DEFAULT_PRODUCER_CACHE_EXPIRE_AFTER_SECONDS = 5 * 60L // 5 Minutes

private const val OBLX_SENDER_NAME = "oblx-sender-name"

@Singleton
class PulsarMessageBroker @Inject constructor(private val config: OblxConfig, private val pulsarClient: PulsarClient) :
    MessageBroker {

    private val cacheMaxSize = config.getLong(ENV_PRODUCER_CACHE_MAX_SIZE, DEFAULT_PRODUCER_CACHE_MAX_SIZE)
    private val cacheExpireAfterSeconds =
        config.getLong(ENV_PRODUCER_CACHE_EXPIRE_AFTER_SECONDS, DEFAULT_PRODUCER_CACHE_EXPIRE_AFTER_SECONDS)
    private val producerCache: LoadingCache<CacheKey, Deferred<MessageProducer<*>>> = Caffeine.newBuilder()
        .maximumSize(cacheMaxSize)
        .expireAfterAccess(cacheExpireAfterSeconds, TimeUnit.SECONDS)
        .removalListener { _: CacheKey?, producer: Deferred<MessageProducer<*>>?, _ ->
            runBlocking {
                producer?.await()?.close()
            }
        }
        .build { key ->
            runBlocking {
                async { createProducer(key.topicName, key.contentType, key.senderName, key.mode) }
            }
        }

    override suspend fun <T : Any> createProducer(
        topicName: String,
        contentType: KClass<T>,
        senderName: String?,
        mode: ProducerMode
    ): MessageProducer<T> {
        val producerBuilder = pulsarClient.newProducer(Schema.JSON(contentType.java))
            .blockIfQueueFull(true)
            .topic(topicName)
        if (senderName != null) {
            producerBuilder.property(OBLX_SENDER_NAME, senderName)
        }
        if (mode == ProducerMode.HIGH_THROUGHPUT) {
            producerBuilder.enableBatching(true)
                .batchingMaxBytes(1024 * 1024) // 1MB
                .batchingMaxMessages(Integer.MAX_VALUE)
                .maxPendingMessagesAcrossPartitions(Integer.MAX_VALUE)
                .batchingMaxPublishDelay(50, TimeUnit.MILLISECONDS)
        }
        return PulsarMessageProducer(producerBuilder.createAsync().await())
    }

    override suspend fun <T : Any> getProducer(
        topicName: String,
        contentType: KClass<T>,
        senderName: String?,
        mode: ProducerMode
    ): MessageProducer<T> {
        // We can use !! here, as the cache is a loading cache. The cast is also safe, because it is based on contentType.
        return producerCache[CacheKey(topicName, contentType, senderName, mode)]!!.await() as MessageProducer<T>
    }

    override suspend fun <T : Any> createConsumer(
        topicName: String,
        subscriptionName: String,
        contentType: KClass<T>,
        mode: MessagingMode
    ): MessageConsumer<T> {
        return createConsumer(listOf(topicName), subscriptionName, contentType, mode)
    }

    override suspend fun <T : Any> createConsumer(
        topicNames: List<String>,
        subscriptionName: String,
        contentType: KClass<T>,
        mode: MessagingMode
    ): MessageConsumer<T> {
        val subscriptionType = when (mode) {
            MessagingMode.SIGNALING, MessagingMode.QUEUEING -> SubscriptionType.Shared
            MessagingMode.STREAMING -> SubscriptionType.Failover
        }
        val pulsarSubscriptionName = when (mode) {
            MessagingMode.SIGNALING -> "${subscriptionName}@${config.hostname()}"
            else -> "${subscriptionName}[$subscriptionType]"
        }
        val consumer = pulsarClient.newConsumer(Schema.JSON(contentType.java))
            .subscriptionName(pulsarSubscriptionName)
            .subscriptionType(subscriptionType)
            .subscriptionInitialPosition(SubscriptionInitialPosition.Earliest)
            .topics(topicNames)
            .subscribeAsync()
            .await()

        return PulsarMessageConsumer(consumer)
    }

}

class PulsarMessageProducer<T>(
    private val producer: Producer<T>
) : MessageProducer<T> {

    override suspend fun send(content: T): MessageId {
        return PulsarMessageId(producer.sendAsync(content).await())
    }

    override fun close() {
        producer.close()
    }

}

class PulsarMessageConsumer<T>(
    private val consumer: Consumer<T>,
    private var closed: Boolean = false
) : MessageConsumer<T> {
    override suspend fun receive(): Flow<Message<T>> = flow {
        while (!closed) {
            consumer.batchReceiveAsync().await()
                .map { Message(it.value, PulsarMessageId(it.messageId)) }
                .forEach { emit(it) }
        }
    }

    override suspend fun acknowledge(messageId: MessageId) {
        consumer.acknowledgeAsync((messageId as PulsarMessageId).wrappedMessageId).await()
    }

    override suspend fun acknowledge(messageIds: Collection<MessageId>) {
        consumer.acknowledgeAsync(messageIds.map { (it as PulsarMessageId).wrappedMessageId }).await()
    }

    override suspend fun acknowledgeCumulative(messageId: MessageId) {
        consumer.acknowledgeCumulativeAsync((messageId as PulsarMessageId).wrappedMessageId)
    }

    override suspend fun seek(messageId: MessageId) {
        consumer.seekAsync((messageId as PulsarMessageId).wrappedMessageId).await()
    }

    override suspend fun seekToLatest() {
        seek(getLastMessageId())
    }

    private suspend fun getLastMessageId(): MessageId {
        return PulsarMessageId(consumer.lastMessageIdAsync.await())
    }

    override fun close() {
        consumer.close()
        closed = true
    }

}

data class PulsarMessageId(val wrappedMessageId: org.apache.pulsar.client.api.MessageId) : MessageId
private data class CacheKey(
    val topicName: String,
    val contentType: KClass<*>,
    val senderName: String?,
    val mode: ProducerMode
)
