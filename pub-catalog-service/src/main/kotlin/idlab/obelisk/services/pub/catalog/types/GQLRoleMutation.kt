package idlab.obelisk.services.pub.catalog.types

import graphql.schema.DataFetchingEnvironment
import idlab.obelisk.definitions.FilterExpression
import idlab.obelisk.definitions.catalog.AccessManager
import idlab.obelisk.definitions.catalog.MetaStore
import idlab.obelisk.definitions.catalog.Permission
import idlab.obelisk.definitions.catalog.Role
import idlab.obelisk.definitions.catalog.codegen.RoleNullableField
import idlab.obelisk.definitions.catalog.codegen.RoleUpdate
import idlab.obelisk.services.pub.catalog.impl.Response
import idlab.obelisk.services.pub.catalog.impl.errorResponse
import idlab.obelisk.services.pub.catalog.types.util.GQLFetcher
import idlab.obelisk.services.pub.catalog.types.util.GQLType
import idlab.obelisk.services.pub.catalog.types.util.Operations
import idlab.obelisk.utils.service.reactive.flatMap
import idlab.obelisk.utils.service.reactive.flatMapSingle
import io.reactivex.Single
import io.vertx.core.json.JsonObject
import java.util.concurrent.CompletionStage
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
@GQLType("RoleMutation")
class GQLRoleMutation @Inject constructor(private val metaStore: MetaStore, accessManager: AccessManager) :
        Operations(accessManager) {

    @GQLFetcher
    fun update(env: DataFetchingEnvironment): CompletionStage<Response<Role>> {
        return withAccess(env) {
            val role = env.getSource<Role>()
            val (update, nullFields) = calcUpdate<RoleUpdate, RoleNullableField>(env)
            metaStore.updateRole(role.id!!, update, nullFields)
                    .flatMap { invalidateRole(role.id!!, metaStore) }
                    .flatMapSingle { metaStore.getRole(role.id!!).toSingle() }
                    .map { Response(item = it) }
                    .onErrorReturn { errorResponse(env, it) }
        }
    }

    @Deprecated("Remove once GraphQL no longer exposes this mutation")
    @GQLFetcher
    fun setName(env: DataFetchingEnvironment): CompletionStage<Response<Role>> {
        return withAccess(env) {
            updateHelper(env) { role ->
                role.copy(name = env.getArgument("name"))
            }
        }
    }

    @Deprecated("Remove once GraphQL no longer exposes this mutation")
    @GQLFetcher
    fun setDescription(env: DataFetchingEnvironment): CompletionStage<Response<Role>> {
        return withAccess(env) {
            updateHelper(env) { role ->
                role.copy(description = env.getArgument("description"))
            }
        }
    }

    @Deprecated("Remove once GraphQL no longer exposes this mutation")
    @GQLFetcher
    fun setPermissions(env: DataFetchingEnvironment): CompletionStage<Response<Role>> {
        return withAccess(env) {
            updateHelper(env) { role ->
                role.copy(
                        grant = role.grant.copy(
                                permissions = env.getArgument<List<String>>("permissions").map { Permission.valueOf(it) }
                                        .toSet()
                        )
                )
            }
        }
    }

    @Deprecated("Remove once GraphQL no longer exposes this mutation")
    @GQLFetcher
    fun setReadFilter(env: DataFetchingEnvironment): CompletionStage<Response<Role>> {
        return withAccess(env) {
            val expr = JsonObject(env.getArgument<Map<String, *>>("readFilter")).mapTo(FilterExpression::class.java)
            updateHelper(env) { role ->
                role.copy(grant = role.grant.copy(readFilter = expr))
            }
        }
    }

    private fun updateHelper(env: DataFetchingEnvironment, updateFunction: (Role) -> Role): Single<Response<Role>> {
        val role = env.getSource<Role>()
        val updatedRole = updateFunction.invoke(role)
        return metaStore.createRole(updatedRole).map { updatedRole }
                .map { Response(item = it) }
                .onErrorReturn { errorResponse(env, it) }
    }

    @GQLFetcher
    fun remove(env: DataFetchingEnvironment): CompletionStage<Response<Role>> {
        return withAccess(env) { _ ->
            val role = env.getSource<Role>()
            metaStore.removeRole(role.id!!)
                    .flatMap { invalidateRole(role.id!!, metaStore) }
                    .toSingleDefault(role)
                    .map { Response(item = it) }
                    .onErrorReturn { errorResponse(env, it) }
        }
    }

}