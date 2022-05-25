package idlab.obelisk.services.pub.catalog.types

import graphql.schema.DataFetchingEnvironment
import idlab.obelisk.definitions.catalog.*
import idlab.obelisk.services.pub.catalog.impl.Membership
import idlab.obelisk.services.pub.catalog.types.util.GQLFetcher
import idlab.obelisk.services.pub.catalog.types.util.GQLType
import idlab.obelisk.services.pub.catalog.types.util.Operations
import io.reactivex.Maybe
import io.reactivex.Single
import java.util.concurrent.CompletionStage
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
@GQLType("Membership")
class GQLMembership @Inject constructor(
    accessManager: AccessManager,
    val metaStore: MetaStore
) : Operations(accessManager) {

    @GQLFetcher
    fun dataset(env: DataFetchingEnvironment): CompletionStage<Dataset> {
        return withAccessMaybe(env) {
            val ms = env.getSource<Membership>()
            metaStore.getDataset(ms.datasetId)
        }
    }

    @GQLFetcher
    fun roles(env: DataFetchingEnvironment): CompletionStage<List<Role>> {
        return withAccess(env) {
            val ms = env.getSource<Membership>()
            (when {
                ms.userId != null -> {
                    metaStore.getRolesForUser(ms.userId, ms.datasetId)
                }
                ms.teamId != null -> {
                    metaStore.getRolesForTeam(ms.teamId, ms.datasetId)
                }
                else -> {
                    Maybe.empty()
                }
            }).toSingle(emptyList())
        }
    }

    @GQLFetcher
    fun aggregatedGrant(env: DataFetchingEnvironment): CompletionStage<Grant> {
        return withAccess(env) {
            val ms = env.getSource<Membership>()

            when {
                ms.userId != null -> {
                    metaStore.getAggregatedGrantsForUser(ms.userId!!, ms.datasetId)
                        .map { it[ms.datasetId] ?: Grant(permissions = emptySet()) }
                }
                ms.teamId != null -> {
                    metaStore.getAggregatedGrantsForTeam(ms.teamId!!, ms.datasetId)
                        .map { it[ms.datasetId] ?: Grant(permissions = emptySet()) }
                }
                else -> Single.just(Grant(permissions = emptySet()))
            }
        }
    }

}