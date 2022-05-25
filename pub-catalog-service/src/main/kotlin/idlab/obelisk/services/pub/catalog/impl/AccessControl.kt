package idlab.obelisk.services.pub.catalog.impl

import graphql.language.NonNullType
import graphql.language.OperationDefinition
import graphql.language.TypeName
import graphql.schema.DataFetchingEnvironment
import graphql.schema.GraphQLInputObjectType
import idlab.obelisk.definitions.catalog.*
import idlab.obelisk.utils.service.http.AuthorizationException
import io.vertx.ext.web.impl.RoutingContextImpl
import io.vertx.reactivex.ext.web.RoutingContext

enum class DefaultRoles(val description: String) {
    manager("Has all permissions in the dataset: read, write and manage."),
    consumer("Can only read from the dataset."),
    contributor("Can read from and write to the dataset.")
}

internal fun loadToken(accessManager: AccessManager, ctx: RoutingContext) {
    accessManager.getToken(ctx.request())
        .doFinally { ctx.next() }
        .subscribe(
            { ctx.put(Token::class.java.name, it) },
            { ctx.put(Token::class.java.name, it) }
        )
}

internal fun checkAccess(env: DataFetchingEnvironment): Token {
    val ctx = getCtxSafe(env)
    return when (val token: Any? = ctx[Token::class.java.name]) {
        is Token -> {
            val clearance = RestrictedDirective.fromFieldDef(env.fieldDefinition)
            val datasetId = datasetIdFrom(env)
            val teamId = teamIdFrom(env)
            checkClientScope(env, token)
            if (clearance.isEmpty() || clearance.any {
                    when (it) {
                        RestrictedDirective.ClearanceLevel.PLATFORM_MANAGER -> token.user.platformManager
                        RestrictedDirective.ClearanceLevel.DATASET_MANAGER -> isDatasetManager(token, datasetId)
                        RestrictedDirective.ClearanceLevel.DATASET_MEMBER -> isDatasetMember(token, datasetId)
                        RestrictedDirective.ClearanceLevel.ME -> isPersonal(token, env)
                        RestrictedDirective.ClearanceLevel.TEAM_MANAGER -> isTeamManager(token, teamId)
                        RestrictedDirective.ClearanceLevel.TEAM_MEMBER -> isTeamMember(token, teamId)
                    }
                }) token else throw AuthorizationException("Current auth context does not have the appropriate permissions.")
        }
        is Throwable -> throw token
        else -> throw AuthorizationException("Invalid auth context!")
    }
}

fun checkUpdateFieldAllowed(env: DataFetchingEnvironment, fieldName: String): Boolean {
    val ctx = getCtxSafe(env)
    return when (val token: Any? = ctx[Token::class.java.name]) {
        is Token -> {
            val inputTypeName =
                (env.fieldDefinition.getArgument("input").definition.type as NonNullType).type as TypeName
            val inputType = env.graphQLSchema.getType(inputTypeName.name) as GraphQLInputObjectType?
            if (inputType != null) {
                val clearance = RestrictedDirective.fromInputFieldDef(inputType.getField(fieldName))
                val datasetId = datasetIdFrom(env)
                val teamId = teamIdFrom(env)
                checkClientScope(env, token)
                (clearance.isEmpty() || clearance.any {
                    when (it) {
                        RestrictedDirective.ClearanceLevel.PLATFORM_MANAGER -> token.user.platformManager
                        RestrictedDirective.ClearanceLevel.DATASET_MANAGER -> isDatasetManager(token, datasetId)
                        RestrictedDirective.ClearanceLevel.DATASET_MEMBER -> isDatasetMember(token, datasetId)
                        RestrictedDirective.ClearanceLevel.ME -> isPersonal(token, env)
                        RestrictedDirective.ClearanceLevel.TEAM_MANAGER -> isTeamManager(token, teamId)
                        RestrictedDirective.ClearanceLevel.TEAM_MEMBER -> isTeamMember(token, teamId)
                    }
                }
                        )
            } else {
                false
            }
        }
        else -> false
    }
}

private fun getCtxSafe(env: DataFetchingEnvironment): RoutingContext {
    // Workaround for Vertx not providing a Reactive RoutingContext here...
    return when (val ctx = env.getContext<Any>()) {
        is RoutingContext -> ctx
        is RoutingContextImpl -> RoutingContext(ctx)
        else -> throw IllegalArgumentException()
    }
}

private fun checkClientScope(env: DataFetchingEnvironment, token: Token) {
    if (token.client != null) {
        when (env.operationDefinition.operation) {
            OperationDefinition.Operation.QUERY -> if (!token.client!!.scope.contains(Permission.READ)) throw AuthorizationException(
                "Client cannot perform the query (requires READ scope)"
            )
            OperationDefinition.Operation.MUTATION -> if (!token.client!!.scope.contains(Permission.WRITE)) throw AuthorizationException(
                "Client cannot perform the mutation (requires MANAGE scope)"
            )
        }
    }
}

private fun isDatasetMember(token: Token, datasetId: String?): Boolean {
    return if (datasetId != null) token.grants.containsKey(datasetId) else false
}

private fun isDatasetManager(token: Token, datasetId: String?): Boolean {
    return if (datasetId != null) token.grants[datasetId]?.permissions?.contains(Permission.MANAGE) ?: false else false
}

private fun isTeamMember(token: Token, teamId: String?): Boolean {
    return if (teamId != null) token.user.teamMemberships.any { it.teamId == teamId } else false
}

private fun isTeamManager(token: Token, teamId: String?): Boolean {
    return if (teamId != null) token.user.teamMemberships.find { it.teamId == teamId }?.manager ?: false else false
}

private fun isPersonal(token: Token, env: DataFetchingEnvironment): Boolean {
    return when (val source = env.getSource<Any>()) {
        is User -> source.id == token.user.id
        is DataStream -> source.userId == token.user.id
        is DataExport -> source.userId == token.user.id
        is Client -> source.userId == token.user.id
        is AccessRequest -> source.userId == token.user.id
        else -> false
    }
}

private val datasetSensitiveFields = setOf("membership", "dataset")
private fun datasetIdFrom(env: DataFetchingEnvironment): String? {
    return when (val source = env.getSource<Any>()) {
        is Dataset -> source.id // Dataset field access
        is Role -> source.datasetId
        is Membership -> source.datasetId
        is User -> if (datasetSensitiveFields.contains(env.field.name)) env.getArgument<String>("id") else null
        is AccessRequest -> source.datasetId
        is Team -> if (datasetSensitiveFields.contains(env.field.name)) env.getArgument<String>("id") else null
        else -> null
    }
}

private fun teamIdFrom(env: DataFetchingEnvironment): String? {
    return when (val source = env.getSource<Any>()) {
        is Team -> source.id
        is Client -> source.teamId
        is DataStream -> source.teamId
        is DataExport -> source.teamId
        is TeamInvite -> source.teamId
        is Dataset -> env.getArgument("teamId")
        is AccessRequest -> source.teamId
        is Invite -> env.getArgument("teamId")
        else -> null
    }
}