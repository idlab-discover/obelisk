package idlab.obelisk.services.pub.catalog.types

import com.github.benmanes.caffeine.cache.Caffeine
import graphql.schema.DataFetchingEnvironment
import hu.akarnokd.rxjava3.bridge.RxJavaBridge
import idlab.obelisk.definitions.*
import idlab.obelisk.definitions.catalog.*
import idlab.obelisk.definitions.catalog.codegen.*
import idlab.obelisk.definitions.data.DataStore
import idlab.obelisk.definitions.data.IndexField
import idlab.obelisk.definitions.data.MetaField
import idlab.obelisk.definitions.data.MetaQuery
import idlab.obelisk.definitions.framework.OblxConfig
import idlab.obelisk.services.pub.catalog.impl.*
import idlab.obelisk.services.pub.catalog.types.util.*
import idlab.obelisk.services.pub.catalog.types.util.getInvites
import idlab.obelisk.utils.service.utils.unpage
import io.reactivex.Maybe
import io.reactivex.Single
import io.vertx.core.json.Json
import org.redisson.api.RedissonClient
import java.util.concurrent.CompletionStage
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
@GQLType("Dataset")
class GQLDataset @Inject constructor(
    private val redis: RedissonClient,
    private val dataStore: DataStore,
    private val metaStore: MetaStore,
    config: OblxConfig,
    accessManager: AccessManager
) : Operations(accessManager) {

    private val cache = Caffeine.newBuilder().expireAfterWrite(
        config.getLong(ENV_DATASET_STATS_CACHE_TIME_MS, DEFAULT_DATASET_STATS_CACHE_TIME_MS),
        TimeUnit.MILLISECONDS
    ).maximumSize(config.getLong(ENV_DATASET_STATS_CACHE_MAX_ENTRIES, DEFAULT_DATASET_STATS_CACHE_MAX_ENTRIES))
        .build<CacheKey, Any>()

    @GQLFetcher
    fun accessRequest(env: DataFetchingEnvironment): CompletionStage<AccessRequest> {
        return withAccessMaybe(env) {
            metaStore.getAccessRequest(env.getArgument("id"))
        }
    }

    @GQLFetcher
    fun accessRequests(env: DataFetchingEnvironment): CompletionStage<GraphQLPage<AccessRequest>> {
        return withAccess(env) {
            val dataset = env.getSource<Dataset>()
            val filter = Eq(AccessRequestField.DATASET_ID, dataset.id!!)
            metaStore.queryAccessRequests(
                filter = filter,
                limit = limitFrom(env),
                cursor = cursorFrom(env)
            )
                .map { GraphQLPage(it.items, it.cursor) { metaStore.countAccessRequests(filter) } }
        }
    }

    @GQLFetcher
    fun member(env: DataFetchingEnvironment): CompletionStage<User> {
        return withAccessMaybe(env) {
            val userId = env.getArgument<String>("id")
            val dataset = env.getSource<Dataset>()
            metaStore.getUser(userId).flatMap { user ->
                if (user.datasetMemberships.any { dataset.id == it.datasetId }) {
                    Maybe.just(user)
                } else {
                    Maybe.empty()
                }
            }
        }
    }

    @GQLFetcher
    fun members(env: DataFetchingEnvironment): CompletionStage<GraphQLPage<User>> {
        return withAccess(env) {
            val dataset = env.getSource<Dataset>()
            val filter = And(
                Eq(Field(UserField.DATASET_MEMBERSHIPS, DatasetMembershipField.DATASET_ID), dataset.id!!),
                env.getFilter()
            )
            metaStore.queryUsers(
                filter = filter,
                limit = limitFrom(env),
                cursor = cursorFrom(env)
            )
                .map { GraphQLPage(it.items, it.cursor) { metaStore.countUsers(filter) } }
        }
    }

    @GQLFetcher
    fun team(env: DataFetchingEnvironment): CompletionStage<Team> {
        return withAccessMaybe(env) {
            val teamId = env.getArgument<String>("id")
            val dataset = env.getSource<Dataset>()
            metaStore.getTeam(teamId).flatMap { team ->
                if (team.datasetMemberships.any { dataset.id == it.datasetId }) {
                    Maybe.just(team)
                } else {
                    Maybe.empty()
                }
            }
        }
    }

    @GQLFetcher
    fun teams(env: DataFetchingEnvironment): CompletionStage<GraphQLPage<Team>> {
        return withAccess(env) {
            val dataset = env.getSource<Dataset>()
            val filter = And(
                Eq(Field(TeamField.DATASET_MEMBERSHIPS, DatasetMembershipField.DATASET_ID), dataset.id!!),
                env.getFilter()
            )
            metaStore.queryTeams(
                filter = filter,
                limit = limitFrom(env),
                cursor = cursorFrom(env),
            )
                .map { GraphQLPage(it.items, it.cursor) { metaStore.countTeams(filter) } }
        }
    }

    @GQLFetcher
    fun role(env: DataFetchingEnvironment): CompletionStage<Role> {
        return withAccessMaybe(env) {
            val dataset = env.getSource<Dataset>()
            val roleId = env.getArgument<String>("id")
            metaStore.getRole(roleId)
                .filter { it.datasetId == dataset.id }
        }
    }

    @GQLFetcher
    fun roles(env: DataFetchingEnvironment): CompletionStage<GraphQLPage<Role>> {
        return withAccess(env) {
            val dataset = env.getSource<Dataset>()
            val filter = And(Eq(RoleField.DATASET_ID, dataset.id!!), env.getFilter())
            metaStore.queryRoles(
                filter = filter,
                limit = limitFrom(env),
                cursor = cursorFrom(env)
            )
                .map { GraphQLPage(it.items, it.cursor) { metaStore.countRoles(filter) } }
        }
    }

    @GQLFetcher
    fun metaStats(env: DataFetchingEnvironment): CompletionStage<MetaStats> {
        return withOptionalAccessMaybe(env) { optionalToken ->
            val dataset = env.getSource<Dataset>()
            if (optionalToken.token != null && (dataset.published || optionalToken.token.grants.containsKey(dataset.id!!) || optionalToken.token.user.platformManager)) {
                // Pre count events for metrics (can be re-used twice)
                cache.getOrLoad(CacheKey(dataset.id!!, "metaStats")) {
                    unpage { cursor ->
                        dataStore.getMetadata(
                            MetaQuery(
                                dataRange = DataRange.fromDatasetId(dataset.id!!),
                                fields = listOf(
                                    MetaField.metric,
                                    MetaField.started,
                                    MetaField.lastUpdate,
                                    MetaField.count
                                ),
                                cursor = cursor
                            )
                        )
                    }.toList()
                }
                    .flatMapMaybe {
                        Maybe.just(MetaStats(dataset.id!!, it))
                    }
            } else {
                Maybe.empty()
            }
        }
    }

    @GQLFetcher
    fun userOrigin(env: DataFetchingEnvironment): CompletionStage<Origin> {
        return withAccessMaybe(env) {
            val dataset = env.getSource<Dataset>()
            metaStore.getUser(env.getArgument("id"))
                .filter { it.datasetMemberships.any { mem -> mem.datasetId == dataset.id } && !it.hideOrigin }
                .map { Origin(dataset.id!!, it) }
        }
    }

    @GQLFetcher
    fun clientOrigin(env: DataFetchingEnvironment): CompletionStage<Origin> {
        return withAccessMaybe(env) {
            val dataset = env.getSource<Dataset>()
            metaStore.getClient(env.getArgument("id"))
                .flatMap { client ->
                    metaStore.getUser(client.userId)
                        .filter { it.datasetMemberships.any { mem -> mem.datasetId == dataset.id } && !it.hideOrigin }
                        .map { Origin(dataset.id!!, it) }
                }
        }
    }

    @GQLFetcher
    fun origins(env: DataFetchingEnvironment): CompletionStage<PagedResult<Origin>> {
        return withAccess(env) { token ->
            val dataset = env.getSource<Dataset>()

            dataStore.getMetadata(
                MetaQuery(
                    dataRange = DataRange(listOf(dataset.id!!)),
                    fields = listOf(MetaField.producer),
                    limit = limitFrom(env),
                    cursor = cursorFrom(env),
                    // Apply readFilter the User has!!
                    filter = token.grants[dataset.id!!]?.readFilter ?: SELECT_ALL
                )
            ).flatMap { result ->
                // Filter out users / clients that don't want to be listed
                unpage { userCursor ->
                    metaStore.queryUsers(
                        filter = Neq(UserField.HIDE_ORIGIN, true),
                        cursor = userCursor
                    )
                }.toList()
                    .flatMap { users ->
                        unpage { clientCursor ->
                            metaStore.queryClients(
                                filter = Neq(ClientField.HIDE_ORIGIN, true),
                                cursor = clientCursor
                            )
                        }.toList()
                            .map { clients ->
                                val idToUsers = users.associateBy { it.id!! }
                                val idToClients = clients.associateBy { it.id!! }
                                PagedResult(items = result.items
                                    .filter {
                                        if (it.producer!!.clientId == null) idToUsers.containsKey(it.producer!!.userId) else idToClients.containsKey(
                                            it.producer!!.clientId!!
                                        )
                                    }
                                    .map {
                                        Origin(
                                            dataset.id!!,
                                            if (it.producer!!.clientId == null) idToUsers[it.producer!!.userId]!! else idToClients[it.producer!!.clientId!!]!!
                                        )
                                    }, cursor = result.cursor
                                )
                            }
                    }
            }
        }
    }

    @GQLFetcher
    fun metric(env: DataFetchingEnvironment): CompletionStage<Metric> {
        return withAccessMaybe(env) { token ->
            val dataset = env.getSource<Dataset>()
            val metricId = MetricName(env.getArgument("id"))
            val defaultMetric = Metric(name = metricId, datasetId = dataset.id!!)

            dataStore.countMetadata(
                MetaQuery(
                    dataRange = DataRange(
                        datasets = listOf(dataset.id!!),
                        metrics = listOf(metricId)
                    ),
                    filter = And(token.readFilterForDataset(dataset.id!!)),
                    fields = listOf(MetaField.metric)
                )
            )
                .flatMapMaybe { count ->
                    if (count >= 1) {
                        metaStore.queryMetrics(
                            And(
                                Eq(MetricField.DATASET_ID, dataset.id!!),
                                Eq("fqMetricId", metricId.getFullyQualifiedId())
                            ), limit = 1
                        )
                            .map { result -> result.items.firstOrNull() ?: defaultMetric }
                            .toMaybe()
                    } else {
                        Maybe.empty()
                    }
                }
        }
    }

    @GQLFetcher
    fun metrics(env: DataFetchingEnvironment): CompletionStage<GraphQLPage<Metric>> {
        return withAccess(env) { token ->
            val dataset = env.getSource<Dataset>()
            val q = MetaQuery(
                dataRange = DataRange(listOf(dataset.id!!)),
                fields = listOf(MetaField.metric),
                // Apply User readFilter
                filter = And(token.readFilterForDataset(dataset.id!!), env.getFilter())
            )

            dataStore.getMetadata(q.copy(limit = limitFrom(env), cursor = cursorFrom(env)))
                .flatMap { result ->
                    unpage { cursor ->
                        metaStore.queryMetrics(
                            filter = And(
                                Eq(MetricField.DATASET_ID, dataset.id!!),
                                In(
                                    "fqMetricId",
                                    result.items.mapNotNull { it.metricName() }.map { it.getFullyQualifiedId() }.toSet()
                                )
                            ),
                            cursor = cursor,
                            limit = LOAD_PAGE_SIZE
                        )
                    }.toList()
                        .map { combineMetricsInfo(dataset.id!!, result.items, it) }
                        .map {
                            GraphQLPage(it, result.cursor) {
                                dataStore.countMetadata(q)
                            }
                        }
                }
        }
    }

    @GQLFetcher
    fun thing(env: DataFetchingEnvironment): CompletionStage<Thing> {
        return withAccessMaybe(env) { token ->
            val dataset = env.getSource<Dataset>()
            val thingId = env.getArgument<String>("id")
            val defaultThing = Thing(sourceId = thingId, datasetId = dataset.id!!)

            dataStore.countMetadata(
                MetaQuery(
                    dataRange = DataRange(datasets = listOf(dataset.id!!)),
                    filter = And(token.readFilterForDataset(dataset.id!!), Eq(IndexField.source, thingId)),
                    fields = listOf(MetaField.source)
                )
            )
                .flatMapMaybe { count ->
                    if (count >= 1) {
                        metaStore.queryThings(
                            And(
                                Eq(ThingField.DATASET_ID, dataset.id!!),
                                Eq(ThingField.SOURCE_ID, thingId)
                            ), limit = 1
                        )
                            .map { result -> result.items.firstOrNull() ?: defaultThing }
                            .toMaybe()
                    } else {
                        Maybe.empty()
                    }
                }
        }
    }

    @GQLFetcher
    fun things(env: DataFetchingEnvironment): CompletionStage<GraphQLPage<Thing>> {
        return withAccess(env) { token ->
            val dataset = env.getSource<Dataset>()
            val q = MetaQuery(
                dataRange = DataRange(listOf(dataset.id!!)),
                fields = listOf(MetaField.source),
                filter = And(token.readFilterForDataset(dataset.id!!), env.getFilter())
            )

            dataStore.getMetadata(q.copy(limit = limitFrom(env), cursor = cursorFrom(env)))
                .flatMap { result ->
                    unpage { cursor ->
                        metaStore.queryThings(
                            filter = And(
                                Eq(ThingField.DATASET_ID, dataset.id!!),
                                In(ThingField.SOURCE_ID, result.items.mapNotNull { it.source }.toSet())
                            ),
                            cursor = cursor,
                            limit = LOAD_PAGE_SIZE
                        )
                    }.toList()
                        .map { combineThingsInfo(dataset.id!!, result.items, it) }
                        .map { GraphQLPage(it, result.cursor) { dataStore.countMetadata(q) } }
                }
        }
    }

    // TODO: Implemented as in service filtering and paging, can be optimized!
    @GQLFetcher
    fun invites(env: DataFetchingEnvironment): CompletionStage<PagedResult<Invite>> {
        return withAccess(env) {
            val dataset = env.getSource<Dataset>()
            val offset = cursorFrom(env)?.let { InviteCursor(it).offset } ?: 0
            val limit = limitFrom(env)

            redis.getInvites()
                .valueIterator()
                .to(RxJavaBridge.toV2Flowable())
                .map { Json.decodeValue(it, Invite::class.java) }
                .filter { it.datasetId == dataset.id }
                .skip(offset.toLong())
                .limit(limit.toLong())
                .toList()
                .map { PagedResult(it, InviteCursor(offset + limit).asCursor()) }
        }
    }

    // TODO: Implemented using multimap, would be more efficient as a map in map.
    @GQLFetcher
    fun invite(env: DataFetchingEnvironment): CompletionStage<Invite> {
        return withAccess(env) {
            val dataset = env.getSource<Dataset>()
            val id = env.getArgument<String>("id")
            redis.getInvites()
                .get(redisInviteKeyOrPattern(datasetId = dataset.id!!, inviteId = id))
                .to(RxJavaBridge.toV2Maybe())
                .map { Json.decodeValue(it, Invite::class.java) }
                .toSingle()
        }
    }

    @GQLFetcher
    fun archived(env: DataFetchingEnvironment): CompletionStage<Boolean> {
        return withAccess(env) {
            val dataset = env.getSource<Dataset>()
            Single.just(dataset.locked)
        }
    }

}