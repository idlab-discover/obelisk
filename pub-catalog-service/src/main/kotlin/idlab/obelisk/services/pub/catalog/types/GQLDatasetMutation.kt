package idlab.obelisk.services.pub.catalog.types

import graphql.schema.DataFetchingEnvironment
import hu.akarnokd.rxjava3.bridge.RxJavaBridge
import idlab.obelisk.definitions.And
import idlab.obelisk.definitions.DataRange
import idlab.obelisk.definitions.Eq
import idlab.obelisk.definitions.MetricName
import idlab.obelisk.definitions.catalog.*
import idlab.obelisk.definitions.catalog.codegen.*
import idlab.obelisk.definitions.data.DataStore
import idlab.obelisk.definitions.data.EventsQuery
import idlab.obelisk.services.pub.catalog.impl.*
import idlab.obelisk.services.pub.catalog.types.util.*
import idlab.obelisk.services.pub.catalog.types.util.getInvites
import idlab.obelisk.utils.service.reactive.flatMap
import idlab.obelisk.utils.service.reactive.flatMapSingle
import idlab.obelisk.utils.service.utils.copyWithUpdate
import idlab.obelisk.utils.service.utils.remove
import idlab.obelisk.utils.service.utils.unpage
import io.reactivex.Completable
import io.reactivex.Single
import io.vertx.core.json.Json
import io.vertx.core.json.JsonObject
import org.redisson.api.RedissonClient
import java.util.concurrent.CompletionStage
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

private const val DS_PROPERTY_CATALOG = "_catalog"
private const val DS_PROPERTY_CATALOG_ARCHIVED_ON = "archivedOn"

@Singleton
@GQLType("DatasetMutation")
class GQLDatasetMutation @Inject constructor(
    private val redis: RedissonClient,
    private val metaStore: MetaStore,
    private val dataStore: DataStore,
    accessManager: AccessManager
) : Operations(accessManager) {

    @GQLFetcher
    fun requestAccess(env: DataFetchingEnvironment): CompletionStage<Response<AccessRequest>> {
        return withAccess(env) { token ->
            val datasetId = env.getSource<Dataset>().id!!
            val input = env.parseInput(RequestAccessInput::class.java)
            val accessRequest = AccessRequest(
                datasetId = datasetId,
                message = input.message,
                type = input.type.toSet(),
                userId = token.user.id!!
            )
            metaStore.createAccessRequest(accessRequest)
                .flatMap { metaStore.getAccessRequest(it).toSingle().map { ar -> Response(item = ar) } }
                .onErrorReturn { errorResponse(env, it) }

        }
    }

    @GQLFetcher
    fun requestAccessAsTeam(env: DataFetchingEnvironment): CompletionStage<Response<AccessRequest>> {
        return withAccess(env) { token ->
            val datasetId = env.getSource<Dataset>().id!!
            val teamId = env.getArgument<String>("teamId")
            val input = env.parseInput(RequestAccessInput::class.java)
            val accessRequest = AccessRequest(
                datasetId = datasetId,
                message = input.message,
                type = input.type.toSet(),
                userId = token.user.id!!,
                teamId = teamId
            )
            metaStore.createAccessRequest(accessRequest)
                .flatMap { metaStore.getAccessRequest(it).toSingle().map { ar -> Response(item = ar) } }
                .onErrorReturn { errorResponse(env, it) }
        }
    }

    @GQLFetcher
    fun addMember(env: DataFetchingEnvironment): CompletionStage<Response<Dataset>> {
        return withAccess(env) {
            val dataset = env.getSource<Dataset>()
            val userId = env.getArgument<String>("userId")
            metaStore.getUser(userId)
                .toSingle()
                .flatMap { user ->
                    if (user.datasetMemberships.none { it.datasetId == dataset.id }) {
                        metaStore.updateUser(
                            userId,
                            UserUpdate(datasetMemberships = user.datasetMemberships.plus(DatasetMembership(dataset.id!!)))
                        )
                            .toSingleDefault(dataset)
                    } else {
                        Single.just(dataset)
                    }
                }
                .flatMap { invalidateUser(userId, metaStore).toSingleDefault(it) }
                .map { Response(item = it) }
                .onErrorReturn { errorResponse(env, it) }
        }
    }

    @GQLFetcher
    fun addTeam(env: DataFetchingEnvironment): CompletionStage<Response<Dataset>> {
        return withAccess(env) {
            val dataset = env.getSource<Dataset>()
            val teamId = env.getArgument<String>("teamId")
            metaStore.getTeam(teamId)
                .toSingle()
                .flatMap { team ->
                    if (team.datasetMemberships.none { it.datasetId == dataset.id }) {
                        metaStore.updateTeam(
                            teamId, TeamUpdate(
                                datasetMemberships = team.datasetMemberships.plus(
                                    DatasetMembership(dataset.id!!)
                                )
                            )
                        ).toSingleDefault(dataset)
                    } else {
                        Single.just(dataset)
                    }
                }
                .flatMap { invalidateTeam(teamId, metaStore).toSingleDefault(it) }
                .map { Response(item = it) }
                .onErrorReturn { errorResponse(env, it) }
        }
    }

    @GQLFetcher
    fun removeMember(env: DataFetchingEnvironment): CompletionStage<Response<Dataset>> {
        return withAccess(env) {
            val dataset = env.getSource<Dataset>()
            val userId = env.getArgument<String>("userId")
            metaStore.getUser(userId)
                .toSingle()
                .flatMap { user ->
                    metaStore.updateUser(
                        user.id!!,
                        UserUpdate(datasetMemberships = user.datasetMemberships.filterNot { it.datasetId == dataset.id })
                    ).toSingleDefault(dataset)
                }
                .flatMap { invalidateUser(userId, metaStore).toSingleDefault(it) }
                .map { Response(item = it) }
                .onErrorReturn { errorResponse(env, it) }
        }
    }

    @GQLFetcher
    fun removeTeam(env: DataFetchingEnvironment): CompletionStage<Response<Dataset>> {
        return withAccess(env) {
            val dataset = env.getSource<Dataset>()
            val teamId = env.getArgument<String>("teamId")
            metaStore.getTeam(teamId)
                .toSingle()
                .flatMap { team ->
                    metaStore.updateTeam(
                        team.id!!,
                        TeamUpdate(datasetMemberships = team.datasetMemberships.filterNot { it.datasetId == dataset.id })
                    ).toSingleDefault(dataset)
                }
                .flatMap { invalidateTeam(teamId, metaStore).toSingleDefault(it) }
                .map { Response(item = it) }
                .onErrorReturn { errorResponse(env, it) }
        }
    }

    @GQLFetcher
    fun createRole(env: DataFetchingEnvironment): CompletionStage<Response<Role>> {
        return withAccess(env) {
            val input = env.parseInput(CreateRoleInput::class.java)
            val datasetId = env.getSource<Dataset>().id!!
            metaStore.createRole(
                Role(
                    name = input.name,
                    datasetId = datasetId,
                    description = input.description,
                    grant = Grant(input.permissions.toSet(), input.readFilter)
                )
            )
                .flatMap { roleId -> metaStore.getRole(roleId).toSingle() }
                .map { Response(item = it) }
                .onErrorReturn { errorResponse(env, it) }
        }
    }

    @GQLFetcher
    fun onRole(env: DataFetchingEnvironment): CompletionStage<Role> {
        return withAccessMaybe(env) {
            metaStore.getRole(env.getArgument("id"))
                .filter { role -> role.datasetId == env.getSource<Dataset>().id }
        }
    }

    @GQLFetcher
    fun assignRoles(env: DataFetchingEnvironment): CompletionStage<Response<Dataset>> {
        return withAccess(env) { _ ->
            val input = env.parseInput(AssignRolesInput::class.java)
            val dataset = env.getSource<Dataset>()
            metaStore.getUser(input.userId)
                .flatMapCompletable { user ->
                    metaStore.updateUser(
                        input.userId, UserUpdate(
                            datasetMemberships = user.datasetMemberships.filterNot { it.datasetId == dataset.id }.plus(
                                DatasetMembership(dataset.id!!, input.roleIds?.toSet() ?: emptySet())
                            )
                        )
                    )
                }
                .flatMap { invalidateUser(input.userId, metaStore) }
                .toSingleDefault(Response(item = dataset))
                .onErrorReturn { errorResponse(env, it) }
        }
    }

    @GQLFetcher
    fun assignRolesAsTeam(env: DataFetchingEnvironment): CompletionStage<Response<Dataset>> {
        return withAccess(env) {
            val input = env.parseInput(AssignTeamRolesInput::class.java)
            val dataset = env.getSource<Dataset>()
            metaStore.getTeam(input.teamId)
                .flatMapCompletable { team ->
                    metaStore.updateTeam(
                        input.teamId, TeamUpdate(
                            datasetMemberships = team.datasetMemberships.filterNot { it.datasetId == dataset.id }.plus(
                                DatasetMembership(dataset.id!!, input.roleIds?.toSet() ?: emptySet())
                            )
                        )
                    )
                }
                .flatMap { invalidateTeam(input.teamId, metaStore) }
                .toSingleDefault(Response(item = dataset))
                .onErrorReturn { errorResponse(env, it) }
        }
    }

    @GQLFetcher
    fun update(env: DataFetchingEnvironment): CompletionStage<Response<Dataset>> {
        return withAccess(env) {
            val source = env.getSource<Dataset>()
            val updatePair = calcUpdate<DatasetUpdate, DatasetNullableField>(env)
            metaStore.updateDataset(source.id!!, updatePair.first, updatePair.second)
                .flatMapSingle { metaStore.getDataset(source.id!!).toSingle() }
                .map { Response(item = it) }
                .onErrorReturn { errorResponse(env, it) }
        }
    }

    @Deprecated("Remove once GraphQL no longer exposes this mutation!")
    @GQLFetcher
    fun setName(env: DataFetchingEnvironment): CompletionStage<Response<Dataset>> {
        return withAccess(env) {
            updateDataset(env, DatasetUpdate(name = env.getArgument("name")))
        }
    }

    @Deprecated("Remove once GraphQL no longer exposes this mutation!")
    @GQLFetcher
    fun setDescription(env: DataFetchingEnvironment): CompletionStage<Response<Dataset>> {
        return withAccess(env) {
            updateDataset(env, DatasetUpdate(description = env.getArgument("description")))
        }
    }

    @Deprecated("Remove once GraphQL no longer exposes this mutation!")
    @GQLFetcher
    fun setProperties(env: DataFetchingEnvironment): CompletionStage<Response<Dataset>> {
        return withAccess(env) {
            // Workaround for faulty native GraphQL serialization (integer numbers are parsed as BigIntegers, which are not compatible with mongo serdes)
            val props = JsonObject(Json.encode(env.getArgument("properties"))).map
            updateDataset(env, DatasetUpdate(properties = props))
        }
    }

    @Deprecated("Remove once GraphQL no longer exposes this mutation!")
    @GQLFetcher
    fun setPublished(env: DataFetchingEnvironment): CompletionStage<Response<Dataset>> {
        return withAccess(env) {
            updateDataset(env, DatasetUpdate(published = env.getArgument("published")))
        }
    }

    @Deprecated("Remove once GraphQL no longer exposes this mutation!")
    @GQLFetcher
    fun setOpenData(env: DataFetchingEnvironment): CompletionStage<Response<Dataset>> {
        return withAccess(env) {
            updateDataset(env, DatasetUpdate(openData = env.getArgument("openData")))
        }
    }

    @GQLFetcher
    fun archive(env: DataFetchingEnvironment): CompletionStage<Response<Dataset>> {
        return withAccess(env) { token ->
            val dataset = env.getSource<Dataset>()
            updateDataset(
                env, DatasetUpdate(
                    locked = true, properties = dataset.properties.copyWithUpdate(
                        listOf(
                            DS_PROPERTY_CATALOG, DS_PROPERTY_CATALOG_ARCHIVED_ON
                        ), System.currentTimeMillis()
                    )
                )
            )
                .flatMap { resp -> accessManager.invalidateToken(token).toSingleDefault(resp) }
        }
    }

    @GQLFetcher
    fun unarchive(env: DataFetchingEnvironment): CompletionStage<Response<Dataset>> {
        return withAccess(env) { token ->
            val dataset = env.getSource<Dataset>()
            updateDataset(
                env, DatasetUpdate(
                    locked = false, properties = dataset.properties.remove(
                        listOf(
                            DS_PROPERTY_CATALOG, DS_PROPERTY_CATALOG_ARCHIVED_ON
                        )
                    )
                )
            )
                .flatMap { resp -> accessManager.invalidateToken(token).toSingleDefault(resp) }
        }
    }

    @GQLFetcher
    fun remove(env: DataFetchingEnvironment): CompletionStage<Response<Dataset>> {
        return withAccess(env) {
            val dataset = env.getSource<Dataset>()
            metaStore.removeDataset(datasetId = dataset.id!!)
                .flatMap {
                    // Invalidate sessions with access to the dataset (so no additional data can be produced)
                    unpage { cursor ->
                        metaStore.queryRoles(
                            filter = Eq(RoleField.DATASET_ID, dataset.id!!),
                            cursor = cursor
                        )
                    }
                        .flatMapCompletable { role ->
                            invalidateRole(role.id!!, metaStore)
                        }
                }
                .flatMap {
                    dataStore.delete(
                        EventsQuery(
                            dataRange = DataRange.fromDatasetId(dataset.id!!)
                        )
                    )
                }
                .toSingleDefault(Response(item = dataset))
                .onErrorReturn { errorResponse(env, it) }
        }
    }


    @GQLFetcher
    fun createInvite(env: DataFetchingEnvironment): CompletionStage<Response<Invite>> {
        return withAccess(env) {
            val datasetId = env.getSource<Dataset>().id!!
            val input = env.parseInput(InviteInput::class.java)
            val invite = Invite(
                datasetId = datasetId,
                roleIds = input.roleIds.toSet(),
                disallowTeams = input.disallowTeams
            )
            saveInvite(datasetId, invite)
                .toSingleDefault(Response(item = invite))
                .onErrorReturn { errorResponse(env, it) }
        }
    }

    @GQLFetcher
    fun onInvite(env: DataFetchingEnvironment): CompletionStage<Invite> {
        return withAccess(env) {
            val datasetId = env.getSource<Dataset>().id!!
            val inviteId = env.getArgument<String>("id")
            redis.getInvites()
                .get(redisInviteKeyOrPattern(datasetId = datasetId, inviteId = inviteId))
                .to(RxJavaBridge.toV2Maybe())
                .map { Json.decodeValue(it, Invite::class.java) }
                .toSingle()
        }
    }

    @GQLFetcher
    fun onAccessRequest(env: DataFetchingEnvironment): CompletionStage<AccessRequest> {
        return withAccessMaybe(env) {
            metaStore.getAccessRequest(env.getArgument("id"))
        }
    }

    @GQLFetcher
    fun onThing(env: DataFetchingEnvironment): CompletionStage<Thing> {
        return withAccess(env) {
            val dataset = env.getSource<Dataset>()
            val thingId = env.getArgument<String>("id")
            val defaultThing = Thing(sourceId = thingId, datasetId = dataset.id!!)
            metaStore.queryThings(
                filter = And(
                    Eq(ThingField.DATASET_ID, dataset.id!!),
                    Eq(ThingField.SOURCE_ID, thingId)
                ), limit = 1
            )
                .map { result -> result.items.firstOrNull() ?: defaultThing }
        }
    }

    @GQLFetcher
    fun onMetric(env: DataFetchingEnvironment): CompletionStage<Metric> {
        return withAccess(env) {
            val dataset = env.getSource<Dataset>()
            val metricId = MetricName(env.getArgument("id"))
            val defaultMetric = Metric(name = metricId, datasetId = dataset.id!!)

            metaStore.queryMetrics(
                filter = And(
                    Eq(MetricField.DATASET_ID, dataset.id!!),
                    Eq("fqMetricId", metricId.getFullyQualifiedId())
                ), limit = 1
            )
                .map { result -> result.items.firstOrNull() ?: defaultMetric }
        }
    }

    private fun updateDataset(
        env: DataFetchingEnvironment,
        update: DatasetUpdate
    ): Single<Response<Dataset>> {
        val datasetId = env.getSource<Dataset>().id!!
        return metaStore.updateDataset(datasetId, update)
            .flatMapSingle { metaStore.getDataset(datasetId).toSingle() }
            .map { Response(item = it) }
            .onErrorReturn { errorResponse(env, it) }
    }

    private fun saveInvite(datasetId: String, invite: Invite): Completable {
        return redis.getInvites()
            .fastPut(
                redisInviteKeyOrPattern(datasetId = datasetId, inviteId = invite.id),
                Json.encode(invite),
                DEFAULT_INVITE_TTL_MINUTES.toLong(),
                TimeUnit.MINUTES
            )
            .to(RxJavaBridge.toV2Single())
            .doOnSuccess { logger.debug { "Successfully added invite to Redis" } }
            .doOnError { logger.error(it) { "Failed to add invite to redis" } }
            .ignoreElement()
    }

}