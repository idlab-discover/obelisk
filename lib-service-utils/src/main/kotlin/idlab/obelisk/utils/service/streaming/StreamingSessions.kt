package idlab.obelisk.utils.service.streaming

import hu.akarnokd.rxjava3.bridge.RxJavaBridge
import io.reactivex.Completable
import io.reactivex.Single
import org.redisson.api.RedissonClient
import java.util.*

// String value used to signal Catalog service wants the streaming session to close
private const val CLOSED_CONST = "closed"

class StreamingSessions(private val redis: RedissonClient) {

    /**
     * Start a new session for the specified stream
     *
     * From this moment on, old sessions are obsolete
     */
    fun start(streamId: String): Single<String> {
        val sessionId = UUID.randomUUID().toString()
        return redis.rxJava().getBucket<String>(streamId).set(sessionId)
            .toSingleDefault(sessionId)
            .to(RxJavaBridge.toV2Single())
    }

    /**
     * Stop an existing session
     */
    fun stop(streamId: String, sessionId: String): Completable {
        return redis.rxJava().getBucket<String>(streamId).compareAndSet(sessionId, CLOSED_CONST).ignoreElement()
            .to(RxJavaBridge.toV2Completable())
    }

    /**
     * The session state is cleared for the specified streamId
     *
     * From this moment on, old sessions are obsolete
     */
    fun clear(streamId: String): Completable {
        return redis.rxJava().getBucket<String>(streamId).set(CLOSED_CONST).to(RxJavaBridge.toV2Completable())
    }

    /**
     * Check if the session for the specified streamId should exist
     */
    fun shouldExist(streamId: String, sessionId: String): Single<Boolean> {
        /**
         * The session should exists if it was the last one created
         * (in this case the value stored in redis should be equal to the sessionId)
         * OR if no entry can be found
         *
         * In all other cases, the session should not exist
         */
        return redis.rxJava().getBucket<String>(streamId).get().defaultIfEmpty("")
            .map { value ->
                value == "" || value == sessionId
            }
            .to(RxJavaBridge.toV2Single())
    }

    fun isActive(streamId: String): Single<Boolean> {
        return redis.rxJava().getBucket<String>(streamId).get().defaultIfEmpty("")
            .map { it != "" && it != CLOSED_CONST }
            .to(RxJavaBridge.toV2Single())
    }

}