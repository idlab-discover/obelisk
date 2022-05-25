package idlab.obelisk.services.pub.catalog.types

import graphql.schema.DataFetchingEnvironment
import idlab.obelisk.definitions.catalog.*
import idlab.obelisk.definitions.catalog.codegen.ClientNullableField
import idlab.obelisk.definitions.catalog.codegen.ClientUpdate
import idlab.obelisk.definitions.data.DataStore
import idlab.obelisk.plugins.accessmanager.basic.utils.SecureSecret
import idlab.obelisk.services.pub.catalog.impl.DataRemovalRequest
import idlab.obelisk.services.pub.catalog.impl.Response
import idlab.obelisk.services.pub.catalog.impl.errorResponse
import idlab.obelisk.services.pub.catalog.impl.parseInput
import idlab.obelisk.services.pub.catalog.types.util.GQLFetcher
import idlab.obelisk.services.pub.catalog.types.util.GQLType
import idlab.obelisk.services.pub.catalog.types.util.Operations
import idlab.obelisk.services.pub.catalog.types.util.restrictDeleteToContext
import idlab.obelisk.utils.service.reactive.flatMap
import idlab.obelisk.utils.service.reactive.flatMapSingle
import io.reactivex.Single
import io.vertx.core.json.JsonObject
import java.util.*
import java.util.concurrent.CompletionStage
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
@GQLType("ClientMutation")
class GQLClientMutation @Inject constructor(
    private val metaStore: MetaStore,
    private val dataStore: DataStore,
    accessManager: AccessManager
) :
    Operations(accessManager) {

    @GQLFetcher
    fun update(env: DataFetchingEnvironment): CompletionStage<Response<Client>> {
        return withAccess(env) {
            val client = env.getSource<Client>()
            val (update, nullFields) = calcUpdate<ClientUpdate, ClientNullableField>(env)
            metaStore.updateClient(client.id!!, update, nullFields)
                .flatMap { accessManager.invalidateSessions(clientIds = setOf(client.id!!)) }
                .flatMapSingle { metaStore.getClient(client.id!!).toSingle() }
                .map { Response(item = it) }
                .onErrorReturn { errorResponse(env, it) }
        }
    }

    @Deprecated("Remove once GraphQL no longer exposes this mutation!")
    @GQLFetcher
    fun setName(env: DataFetchingEnvironment): CompletionStage<Response<Client>> {
        return withAccess(env) {
            val client = env.getSource<Client>()
            updateClient(client.id!!, ClientUpdate(name = env.getArgument("name")))
                .onErrorReturn { errorResponse(env, it) }
        }
    }

    @Deprecated("Remove once GraphQL no longer exposes this mutation!")
    @GQLFetcher
    fun setConfidential(env: DataFetchingEnvironment): CompletionStage<Response<Client>> {
        return withAccess(env) {
            val client = env.getSource<Client>()
            val confidential = env.getArgument<Boolean>("confidential")
            updateClient(
                client.id!!, ClientUpdate(
                    confidential = confidential,
                    secretHash = if (confidential) UUID.randomUUID().toString() else null
                )
            )
                .onErrorReturn { errorResponse(env, it) }
        }
    }

    @Deprecated("Remove once GraphQL no longer exposes this mutation!")
    @GQLFetcher
    fun setOnBehalfOfUser(env: DataFetchingEnvironment): CompletionStage<Response<Client>> {
        return withAccess(env) {
            val client = env.getSource<Client>()
            updateClient(client.id!!, ClientUpdate(onBehalfOfUser = env.getArgument("onBehalfOfUser")))
                .onErrorReturn { errorResponse(env, it) }
        }
    }

    @Deprecated("Remove once GraphQL no longer exposes this mutation!")
    @GQLFetcher
    fun setProperties(env: DataFetchingEnvironment): CompletionStage<Response<Client>> {
        return withAccess(env) {
            val client = env.getSource<Client>()
            updateClient(client.id!!, ClientUpdate(properties = env.getArgument("properties")))
                .onErrorReturn { errorResponse(env, it) }
        }
    }

    @Deprecated("Remove once GraphQL no longer exposes this mutation!")
    @GQLFetcher
    fun setRestrictions(env: DataFetchingEnvironment): CompletionStage<Response<Client>> {
        return withAccess(env) {
            val client = env.getSource<Client>()
            val restrictions = env.getArgument<List<Map<String, Any>>>("restrictions")
                .map { JsonObject(it).mapTo(ClientRestriction::class.java) }
            updateClient(client.id!!, ClientUpdate(restrictions = restrictions))
                .onErrorReturn { errorResponse(env, it) }
        }
    }

    @Deprecated("Remove once GraphQL no longer exposes this mutation!")
    @GQLFetcher
    fun setScope(env: DataFetchingEnvironment): CompletionStage<Response<Client>> {
        return withAccess(env) { _ ->
            val client = env.getSource<Client>()
            updateClient(client.id!!,
                ClientUpdate(scope = env.getArgument<List<String>>("permissions").map { Permission.valueOf(it) }
                    .toSet())
            )
                .onErrorReturn { errorResponse(env, it) }
        }
    }

    @Deprecated("Remove once GraphQL no longer exposes this mutation!")
    @GQLFetcher
    fun setRedirectURIs(env: DataFetchingEnvironment): CompletionStage<Response<Client>> {
        return withAccess(env) {
            val client = env.getSource<Client>()
            updateClient(client.id!!, ClientUpdate(redirectURIs = env.getArgument("redirectURIs")))
                .onErrorReturn { errorResponse(env, it) }
        }
    }

    @Deprecated("Remove once GraphQL no longer exposes this mutation!")
    @GQLFetcher
    fun setHideOrigin(env: DataFetchingEnvironment): CompletionStage<Response<Client>> {
        return withAccess(env) {
            val client = env.getSource<Client>()
            updateClient(client.id!!, ClientUpdate(hideOrigin = env.getArgument("hideOrigin")))
                .onErrorReturn { errorResponse(env, it) }
        }
    }

    @GQLFetcher
    fun generateSecret(env: DataFetchingEnvironment): CompletionStage<Response<String>> {
        return withAccess(env) {
            val client = env.getSource<Client>()
            val secret = UUID.randomUUID().toString()
            updateClient(
                client.id!!,
                ClientUpdate(secretHash = if (client.confidential) SecureSecret.hash(secret) else null)
            )
                .map { Response(item = secret) }
                .onErrorReturn { errorResponse(env, it) }
        }
    }

    @GQLFetcher
    fun remove(env: DataFetchingEnvironment): CompletionStage<Response<Client>> {
        return withAccess(env) {
            val client = env.getSource<Client>()
            metaStore.removeClient(client.id!!)
                .flatMap { accessManager.invalidateSessions(clientIds = setOf(client.id!!)) }
                .toSingleDefault(Response(item = client))
                .onErrorReturn { errorResponse(env, it) }
        }
    }

    private fun updateClient(clientId: String, update: ClientUpdate): Single<Response<Client>> {
        return metaStore.updateClient(clientId, update)
            .flatMapSingle { metaStore.getClient(clientId).toSingle() }
            .map { Response(item = it) }
    }

    @GQLFetcher
    fun requestDataRemoval(env: DataFetchingEnvironment): CompletionStage<Response<Client>> {
        return withAccess(env) {
            val client = env.getSource<Client>()
            val request = env.parseInput(DataRemovalRequest::class.java)
            dataStore.delete(request.toQuery().restrictDeleteToContext(client.userId, client.id!!))
                .toSingleDefault(Response(item = client))
                .onErrorReturn { errorResponse(env, it) }
        }
    }

}