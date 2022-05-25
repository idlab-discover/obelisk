package idlab.obelisk.services.pub.catalog.types

import graphql.schema.DataFetchingEnvironment
import idlab.obelisk.definitions.Eq
import idlab.obelisk.definitions.catalog.*
import idlab.obelisk.definitions.catalog.codegen.DataExportField
import idlab.obelisk.definitions.catalog.codegen.DataStreamField
import idlab.obelisk.definitions.ratelimiting.RateLimiter
import idlab.obelisk.services.pub.catalog.types.util.GQLFetcher
import idlab.obelisk.services.pub.catalog.types.util.GQLType
import idlab.obelisk.services.pub.catalog.types.util.Operations
import io.reactivex.Maybe
import io.reactivex.Single
import java.util.concurrent.CompletionStage
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
@GQLType("Client")
class GQLClient @Inject constructor(
    private val rateLimiter: RateLimiter,
    private val metaStore: MetaStore,
    accessManager: AccessManager
) :
    Operations(accessManager) {

    @GQLFetcher
    fun user(env: DataFetchingEnvironment): CompletionStage<User> {
        return withAccess(env) {
            val parent = env.getSource<Client>()
            metaStore.getUser(parent.userId).toSingle()
        }
    }

    @GQLFetcher
    fun team(env: DataFetchingEnvironment): CompletionStage<Team> {
        return withAccessMaybe(env) {
            val parent = env.getSource<Client>()
            parent.teamId?.let { metaStore.getTeam(it) } ?: Maybe.empty()
        }
    }

    @GQLFetcher
    fun usageLimit(env: DataFetchingEnvironment): CompletionStage<UsageLimit> {
        return withAccess(env) {
            val parent = env.getSource<Client>()
            getLimits(parent).map { it.second }
        }
    }

    @GQLFetcher
    fun usageRemaining(env: DataFetchingEnvironment): CompletionStage<Map<UsageLimitId, Long>> {
        return withAccessMaybe(env) {
            val parent = env.getSource<Client>()

            if (parent.onBehalfOfUser) {
                Maybe.empty()
            } else {
                getLimits(parent)
                    .flatMap { (user, limits) ->
                        // Create tmp token and fetch rate limits
                        rateLimiter.getStatus(Token(user, parent, emptyMap(), limits, ""))
                            .flatMap { status ->
                                // Calculate remaining exports and streams
                                metaStore.countDataExports(Eq(DataExportField.USER_ID, user.id!!))
                                    .zipWith(
                                        metaStore.countDataStreams(
                                            Eq(
                                                DataStreamField.USER_ID,
                                                user.id!!
                                            )
                                        )
                                    ) { nrOfExports, nrOfStreams ->
                                        status
                                            .plus(UsageLimitId.maxDataExports to (limits.values[UsageLimitId.maxDataExports]!! - nrOfExports.toInt()))
                                            .plus(UsageLimitId.maxDataStreams to (limits.values[UsageLimitId.maxDataStreams]!! - nrOfStreams.toInt()))
                                    }
                            }
                    }
                    .toMaybe()
            }
        }
    }

    private fun getLimits(client: Client): Single<Pair<User, UsageLimit>> {
        return metaStore.getUser(client.userId)
            .flatMapSingle { user ->
                metaStore.getAggregatedUsageLimits(user, client).map { user to it }
            }
    }

}