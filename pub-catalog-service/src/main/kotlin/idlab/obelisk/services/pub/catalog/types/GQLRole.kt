package idlab.obelisk.services.pub.catalog.types

import graphql.schema.DataFetchingEnvironment
import idlab.obelisk.definitions.And
import idlab.obelisk.definitions.Eq
import idlab.obelisk.definitions.Field
import idlab.obelisk.definitions.PagedResult
import idlab.obelisk.definitions.catalog.*
import idlab.obelisk.definitions.catalog.codegen.DatasetMembershipField
import idlab.obelisk.definitions.catalog.codegen.TeamField
import idlab.obelisk.definitions.catalog.codegen.UserField
import idlab.obelisk.services.pub.catalog.impl.GraphQLPage
import idlab.obelisk.services.pub.catalog.impl.getFilter
import idlab.obelisk.services.pub.catalog.types.util.GQLFetcher
import idlab.obelisk.services.pub.catalog.types.util.GQLType
import idlab.obelisk.services.pub.catalog.types.util.Operations
import java.util.concurrent.CompletionStage
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
@GQLType("Role")
class GQLRole @Inject constructor(
    private val metaStore: MetaStore,
    accessManager: AccessManager
) : Operations(accessManager) {

    @GQLFetcher
    fun dataset(env: DataFetchingEnvironment): CompletionStage<Dataset> {
        return withAccessMaybe(env) {
            val role = env.getSource<Role>()
            metaStore.getDataset(role.datasetId!!)
        }
    }

    @GQLFetcher
    fun user(env: DataFetchingEnvironment): CompletionStage<User> {
        return withAccess(env) {
            val role = env.getSource<Role>()
            metaStore.getUser(env.getArgument("id"))
                .filter { user ->
                    user.datasetMemberships.any {
                        it.datasetId == role.datasetId && it.assignedRoleIds.contains(
                            role.id
                        )
                    }
                }
                .toSingle()
        }
    }

    @GQLFetcher
    fun users(env: DataFetchingEnvironment): CompletionStage<GraphQLPage<User>> {
        return withAccess(env) {
            val role = env.getSource<Role>()
            val filter = And(
                Eq(Field(UserField.DATASET_MEMBERSHIPS, DatasetMembershipField.DATASET_ID), role.datasetId),
                Eq(Field(UserField.DATASET_MEMBERSHIPS, DatasetMembershipField.ASSIGNED_ROLE_IDS), role.id!!),
                env.getFilter()
            )
            metaStore.queryUsers(
                filter, limitFrom(env), cursorFrom(env)
            )
                .map { GraphQLPage(it.items, it.cursor) { metaStore.countUsers(filter) } }
        }
    }

    @GQLFetcher
    fun team(env: DataFetchingEnvironment): CompletionStage<Team> {
        return withAccess(env) {
            val role = env.getSource<Role>()
            metaStore.getTeam(env.getArgument("id"))
                .filter { team ->
                    team.datasetMemberships.any {
                        it.datasetId == role.datasetId && it.assignedRoleIds.contains(
                            role.id
                        )
                    }
                }
                .toSingle()
        }
    }

    @GQLFetcher
    fun teams(env: DataFetchingEnvironment): CompletionStage<GraphQLPage<Team>> {
        return withAccess(env) {
            val role = env.getSource<Role>()
            val filter = And(
                Eq(Field(TeamField.DATASET_MEMBERSHIPS, DatasetMembershipField.DATASET_ID), role.datasetId),
                Eq(Field(TeamField.DATASET_MEMBERSHIPS, DatasetMembershipField.ASSIGNED_ROLE_IDS), role.id!!),
                env.getFilter()
            )
            metaStore.queryTeams(
                filter, limitFrom(env), cursorFrom(env)
            )
                .map { GraphQLPage(it.items, it.cursor) { metaStore.countTeams(filter) } }
        }
    }

}