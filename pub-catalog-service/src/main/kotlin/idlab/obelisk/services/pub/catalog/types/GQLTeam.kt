package idlab.obelisk.services.pub.catalog.types

import graphql.GraphQL
import graphql.schema.DataFetchingEnvironment
import hu.akarnokd.rxjava3.bridge.RxJavaBridge
import idlab.obelisk.definitions.*
import idlab.obelisk.definitions.catalog.*
import idlab.obelisk.definitions.catalog.codegen.*
import idlab.obelisk.services.pub.catalog.impl.*
import idlab.obelisk.services.pub.catalog.types.util.*
import idlab.obelisk.services.pub.catalog.types.util.getTeamInvites
import idlab.obelisk.utils.service.reactive.toSet
import idlab.obelisk.utils.service.utils.Base64.decodeFromBase64
import idlab.obelisk.utils.service.utils.Base64.encodeAsBase64
import idlab.obelisk.utils.service.utils.matches
import idlab.obelisk.utils.service.utils.unpage
import io.reactivex.Maybe
import io.reactivex.Single
import io.vertx.core.json.Json
import org.redisson.api.RedissonClient
import java.util.concurrent.CompletionStage
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
@GQLType("Team")
class GQLTeam @Inject constructor(
    accessManager: AccessManager,
    private val metaStore: MetaStore,
    private val redis: RedissonClient
) :
    Operations(accessManager) {

    @GQLFetcher
    fun usagePlanAssigned(env: DataFetchingEnvironment): CompletionStage<Boolean> {
        return withAccess(env) {
            val team = env.getSource<Team>()
            Single.just(team.usagePlanId != null)
        }
    }

    @GQLFetcher
    fun usagePlan(env: DataFetchingEnvironment): CompletionStage<UsagePlan> {
        return withAccess(env) {
            val team = env.getSource<Team>()
            metaStore.getUsagePlanOrDefault(team.usagePlanId)
        }
    }

    @GQLFetcher
    fun usersRemaining(env: DataFetchingEnvironment): CompletionStage<Int> {
        return withAccess(env) {
            val team = env.getSource<Team>()
            metaStore.getUsagePlanOrDefault(team.usagePlanId)
                .flatMap { plan ->
                    metaStore.countUsers(Eq(Field(UserField.TEAM_MEMBERSHIPS, TeamMembershipField.TEAM_ID), team.id!!))
                        .map { plan.maxUsers - it.toInt() }
                }
        }
    }

    @GQLFetcher
    fun clientsRemaining(env: DataFetchingEnvironment): CompletionStage<Int> {
        return withAccess(env) {
            val team = env.getSource<Team>()
            metaStore.getUsagePlanOrDefault(team.usagePlanId)
                .flatMap { plan ->
                    metaStore.countClients(Eq(ClientField.TEAM_ID, team.id!!)).map { plan.maxClients - it.toInt() }
                }
        }
    }

    @GQLFetcher
    fun user(env: DataFetchingEnvironment): CompletionStage<TeamUser> {
        return withAccessMaybe(env) {
            val team = env.getSource<Team>()
            metaStore.getUser(env.getArgument("id"))
                .filter { user -> user.teamMemberships.any { it.teamId == team.id } }
                .map { TeamUser(team.id!!, it.id!!) }
        }
    }

    @GQLFetcher
    fun users(env: DataFetchingEnvironment): CompletionStage<GraphQLPage<TeamUser>> {
        return withAccess(env) {
            val teamId = env.getSource<Team>().id!!

            env.getFilter(mapOf(
                listOf("user") to { subFilter ->
                    unpage { cursor ->
                        metaStore.queryUsers(
                            filter = And(
                                // Potential users must be a member of the team
                                Eq(Field(UserField.TEAM_MEMBERSHIPS, TeamMembershipField.TEAM_ID), teamId),
                                // apply user-supplied subfilter
                                subFilter
                            ),
                            cursor = cursor
                        )
                    }.map { it.id!! }.toSet()
                }
            )).flatMap { rootFilter ->
                val filter = And(
                    Eq(Field(UserField.TEAM_MEMBERSHIPS, TeamMembershipField.TEAM_ID), teamId),
                    rootFilter
                )
                metaStore.queryUsers(
                    limit = limitFrom(env),
                    cursor = cursorFrom(env),
                    filter = filter
                )
                    .map { result ->
                        GraphQLPage(
                            cursor = result.cursor,
                            items = result.items.map { TeamUser(teamId, it.id!!) }
                        ) {
                            metaStore.countUsers(filter)
                        }
                    }
            }
        }
    }

    @GQLFetcher
    fun dataset(env: DataFetchingEnvironment): CompletionStage<Dataset> {
        return withAccessMaybe(env) {
            val team = env.getSource<Team>()
            val datasetId = env.getArgument<String>("id")
            team.datasetMemberships.find { it.datasetId == datasetId }?.let {
                metaStore.getDataset(datasetId)
            } ?: Maybe.empty()
        }
    }

    @GQLFetcher
    fun datasets(env: DataFetchingEnvironment): CompletionStage<GraphQLPage<Dataset>> {
        return withAccess(env) {
            val team = env.getSource<Team>()
            val filter = And(
                Eq(DatasetField.LOCKED, false),
                In(DatasetField.ID, team.datasetMemberships.map { it.datasetId }.sorted().toSet()),
                env.getFilter()
            )
            metaStore.queryDatasets(
                filter,
                limitFrom(env),
                cursorFrom(env)
            ).map { result -> GraphQLPage(result.items, result.cursor) { metaStore.countDatasets(filter) } }
        }
    }

    @GQLFetcher
    fun membership(env: DataFetchingEnvironment): CompletionStage<Membership> {
        return withAccessMaybe(env) {
            val team = env.getSource<Team>()
            val datasetId = env.getArgument<String>("id")
            team.datasetMemberships.find { it.datasetId == datasetId }?.let {
                Maybe.just(
                    Membership(
                        teamId = team.id!!,
                        datasetId = datasetId,
                        roleIds = it.assignedRoleIds
                    )
                )
            } ?: Maybe.empty()
        }
    }

    @GQLFetcher
    fun memberships(env: DataFetchingEnvironment): CompletionStage<GraphQLPage<Membership>> {
        return withAccess(env) {
            val team = env.getSource<Team>()
            val offset = cursorFrom(env)?.decodeFromBase64()?.toIntOrNull() ?: 0
            val limit = limitFrom(env)

            env.getFilter(mapOf(
                listOf("dataset") to { subFilter ->
                    unpage { cursor ->
                        metaStore.queryDatasets(
                            filter = And(
                                // Search only in datasets the Team is a member of
                                In(DatasetField.ID, team.datasetMemberships.map { it.datasetId }.toSet()),
                                // Also apply the sub filter on dataset
                                subFilter
                            ),
                            cursor = cursor
                        )
                    }.map { it.id!! }.toSet()
                },
                listOf("aggregatedGrant") to { subFilter ->
                    metaStore.getAggregatedGrantsForTeam(team.id!!)
                        .map { grants -> grants.filter { it.value.matches(subFilter) }.map { it.key } }
                        .map { it.toSet() }
                }
            ))
                .map { filter ->
                    val subset =
                        team.datasetMemberships.filter { it.matches(filter) }
                    Pair(subset.drop(offset).take(limit + 1), subset.size)
                }
                .map { (memberships, totalCount) ->
                    GraphQLPage(
                        items = memberships.map {
                            Membership(
                                teamId = team.id!!,
                                datasetId = it.datasetId,
                                roleIds = it.assignedRoleIds
                            )
                        },
                        cursor = if (memberships.size > limit) (offset + limit).toString().encodeAsBase64() else null
                    ) { Single.just(totalCount.toLong()) }
                }
        }
    }

    @GQLFetcher
    fun invite(env: DataFetchingEnvironment): CompletionStage<TeamInvite> {
        return withAccessMaybe(env) {
            val team = env.getSource<Team>()
            redis.getTeamInvites()[redisInviteKeyOrPattern(datasetId = team.id!!, inviteId = env.getArgument("id"))]
                .to(RxJavaBridge.toV2Maybe())
                .map { Json.decodeValue(it, TeamInvite::class.java) }
        }
    }

    @GQLFetcher
    fun invites(env: DataFetchingEnvironment): CompletionStage<PagedResult<TeamInvite>> {
        return withAccess(env) {
            val team = env.getSource<Team>()
            val offset = cursorFrom(env)?.let { InviteCursor(it).offset } ?: 0
            val limit = limitFrom(env)
            redis.getTeamInvites()
                .valueIterator()
                .to(RxJavaBridge.toV2Flowable())
                .map { Json.decodeValue(it, TeamInvite::class.java) }
                .filter { it.teamId == team.id }
                .skip(offset.toLong())
                .limit(limit.toLong())
                .toList()
                .map { PagedResult(it, InviteCursor(offset + limit).asCursor()) }
        }
    }

    @GQLFetcher
    fun client(env: DataFetchingEnvironment): CompletionStage<Client> {
        return withAccessMaybe(env) {
            val team = env.getSource<Team>()
            metaStore.getClient(env.getArgument("id"))
                .filter { it.teamId == team.id }
        }
    }

    @GQLFetcher
    fun clients(env: DataFetchingEnvironment): CompletionStage<GraphQLPage<Client>> {
        return withAccess(env) {
            val team = env.getSource<Team>()
            val filter = And(Eq(ClientField.TEAM_ID, team.id!!), env.getFilter())
            metaStore.queryClients(
                limit = limitFrom(env),
                cursor = cursorFrom(env),
                filter = filter
            )
                .map { GraphQLPage(it.items, it.cursor) { metaStore.countClients(filter) } }
        }
    }

    @GQLFetcher
    fun accessRequest(env: DataFetchingEnvironment): CompletionStage<AccessRequest> {
        return withAccessMaybe(env) {
            val team = env.getSource<Team>()
            metaStore.getAccessRequest(env.getArgument("id"))
                .filter { it.teamId == team.id }
        }
    }

    @GQLFetcher
    fun accessRequests(env: DataFetchingEnvironment): CompletionStage<GraphQLPage<AccessRequest>> {
        return withAccess(env) {
            val team = env.getSource<Team>()
            val filter = Eq(AccessRequestField.TEAM_ID, team.id!!)
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
            val team = env.getSource<Team>()
            metaStore.getDataStream(env.getArgument("id"))
                .filter { it.teamId == team.id }
        }
    }

    @GQLFetcher
    fun activeStreams(env: DataFetchingEnvironment): CompletionStage<GraphQLPage<DataStream>> {
        return withAccess(env) { _ ->
            val source = env.getSource<Team>()
            val filter = And(Eq(DataStreamField.TEAM_ID, source.id!!), env.getFilter())
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
            val team = env.getSource<Team>()
            metaStore.getDataExport(env.getArgument("id"))
                .filter { it.teamId == team.id }
        }
    }

    @GQLFetcher
    fun exports(env: DataFetchingEnvironment): CompletionStage<GraphQLPage<DataExport>> {
        return withAccess(env) {
            val team = env.getSource<Team>()
            val filter = And(Eq(DataExportField.TEAM_ID, team.id!!), env.getFilter())
            metaStore.queryDataExports(
                filter = filter,
                limit = limitFrom(env),
                cursor = cursorFrom(env)
            )
                .map { GraphQLPage(it.items, it.cursor) { metaStore.countDataExports(filter) } }
        }
    }

}