package idlab.obelisk.services.pub.catalog.types

import graphql.schema.DataFetchingEnvironment
import idlab.obelisk.definitions.catalog.AccessManager
import idlab.obelisk.definitions.catalog.DataStream
import idlab.obelisk.definitions.catalog.MetaStore
import idlab.obelisk.definitions.catalog.Token
import idlab.obelisk.definitions.control.ControlChannels
import idlab.obelisk.definitions.control.DataStreamEvent
import idlab.obelisk.definitions.control.DataStreamEventType
import idlab.obelisk.definitions.framework.OblxConfig
import idlab.obelisk.pulsar.utils.rxSend
import idlab.obelisk.services.pub.catalog.impl.Response
import idlab.obelisk.services.pub.catalog.impl.errorResponse
import idlab.obelisk.services.pub.catalog.types.util.GQLFetcher
import idlab.obelisk.services.pub.catalog.types.util.GQLType
import idlab.obelisk.services.pub.catalog.types.util.Operations
import idlab.obelisk.services.pub.catalog.types.util.PulsarConnections
import idlab.obelisk.utils.service.streaming.StreamingSessions
import io.reactivex.Single
import io.vertx.reactivex.core.Vertx
import org.apache.pulsar.client.api.PulsarClient
import org.apache.pulsar.client.api.Schema
import org.redisson.api.RedissonClient
import java.util.concurrent.CompletionStage
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
@GQLType("StreamMutation")
class GQLStreamMutation @Inject constructor(
    private val metaStore: MetaStore,
    accessManager: AccessManager,
    redis: RedissonClient
) : Operations(accessManager) {

    private val streamSessions = StreamingSessions(redis)

    @GQLFetcher
    fun endSession(env: DataFetchingEnvironment): CompletionStage<Response<DataStream>> {
        return withAccess(env) { token ->
            closeStream(token, env.getSource())
                .map { Response(item = it) }
                .onErrorReturn { errorResponse(env, it) }
        }
    }

    @GQLFetcher
    fun remove(env: DataFetchingEnvironment): CompletionStage<Response<DataStream>> {
        return withAccess(env) { token ->
            closeStream(token, env.getSource())
                .flatMap { stream ->
                    metaStore.removeDataStream(stream.id!!).toSingleDefault(Response(item = stream))
                }
                .onErrorReturn { errorResponse(env, it) }
        }
    }

    private fun closeStream(token: Token, dataStream: DataStream): Single<DataStream> {
        return streamSessions.clear(dataStream.id!!).toSingleDefault(dataStream)
    }
}