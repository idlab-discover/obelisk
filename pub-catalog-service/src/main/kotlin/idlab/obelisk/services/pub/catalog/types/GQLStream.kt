package idlab.obelisk.services.pub.catalog.types

import graphql.schema.DataFetchingEnvironment
import idlab.obelisk.definitions.catalog.*
import idlab.obelisk.services.pub.catalog.types.util.GQLFetcher
import idlab.obelisk.services.pub.catalog.types.util.GQLType
import idlab.obelisk.services.pub.catalog.types.util.Operations
import idlab.obelisk.utils.service.streaming.StreamingSessions
import io.reactivex.Maybe
import org.redisson.api.RedissonClient
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@GQLType("DataStream")
class GQLStream @Inject constructor(
    private val metaStore: MetaStore,
    private val redissonClient: RedissonClient,
    accessManager: AccessManager
) :
    Operations(accessManager) {

    private val streamingSessions = StreamingSessions(redissonClient)

    @GQLFetcher
    fun clientConnected(env: DataFetchingEnvironment): CompletionStage<Boolean> {
        return withAccess(env) {
            val parent = env.getSource<DataStream>()
            streamingSessions.isActive(parent.id!!)
        }
    }

    @GQLFetcher
    fun user(env: DataFetchingEnvironment): CompletionStage<User> {
        return withAccess(env) {
            val parent = env.getSource<DataStream>()
            metaStore.getUser(parent.userId).toSingle()
        }
    }

    @GQLFetcher
    fun team(env: DataFetchingEnvironment): CompletionStage<Team> {
        return withAccessMaybe(env) {
            val parent = env.getSource<DataStream>()
            parent.teamId?.let { metaStore.getTeam(it) } ?: Maybe.empty()
        }
    }

    @GQLFetcher
    fun timestampPrecision(env: DataFetchingEnvironment): CompletionStage<TimeUnit> {
        return CompletableFuture.completedStage(env.getSource<DataStream>().timestampPrecision.unit)
    }

}