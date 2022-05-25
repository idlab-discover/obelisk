package idlab.obelisk.services.pub.catalog.types

import graphql.schema.DataFetchingEnvironment
import idlab.obelisk.definitions.catalog.*
import idlab.obelisk.definitions.catalog.codegen.UserNullableField
import idlab.obelisk.definitions.catalog.codegen.UserUpdate
import idlab.obelisk.definitions.data.DataStore
import idlab.obelisk.services.pub.catalog.impl.DataRemovalRequest
import idlab.obelisk.services.pub.catalog.impl.Response
import idlab.obelisk.services.pub.catalog.impl.errorResponse
import idlab.obelisk.services.pub.catalog.impl.parseInput
import idlab.obelisk.services.pub.catalog.types.util.GQLFetcher
import idlab.obelisk.services.pub.catalog.types.util.GQLType
import idlab.obelisk.services.pub.catalog.types.util.Operations
import idlab.obelisk.services.pub.catalog.types.util.restrictDeleteToContext
import idlab.obelisk.utils.service.reactive.flatMapSingle
import idlab.obelisk.utils.service.utils.applyToken
import java.util.concurrent.CompletionStage
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
@GQLType("UserMutation")
class GQLUserMutation @Inject constructor(
    private val metaStore: MetaStore,
    private val dataStore: DataStore,
    accessManager: AccessManager
) :
    Operations(accessManager) {

    @GQLFetcher
    fun update(env: DataFetchingEnvironment): CompletionStage<Response<User>> {
        return withAccess(env) {
            val source = env.getSource<User>()
            val updatePair = calcUpdate<UserUpdate, UserNullableField>(env)
            metaStore.updateUser(source.id!!, updatePair.first, updatePair.second)
                .flatMapSingle { metaStore.getUser(source.id!!).toSingle() }
                .map { Response(item = it) }
                .onErrorReturn { errorResponse(env, it) }
        }
    }

    @Deprecated("Remove once GraphQL no longer exposes this mutation!")
    @GQLFetcher
    fun setPlatformManager(env: DataFetchingEnvironment): CompletionStage<Response<User>> {
        return withAccess(env) {
            val platformManager = env.getArgument<Boolean>("platformManager")
            val user = env.getSource<User>()
            metaStore.updateUser(user.id!!, UserUpdate(platformManager = platformManager))
                .flatMapSingle { metaStore.getUser(user.id!!).toSingle() }
                .map { Response(item = it) }
                .onErrorReturn { errorResponse(env, it) }
        }
    }

    @Deprecated("Remove once GraphQL no longer exposes this mutation!")
    @GQLFetcher
    fun setHideOrigin(env: DataFetchingEnvironment): CompletionStage<Response<User>> {
        return withAccess(env) {
            val user = env.getSource<User>()
            metaStore.updateUser(user.id!!, UserUpdate(hideOrigin = env.getArgument("hideOrigin")))
                .flatMapSingle { metaStore.getUser(user.id!!).toSingle() }
                .map { Response(item = it) }
                .onErrorReturn { errorResponse(env, it) }
        }
    }

    @GQLFetcher
    fun onAccessRequest(env: DataFetchingEnvironment): CompletionStage<AccessRequest> {
        return withAccessMaybe(env) {
            metaStore.getAccessRequest(env.getArgument("id"))
        }
    }

    @Deprecated("Remove once GraphQL no longer exposes this mutation!")
    @GQLFetcher
    fun setUsageLimit(env: DataFetchingEnvironment): CompletionStage<Response<User>> {
        return withAccess(env) {
            val user = env.getSource<User>()
            metaStore.updateUser(user.id!!, UserUpdate(usageLimitId = env.getArgument("usageLimitId")))
                .flatMapSingle { metaStore.getUser(user.id!!).toSingle() }
                .map { Response(item = it) }
                .onErrorReturn { errorResponse(env, it) }
        }
    }

    @GQLFetcher
    fun requestDataRemoval(env: DataFetchingEnvironment): CompletionStage<Response<User>> {
        return withAccess(env) {
            val user = env.getSource<User>()
            val request = env.parseInput(DataRemovalRequest::class.java)
            dataStore.delete(request.toQuery().restrictDeleteToContext(user.id!!))
                .toSingleDefault(Response(item = user))
                .onErrorReturn { errorResponse(env, it) }
        }
    }

}