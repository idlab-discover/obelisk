package idlab.obelisk.services.pub.catalog.types

import graphql.schema.DataFetchingEnvironment
import idlab.obelisk.definitions.*
import idlab.obelisk.definitions.catalog.*
import idlab.obelisk.definitions.catalog.codegen.*
import idlab.obelisk.definitions.data.DataStore
import idlab.obelisk.definitions.data.MetaField
import idlab.obelisk.definitions.data.MetaQuery
import idlab.obelisk.definitions.ratelimiting.RateLimiter
import idlab.obelisk.services.pub.catalog.impl.GraphQLPage
import idlab.obelisk.services.pub.catalog.impl.Membership
import idlab.obelisk.services.pub.catalog.impl.getFilter
import idlab.obelisk.services.pub.catalog.types.util.GQLFetcher
import idlab.obelisk.services.pub.catalog.types.util.GQLType
import idlab.obelisk.services.pub.catalog.types.util.Operations
import idlab.obelisk.utils.service.reactive.toSet
import idlab.obelisk.utils.service.utils.Base64.decodeFromBase64
import idlab.obelisk.utils.service.utils.Base64.encodeAsBase64
import idlab.obelisk.utils.service.utils.matches
import idlab.obelisk.utils.service.utils.unpage
import io.reactivex.Maybe
import io.reactivex.Single
import io.reactivex.rxkotlin.toFlowable
import java.util.concurrent.CompletionStage
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
@GQLType("User")
class GQLUser @Inject constructor(
        private val metaStore: MetaStore,
        private val dataStore: DataStore,
        private val rateLimiter: RateLimiter,
        accessManager: AccessManager
) : Operations(accessManager) {

    @GQLFetcher
    fun usageLimitAssigned(env: DataFetchingEnvironment): CompletionStage<Boolean> {
        return withAccess(env) {
            val user = env.getSource<User>()
            Single.just(user.usageLimitId != null)
        }
    }

    @GQLFetcher
    fun usageLimit(env: DataFetchingEnvironment): CompletionStage<UsageLimit> {
        return withAccess(env) {
            val user = env.getSource<User>()
            metaStore.getUsageLimitOrDefault(user.usageLimitId)
        }
    }

    @GQLFetcher
    fun aggregatedUsageLimit(env: DataFetchingEnvironment): CompletionStage<UsageLimit> {
        return withAccess(env) {
            val user = env.getSource<User>()
            metaStore.getAggregatedUsageLimits(user)
        }
    }

    @GQLFetcher
    fun usageRemaining(env: DataFetchingEnvironment): CompletionStage<Map<UsageLimitId, Long>> {
        return withAccess(env) {
            val user = env.getSource<User>()

            metaStore.getAggregatedUsageLimits(user)
                    .flatMap { limits ->
                        // Create tmp token and fetch rate limits
                        rateLimiter.getStatus(Token(user, null, emptyMap(), limits, ""))
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
                                                        .plus(UsageLimitId.maxDataExports to (limits.values[UsageLimitId.maxDataExports]!! - nrOfExports))
                                                        .plus(UsageLimitId.maxDataStreams to (limits.values[UsageLimitId.maxDataStreams]!! - nrOfStreams))
                                            }
                                }
                    }
        }
    }

    @GQLFetcher
    fun membership(env: DataFetchingEnvironment): CompletionStage<Membership> {
        return withAccessMaybe(env) {
            val user = env.getSource<User>()
            val datasetId = env.getArgument<String>("id")

            metaStore.getAggregatedDatasetMembershipsForUser(user.id!!, datasetId)
                    .flatMapMaybe { memberships ->
                        memberships[datasetId]?.let { Maybe.just(it) } ?: Maybe.empty()
                    }
                    .map { Membership(userId = user.id!!, datasetId = datasetId, roleIds = it.assignedRoleIds) }
        }
    }

    @GQLFetcher
    fun memberships(env: DataFetchingEnvironment): CompletionStage<GraphQLPage<Membership>> {
        return withAccess(env) { token ->
            val user = env.getSource<User>()
            val offset = cursorFrom(env)?.decodeFromBase64()?.toIntOrNull() ?: 0
            val limit = limitFrom(env)

            getAggregatedGrants(token, user)
                    .flatMap { aggregatedGrants ->
                        env.getFilter(mapOf(
                                listOf("dataset") to { subFilter ->
                                    unpage { cursor ->
                                        metaStore.queryDatasets(
                                                filter = And(
                                                        // Search only in datasets the User is a member of
                                                        In(DatasetField.ID, aggregatedGrants.keys),
                                                        // Also apply the sub filter on dataset
                                                        subFilter
                                                ),
                                                cursor = cursor
                                        )
                                    }.map { it.id!! }.toSet()
                                },
                                listOf("aggregatedGrant") to { subFilter ->
                                    Single.just(aggregatedGrants)
                                            .map { grants -> grants.filter { it.value.matches(subFilter) }.map { it.key } }
                                            .map { it.toSet() }
                                }
                        ))
                                .map { filter ->
                                    val subset = aggregatedGrants.map { DatasetMembership(datasetId = it.key) }.filter { it.matches(filter) }
                                    Pair(subset.drop(offset).take(limit + 1), subset.size)
                                }
                                .map { (memberships, totalCount) ->
                                    GraphQLPage(
                                            items = memberships.map {
                                                Membership(
                                                        userId = user.id!!,
                                                        datasetId = it.datasetId,
                                                        roleIds = it.assignedRoleIds
                                                )
                                            },
                                            cursor = if (memberships.size > limit) (offset + limit).toString().encodeAsBase64() else null
                                    ) { Single.just(totalCount.toLong()) }
                                }
                    }
        }
    }

    @GQLFetcher
    fun client(env: DataFetchingEnvironment): CompletionStage<Client> {
        return withAccessMaybe(env) {
            metaStore.getClient(env.getArgument("id")).filter { it.userId == env.getSource<User>().id }
        }
    }

    @GQLFetcher
    fun clients(env: DataFetchingEnvironment): CompletionStage<GraphQLPage<Client>> {
        return withAccess(env) {
            val user = env.getSource<User>()
            val filter = And(Eq(ClientField.USER_ID, user.id!!), env.getFilter())
            metaStore.queryClients(
                    limit = limitFrom(env),
                    cursor = cursorFrom(env),
                    filter = filter
            )
                    .map { GraphQLPage(it.items, it.cursor) { metaStore.countClients(filter) } }
        }
    }

    @GQLFetcher
    fun dataset(env: DataFetchingEnvironment): CompletionStage<Dataset> {
        return withAccessMaybe(env) {
            val user = env.getSource<User>()
            val datasetId = env.getArgument<String>("id")

            metaStore.getDataset(datasetId)
                    .zipWith(metaStore.getAggregatedDatasetMembershipsForUser(user.id!!, datasetId).toMaybe(), ::Pair)
                    .filter { (dataset, memberships) -> !dataset.locked && memberships.containsKey(datasetId) }
                    .map { it.first }
        }
    }

    @GQLFetcher
    fun datasets(env: DataFetchingEnvironment): CompletionStage<GraphQLPage<Dataset>> {
        return withAccess(env) {
            val user = env.getSource<User>()

            metaStore.getAggregatedDatasetMembershipsForUser(user.id!!)
                    .flatMap { memberships ->
                        val filter = And(
                                Eq(DatasetField.LOCKED, false),
                                In(DatasetField.ID, memberships.values.map { it.datasetId }.sorted().toSet()),
                                env.getFilter()
                        )
                        metaStore.queryDatasets(
                                filter,
                                limitFrom(env),
                                cursorFrom(env)
                        ).map { result -> GraphQLPage(result.items, result.cursor) { metaStore.countDatasets(filter) } }
                    }
        }
    }

    @GQLFetcher
    fun accessRequest(env: DataFetchingEnvironment): CompletionStage<AccessRequest> {
        return withAccessMaybe(env) {
            metaStore.getAccessRequest(env.getArgument("id"))
        }
    }

    @GQLFetcher
    fun accessRequests(env: DataFetchingEnvironment): CompletionStage<GraphQLPage<AccessRequest>> {
        return withAccess(env) {
            val user = env.getSource<User>()
            val filter = Eq(AccessRequestField.USER_ID, user.id!!)
            metaStore.queryAccessRequests(
                    filter = filter,
                    limit = limitFrom(env),
                    cursor = cursorFrom(env)
            )
                    .map { GraphQLPage(it.items, it.cursor) { metaStore.countAccessRequests(filter) } }
        }
    }

    @GQLFetcher
    fun activeStream(env: DataFetchingEnvironment): CompletionStage<DataStream> {
        return withAccessMaybe(env) {
            metaStore.getDataStream(env.getArgument("id"))
        }
    }

    @GQLFetcher
    fun activeStreams(env: DataFetchingEnvironment): CompletionStage<GraphQLPage<DataStream>> {
        return withAccess(env) { _ ->
            val source = env.getSource<User>()
            val filter = And(Eq(DataStreamField.USER_ID, source.id!!), env.getFilter())
            metaStore.queryDataStreams(
                    filter = filter,
                    limit = limitFrom(env),
                    cursor = cursorFrom(env),
            )
                    .map { GraphQLPage(it.items, it.cursor) { metaStore.countDataStreams(filter) } }
        }
    }

    @GQLFetcher
    fun export(env: DataFetchingEnvironment): CompletionStage<DataExport> {
        return withAccessMaybe(env) {
            val user = env.getSource<User>()
            metaStore.getDataExport(env.getArgument("id"))
                    .filter { it.userId == user.id }
        }
    }

    @GQLFetcher
    fun exports(env: DataFetchingEnvironment): CompletionStage<GraphQLPage<DataExport>> {
        return withAccess(env) {
            val user = env.getSource<User>()
            val filter = And(Eq(DataExportField.USER_ID, user.id!!), env.getFilter())
            metaStore.queryDataExports(
                    filter = filter,
                    limit = limitFrom(env),
                    cursor = cursorFrom(env)
            )
                    .map { GraphQLPage(it.items, it.cursor) { metaStore.countDataExports(filter) } }
        }
    }

    @GQLFetcher
    fun team(env: DataFetchingEnvironment): CompletionStage<Team> {
        return withAccessMaybe(env) {
            val user = env.getSource<User>()
            metaStore.getTeam(env.getArgument("id"))
                    .filter { team -> user.teamMemberships.any { team.id == it.teamId } }
        }
    }

    @GQLFetcher
    fun teams(env: DataFetchingEnvironment): CompletionStage<GraphQLPage<Team>> {
        return withAccess(env) { _ ->
            val user = env.getSource<User>()

            env.getFilter(mapOf(
                    listOf("users") to { subFilter ->
                        // Find teams matching the supplied user filter
                        unpage { cursor ->
                            metaStore.queryUsers(
                                    filter = And(
                                            // Users that are member of the same teams the current user is a member of
                                            In(
                                                    Field(UserField.TEAM_MEMBERSHIPS, TeamMembershipField.TEAM_ID),
                                                    user.teamMemberships.map { it.teamId }.toSet()
                                            ),
                                            subFilter
                                    ),
                                    cursor = cursor
                            )
                        }.flatMap { user -> user.teamMemberships.map { it.teamId }.toFlowable() }.toSet()
                    },
                    listOf(
                            "users",
                            "user"
                    ) to { subFilter ->
                        unpage { cursor ->
                            metaStore.queryUsers(
                                    filter = And(
                                            // Users that are member of the same teams the current user is a member of
                                            In(
                                                    Field(UserField.TEAM_MEMBERSHIPS, TeamMembershipField.TEAM_ID),
                                                    user.teamMemberships.map { it.teamId }.toSet()
                                            ),
                                            subFilter
                                    ),
                                    cursor = cursor
                            )
                        }.map { it.id!! }.toSet()
                    }
            ))
                    .flatMap { rootFilter ->
                        val query = And(In("id", user.teamMemberships.map { it.teamId }.toSet()), rootFilter)
                        metaStore.queryTeams(query, limitFrom(env), cursorFrom(env))
                                .map { GraphQLPage(it.items, it.cursor) { metaStore.countTeams(query) } }
                    }
        }
    }

    @GQLFetcher
    fun metrics(env: DataFetchingEnvironment): CompletionStage<GraphQLPage<MetricId>> {
        return withAccess(env) {
            val user = env.getSource<User>()

            env.getFilter(
                    mapOf(
                            listOf("datasets") to { subFilter ->
                                unpage { cursor ->
                                    metaStore.queryDatasets(
                                            filter = And(
                                                    // Search only in datasets the User is a member of
                                                    In(DatasetField.ID, user.datasetMemberships.map { it.datasetId }.toSet()),
                                                    // Also apply the sub filter on dataset
                                                    subFilter
                                            ),
                                            cursor = cursor
                                    )
                                }.map { it.id!! }.toSet()
                            })
            )
                    .flatMap { filter ->
                        val q = MetaQuery(
                                dataRange = DataRange(
                                        user.datasetMemberships.map { it.datasetId }
                                ),
                                fields = listOf(MetaField.metric),
                                filter = filter,
                                limit = limitFrom(env),
                                cursor = cursorFrom(env)
                        )
                        dataStore.getMetadata(q)
                                .map { result ->
                                    GraphQLPage(
                                            result.items.map { MetricId(it.metricName()?.getFullyQualifiedId()!!, user) },
                                            result.cursor
                                    ) { dataStore.countMetadata(q) }
                                }
                    }
        }
    }

    private fun getAggregatedGrants(token: Token, user: User): Single<Map<String, Grant>> {
        return if (token.user.id == user.id) {
            Single.just(token.grants)
        } else {
            metaStore.getAggregatedGrantsForUser(user.id!!)
        }
    }
}