package idlab.obelisk.services.pub.catalog.types

import graphql.schema.DataFetchingEnvironment
import idlab.obelisk.definitions.catalog.*
import idlab.obelisk.definitions.catalog.codegen.UserUpdate
import idlab.obelisk.definitions.data.DataStore
import idlab.obelisk.definitions.framework.OblxConfig
import idlab.obelisk.definitions.messaging.MessageBroker
import idlab.obelisk.services.pub.catalog.impl.*
import idlab.obelisk.services.pub.catalog.types.util.GQLFetcher
import idlab.obelisk.services.pub.catalog.types.util.GQLType
import idlab.obelisk.services.pub.catalog.types.util.Operations
import idlab.obelisk.utils.service.http.AuthorizationException
import idlab.obelisk.utils.service.reactive.flatMap
import idlab.obelisk.utils.service.reactive.flatMapSingle
import io.reactivex.Completable
import io.reactivex.Single
import java.util.concurrent.CompletionStage
import javax.inject.Inject
import javax.inject.Singleton

internal const val ENV_ALLOW_USER_CREATED_DATASETS = "ALLOW_USER_CREATED_DATASETS"
internal const val ENV_ALLOW_USER_CREATED_TEAMS = "ALLOW_USER_CREATED_TEAMS"
internal const val DEFAULT_ALLOW_USER_CREATED_DATASETS = true
internal const val DEFAULT_ALLOW_USER_CREATED_TEAMS = true

@Singleton
@GQLType("Mutation")
class GQLMutationRoot @Inject constructor(
    private val metaStore: MetaStore,
    private val dataStore: DataStore,
    accessManager: AccessManager,
    oblxConfig: OblxConfig,
    private val messageBroker: MessageBroker
) : Operations(accessManager) {

    private val allowUserCreatedDatasets =
        oblxConfig.getBoolean(ENV_ALLOW_USER_CREATED_DATASETS, DEFAULT_ALLOW_USER_CREATED_DATASETS)
    private val allowUserCreatedTeams =
        oblxConfig.getBoolean(ENV_ALLOW_USER_CREATED_TEAMS, DEFAULT_ALLOW_USER_CREATED_TEAMS)

    @GQLFetcher
    fun createDataset(env: DataFetchingEnvironment): CompletionStage<Response<Dataset>> {
        return withAccess(env) { token ->
            val input = env.parseInput(CreateDatasetInput::class.java)
            if (allowUserCreatedDatasets || token.user.platformManager) {
                metaStore.createDataset(Dataset(name = input.name, description = input.description))
                    .flatMap { datasetId ->
                        // If the dataset was created successfully...
                        // 1. Create basic roles for the dataset
                        Completable.mergeArray(
                            metaStore.createRole(
                                Role(
                                    name = DefaultRoles.manager.toString(),
                                    description = DefaultRoles.manager.description,
                                    datasetId = datasetId,
                                    grant = Grant(permissions = Permission.all())
                                )
                            )
                                .flatMapCompletable { roleId ->
                                    // 2. Assign manager role to the user creating the dataset or the specified datasetOwnerId (admin only)
                                    if (input.datasetOwnerId != null) {
                                        if (token.user.platformManager) {
                                            metaStore.getUser(input.datasetOwnerId)
                                                .flatMapCompletable { user ->
                                                    metaStore.updateUser(
                                                        user.id!!, UserUpdate(
                                                            datasetMemberships = user.datasetMemberships.plus(
                                                                DatasetMembership(datasetId, setOf(roleId))
                                                            )
                                                        )
                                                    ).flatMap { invalidateUser(user.id!!, metaStore) }
                                                }
                                        } else {
                                            Completable.error { AuthorizationException("Only administrators can assign a new Dataset to another User!") }
                                        }
                                    } else {
                                        metaStore.getUser(token.user.id!!)
                                            .flatMapCompletable { user ->
                                                metaStore.updateUser(
                                                    user.id!!, UserUpdate(
                                                        datasetMemberships = user.datasetMemberships.plus(
                                                            DatasetMembership(datasetId, setOf(roleId))
                                                        )
                                                    )
                                                )
                                            }
                                            .flatMap { invalidateUser(token.user.id!!, metaStore) }
                                    }
                                },
                            metaStore.createRole(
                                Role(
                                    name = DefaultRoles.consumer.toString(),
                                    description = DefaultRoles.consumer.description,
                                    datasetId = datasetId,
                                    grant = Grant(permissions = Permission.readOnly())
                                )
                            ).ignoreElement(),
                            metaStore.createRole(
                                Role(
                                    name = DefaultRoles.contributor.toString(),
                                    description = DefaultRoles.contributor.description,
                                    datasetId = datasetId,
                                    grant = Grant(permissions = Permission.readAndWrite())
                                )
                            ).ignoreElement()
                        ).flatMapSingle { metaStore.getDataset(datasetId).toSingle() }
                    }
                    .map { Response(item = it) }
                    .onErrorReturn { errorResponse(env, it) }
            } else {
                Single.error { AuthorizationException("You do not have the permissions to create a Dataset!") }
            }
        }
    }

    @GQLFetcher
    fun createClient(env: DataFetchingEnvironment): CompletionStage<Response<Client>> {
        return withAccess(env) { token ->
            val input = env.parseInput(CreateClientInput::class.java)
            metaStore.createClient(
                Client(
                    userId = token.user.id!!,
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
                .onErrorReturn { errorResponse(env, it) }
        }
    }

    @GQLFetcher
    fun createStream(env: DataFetchingEnvironment): CompletionStage<Response<DataStream>> {
        return withAccess(env) { token ->
            val input = env.parseInput(CreateStreamInput::class.java)
            createDataStream(metaStore, token, input)
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
                env.parseInput(CreateExportInput::class.java)
            )
                .map { Response(item = it) }
                .onErrorReturn { errorResponse(env, it) }
        }
    }

    @GQLFetcher
    fun createTeam(env: DataFetchingEnvironment): CompletionStage<Response<Team>> {
        return withAccess(env) { token ->
            val input = env.parseInput(CreateTeamInput::class.java)
            val team = Team(
                name = input.name,
                description = input.description
            )
            if (allowUserCreatedTeams || token.user.platformManager) {
                metaStore.createTeam(team)
                    .flatMapCompletable { teamId ->
                        if (input.teamOwnerId != null) {
                            if (token.user.platformManager) {
                                metaStore.getUser(input.teamOwnerId)
                                    .flatMapCompletable { user ->
                                        metaStore.updateUser(
                                            user.id!!, UserUpdate(
                                                teamMemberships = user.teamMemberships.plus(
                                                    TeamMembership(teamId, true)
                                                )
                                            )
                                        ).flatMap { invalidateUser(user.id!!, metaStore) }
                                    }
                            } else {
                                Completable.error { AuthorizationException("Only administrators can assign a Team to another User!") }
                            }
                        } else {
                            metaStore.getUser(token.user.id!!)
                                .flatMapCompletable { user ->
                                    metaStore.updateUser(
                                        user.id!!, UserUpdate(
                                            teamMemberships = user.teamMemberships.plus(
                                                TeamMembership(teamId, true)
                                            )
                                        )
                                    )
                                }
                                .flatMap { invalidateUser(token.user.id!!, metaStore) }
                        }
                    }
                    .toSingleDefault(Response(item = team))
            } else {
                Single.error { AuthorizationException("You do not have the permissions to create a Team!") }
            }
                .onErrorReturn { errorResponse(env, it) }
        }
    }

    @GQLFetcher
    fun createUsagePlan(env: DataFetchingEnvironment): CompletionStage<Response<UsagePlan>> {
        return withAccess(env) {
            val input = env.parseInput(UsagePlanInput::class.java)
            val usagePlan = UsagePlan(
                name = input.name,
                description = input.description,
                maxClients = input.maxClients,
                clientUsageLimitId = input.clientUsageLimitId,
                maxUsers = input.maxUsers,
                userUsageLimitId = input.userUsageLimitId
            )
            // TODO: validate client and user usage limit relations
            metaStore.createUsagePlan(usagePlan)
                .flatMapMaybe(metaStore::getUsagePlan)
                .toSingle()
                .map { Response(item = it) }
                .onErrorReturn { errorResponse(env, it) }
        }
    }

    @GQLFetcher
    fun createUsageLimit(env: DataFetchingEnvironment): CompletionStage<Response<UsageLimit>> {
        return withAccess(env) {
            val input = env.parseInput(UsageLimit::class.java)
            val usageLimit = UsageLimit(
                name = input.name,
                description = input.description,
                values = input.values
            )
            metaStore.createUsageLimit(usageLimit)
                .flatMapMaybe(metaStore::getUsageLimit)
                .toSingle()
                .map { Response(item = it) }
                .onErrorReturn { errorResponse(env, it) }
        }
    }

    @GQLFetcher
    fun onDataset(env: DataFetchingEnvironment): CompletionStage<Dataset> {
        return withAccessMaybe(env) { token ->
            val datasetId = env.getArgument<String>("id")
            metaStore.getDataset(datasetId = datasetId)
                .filter { !it.locked || token.user.platformManager }
        }
    }

    @GQLFetcher
    fun onUser(env: DataFetchingEnvironment): CompletionStage<User> {
        return withAccessMaybe(env) {
            metaStore.getUser(env.getArgument("id"))
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
    fun onClient(env: DataFetchingEnvironment): CompletionStage<Client> {
        return withAccessMaybe(env) {
            metaStore.getClient(env.getArgument("id"))
        }
    }

    @GQLFetcher
    fun onTeam(env: DataFetchingEnvironment): CompletionStage<Team> {
        return withAccessMaybe(env) {
            metaStore.getTeam(env.getArgument("id"))
        }
    }

    @GQLFetcher
    fun onUsagePlan(env: DataFetchingEnvironment): CompletionStage<UsagePlan> {
        return withAccessMaybe(env) {
            metaStore.getUsagePlan(env.getArgument("id"))
        }
    }

    @GQLFetcher
    fun onUsageLimit(env: DataFetchingEnvironment): CompletionStage<UsageLimit> {
        return withAccessMaybe(env) {
            metaStore.getUsageLimit(env.getArgument("id"))
        }
    }

    @GQLFetcher
    fun createAnnouncement(env: DataFetchingEnvironment): CompletionStage<Response<Announcement>> {
        return withAccess(env) {
            val input = env.parseInput(Announcement::class.java)
            metaStore.createAnnouncement(input)
                .flatMap { metaStore.getAnnouncement(it).toSingle() }
                .map { Response(item = it) }
                .onErrorReturn { errorResponse(env, it) }
        }
    }

    @GQLFetcher
    fun onAnnouncement(env: DataFetchingEnvironment): CompletionStage<Announcement> {
        return withAccessMaybe(env) {
            metaStore.getAnnouncement(env.getArgument("id"))
        }
    }
}
