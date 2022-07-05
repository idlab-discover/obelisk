package idlab.obelisk.services.pub.catalog.types

import graphql.schema.DataFetchingEnvironment
import hu.akarnokd.rxjava3.bridge.RxJavaBridge
import idlab.obelisk.definitions.Eq
import idlab.obelisk.definitions.catalog.*
import idlab.obelisk.definitions.catalog.codegen.ClientField
import idlab.obelisk.definitions.catalog.codegen.TeamNullableField
import idlab.obelisk.definitions.catalog.codegen.TeamUpdate
import idlab.obelisk.definitions.data.DataStore
import idlab.obelisk.definitions.messaging.MessageBroker
import idlab.obelisk.services.pub.catalog.impl.*
import idlab.obelisk.services.pub.catalog.types.util.*
import idlab.obelisk.services.pub.catalog.types.util.getTeamInvites
import idlab.obelisk.utils.service.reactive.flatMapSingle
import io.reactivex.Single
import io.vertx.core.json.Json
import org.redisson.api.RedissonClient
import java.util.concurrent.CompletionStage
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
@GQLType("TeamMutation")
class GQLTeamMutation @Inject constructor(
    accessManager: AccessManager,
    private val metaStore: MetaStore,
    private val dataStore: DataStore,
    private val redis: RedissonClient,
    private val messageBroker: MessageBroker
) :
    Operations(accessManager) {

    @GQLFetcher
    fun update(env: DataFetchingEnvironment): CompletionStage<Response<Team>> {
        return withAccess(env) {
            val team = env.getSource<Team>()
            val (update, nullFields) = calcUpdate<TeamUpdate, TeamNullableField>(env)
            metaStore.updateTeam(team.id!!, update, nullFields)
                .flatMapSingle { metaStore.getTeam(team.id!!).toSingle() }
                .map { Response(item = it) }
                .onErrorReturn { errorResponse(env, it) }
        }
    }

    @Deprecated("Remove once GraphQL no longer exposes this mutation!")
    @GQLFetcher
    fun setName(env: DataFetchingEnvironment): CompletionStage<Response<Team>> {
        return withAccess(env) {
            val team = env.getSource<Team>()
            metaStore.updateTeam(team.id!!, TeamUpdate(name = env.getArgument("name")))
                .flatMapSingle { metaStore.getTeam(team.id!!).toSingle() }
                .map { Response(item = it) }
                .onErrorReturn { errorResponse(env, it) }
        }
    }

    @Deprecated("Remove once GraphQL no longer exposes this mutation!")
    @GQLFetcher
    fun setDescription(env: DataFetchingEnvironment): CompletionStage<Response<Team>> {
        return withAccess(env) {
            val team = env.getSource<Team>()
            metaStore.updateTeam(team.id!!, TeamUpdate(description = env.getArgument("description")))
                .flatMapSingle { metaStore.getTeam(team.id!!).toSingle() }
                .map { Response(item = it) }
                .onErrorReturn { errorResponse(env, it) }
        }
    }

    @Deprecated("Remove once GraphQL no longer exposes this mutation!")
    @GQLFetcher
    fun setUsagePlan(env: DataFetchingEnvironment): CompletionStage<Response<Team>> {
        return withAccess(env) {
            val team = env.getSource<Team>()
            metaStore.updateTeam(team.id!!, TeamUpdate(usagePlanId = env.getArgument("usagePlanId")))
                .flatMapSingle { metaStore.getTeam(team.id!!).toSingle() }
                .map { Response(item = it) }
                .onErrorReturn { errorResponse(env, it) }
        }
    }

    @GQLFetcher
    fun createInvite(env: DataFetchingEnvironment): CompletionStage<Response<TeamInvite>> {
        return withAccess(env) {
            val teamId = env.getSource<Team>().id!!
            val invite = TeamInvite(teamId = teamId)
            redis.getTeamInvites()
                .fastPut(
                    redisInviteKeyOrPattern(datasetId = teamId, inviteId = invite.id),
                    Json.encode(invite),
                    DEFAULT_INVITE_TTL_MINUTES.toLong(),
                    TimeUnit.MINUTES
                )
                .to(RxJavaBridge.toV2Single())
                .doOnSuccess { logger.debug { "Successfully added team invite to Redis" } }
                .doOnError { logger.error(it) { "Failed to add team invite to redis" } }
                .ignoreElement()
                .toSingleDefault(Response(item = invite))
                .onErrorReturn { errorResponse(env, it) }
        }
    }

    @GQLFetcher
    fun createClient(env: DataFetchingEnvironment): CompletionStage<Response<Client>> {
        return withAccess(env) { token ->
            val team = env.getSource<Team>()
            // Check if the team can have another client
            metaStore.getUsagePlanOrDefault(team.usagePlanId)
                .map { plan -> plan.maxClients }
                .zipWith(
                    metaStore.countClients(
                        Eq(
                            ClientField.TEAM_ID,
                            team.id!!
                        )
                    )
                ) { maxClients, clientCount -> Pair(maxClients, clientCount) }
                .flatMap { (maxClients, clientCount) ->
                    if ((clientCount + 1) <= maxClients) {
                        val input = env.parseInput(CreateClientInput::class.java)
                        metaStore.createClient(
                            Client(
                                userId = token.user.id!!,
                                teamId = team.id!!,
                                name = input.name,
                                confidential = input.confidential,
                                onBehalfOfUser = input.onBehalfOfUser,
                                scope = input.scope.toSet(),
                                redirectURIs = input.redirectURIs,
                                restrictions = input.restrictions,
                                properties = input.properties
                            )
                        )
                            .flatMapMaybe(metaStore::getClient)
                            .toSingle()
                            .map { Response(item = it) }
                    } else {
                        Single.error { IllegalStateException("Cannot exceed maximum clients for team ($maxClients)!") }
                    }
                }
                .onErrorReturn { errorResponse(env, it) }
        }
    }

    @GQLFetcher
    fun createStream(env: DataFetchingEnvironment): CompletionStage<Response<DataStream>> {
        return withAccess(env) { token ->
            val input = env.parseInput(CreateStreamInput::class.java)
            createDataStream(metaStore, token, input, env.getSource<Team>().id)
                .map { Response(item = it) }
                .onErrorReturn { errorResponse(env, it) }
        }
    }

    @GQLFetcher
    fun createExport(env: DataFetchingEnvironment): CompletionStage<Response<DataExport>> {
        return withAccess(env) { token ->
            createDataExport(
                dataStore,
                metaStore,
                messageBroker,
                token,
                env.parseInput(CreateExportInput::class.java),
                env.getSource<Team>().id
            )
                .map { Response(item = it) }
                .onErrorReturn { errorResponse(env, it) }
        }
    }

    @GQLFetcher
    fun onInvite(env: DataFetchingEnvironment): CompletionStage<TeamInvite> {
        return withAccess(env) {
            Single.just(TeamInvite(env.getArgument("id"), env.getSource<Team>().id!!))
        }
    }

    @GQLFetcher
    fun onTeamUser(env: DataFetchingEnvironment): CompletionStage<TeamUser> {
        return withAccess(env) {
            Single.just(TeamUser(env.getSource<Team>().id!!, env.getArgument("id")))
        }
    }

    @GQLFetcher
    fun onClient(env: DataFetchingEnvironment): CompletionStage<Client> {
        return withAccessMaybe(env) {
            metaStore.getClient(env.getArgument("id")).filter { it.teamId == env.getSource<Team>().id }
        }
    }

    @GQLFetcher
    fun onStream(env: DataFetchingEnvironment): CompletionStage<DataStream> {
        return withAccessMaybe(env) {
            metaStore.getDataStream(env.getArgument("id"))
        }
    }

    @GQLFetcher
    fun onExport(env: DataFetchingEnvironment): CompletionStage<DataExport> {
        return withAccessMaybe(env) {
            metaStore.getDataExport(env.getArgument("id"))
        }
    }

    @GQLFetcher
    fun remove(env: DataFetchingEnvironment): CompletionStage<Response<Team>> {
        return withAccess(env) {
            val team = env.getSource<Team>()
            metaStore.removeTeam(team.id!!).toSingleDefault(team)
                .map { Response(item = it) }
                .onErrorReturn { errorResponse(env, it) }
        }
    }
}
