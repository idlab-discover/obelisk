@file:JvmName("RxPulsar")

package idlab.obelisk.pulsar.utils

import com.github.benmanes.caffeine.cache.Caffeine
import com.github.benmanes.caffeine.cache.LoadingCache
import idlab.obelisk.definitions.framework.OblxConfig
import io.reactivex.*
import io.reactivex.rxkotlin.toFlowable
import io.reactivex.schedulers.Schedulers
import mu.KotlinLogging
import org.apache.pulsar.client.api.*
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

val logger = KotlinLogging.logger { }
internal val nrOfThreads =
    System.getenv(OblxConfig.PULSAR_LISTENER_THREADS)?.toIntOrNull() ?: OblxConfig().pulsarListenerThreads
internal val pulsarPostScheduler = Schedulers.from(Executors.newFixedThreadPool(nrOfThreads))

fun <T> ProducerBuilder<T>.configureForHighThroughput(): ProducerBuilder<T> {
    return this.enableBatching(true)
        .batchingMaxBytes(1024 * 1024) // 1MB
        .batchingMaxMessages(Integer.MAX_VALUE)
        .maxPendingMessagesAcrossPartitions(Integer.MAX_VALUE)
        .batchingMaxPublishDelay(50, TimeUnit.MILLISECONDS)
}

fun <T> Producer<T>.rxSend(message: T): Single<MessageId> {
    return Single.fromFuture(this.sendAsync(message), pulsarPostScheduler)
}

fun <T> Producer<T>.rxClose(): Completable {
    return Completable.fromFuture(this.closeAsync())
}

fun <T> ConsumerBuilder<T>.rxSubscribe(): Single<Consumer<T>> {
    return Single.fromFuture(this.subscribeAsync())
}

fun <T> ConsumerBuilder<T>.rxSubscribeAsFlowable(backpressureStrategy: BackpressureStrategy = BackpressureStrategy.DROP): Flowable<Pair<Consumer<T>, Message<T>>> {
    return this.rxSubscribe()
        .flatMapPublisher { consumer -> consumer.toFlowable(backpressureStrategy) }
}

fun <T> Consumer<T>.toFlowable(backpressureStrategy: BackpressureStrategy = BackpressureStrategy.DROP): Flowable<Pair<Consumer<T>, Message<T>>> {
    fun generator(emitter: FlowableEmitter<Pair<Consumer<T>, Message<T>>>) {
        this.batchReceiveAsync().thenAccept { msgs ->
            msgs.toFlowable().forEach { msg ->
                emitter.onNext(Pair(this, msg))
            }
            generator(emitter);
        }
    }
    return Flowable.create({ generator(it) }, backpressureStrategy)
}

fun <T> Consumer<T>.rxAcknowledge(message: Message<T>): Completable {
    return Completable.fromFuture(this.acknowledgeAsync(message))
}

fun <T> Consumer<T>.rxAcknowledgeCumulative(messageId: MessageId): Completable {
    return Completable.fromFuture(this.acknowledgeCumulativeAsync(messageId))
}

fun <T> Consumer<T>.rxAcknowledgeCumulative(message: Message<T>): Completable {
    return Completable.fromFuture(this.acknowledgeCumulativeAsync(message))
}


fun <T> Consumer<T>.rxBatchAcknowledge(messages: List<Message<T>>): Completable {
    return Completable.fromFuture(this.acknowledgeAsync(messages.map { it.messageId }))
}

fun <T> Consumer<T>.rxUnsubscribe(): Completable {
    return Completable.fromFuture(this.unsubscribeAsync())
}

fun <T> Consumer<T>.rxClose(): Completable {
    return Completable.fromFuture(this.closeAsync())
}

fun <T> Consumer<T>.rxSeekToLatest(): Completable {
    return Single.fromFuture(this.lastMessageIdAsync)
        .flatMapCompletable { lastMessageId ->
            Completable.fromFuture(this.seekAsync(lastMessageId))
        }
}

/**
 * A cache for keeping Producer connections in memory. Can be useful if you need to produce events to a large number of topics efficiently.
 *
 * By default the cache can contain a maximum of 200 producers (that will expire after 5 minutes of non-use).
 */
class RxPulsarProducerCache<T>(
    private val pulsarClient: PulsarClient,
    private val producerName: String? = null,
    private val maxSize: Long = 200,
    private val expireAfterValue: Long = 5,
    private val expireAfterUnit: TimeUnit = TimeUnit.MINUTES,
    private val configurator: ((ProducerBuilder<T>) -> ProducerBuilder<T>)? = null
) {

    private data class Key<T>(val schema: Schema<T>, val topicName: String)

    private val producerCache: LoadingCache<Key<T>, Single<Producer<T>>> = Caffeine.newBuilder()
        .maximumSize(maxSize)
        .expireAfterAccess(expireAfterValue, expireAfterUnit)
        .removalListener { _: Key<T>?, producer: Single<Producer<T>>?, _ ->
            producer?.flatMapCompletable {
                it.rxClose()
            }?.subscribe()
        }
        .build { key -> createProducer(key).cache() }

    fun rxGet(schema: Schema<T>, topicName: String): Single<Producer<T>> {
        return producerCache[Key(schema, topicName)]!!
    }

    private fun createProducer(key: Key<T>): Single<Producer<T>> {
        val tmp = pulsarClient.newProducer(key.schema)
            .topic(key.topicName)
            .blockIfQueueFull(true)

        producerName?.let { tmp.producerName(it) }
        configurator?.invoke(tmp)

        return Single.fromFuture(tmp.createAsync(), pulsarPostScheduler)
    }
}