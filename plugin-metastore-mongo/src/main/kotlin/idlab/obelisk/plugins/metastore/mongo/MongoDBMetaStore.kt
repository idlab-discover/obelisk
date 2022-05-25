package idlab.obelisk.plugins.metastore.mongo

import idlab.obelisk.annotations.api.GenerateStubsFor
import idlab.obelisk.definitions.Eq
import idlab.obelisk.definitions.In
import idlab.obelisk.definitions.Or
import idlab.obelisk.definitions.SELECT_ALL
import idlab.obelisk.definitions.catalog.*
import idlab.obelisk.definitions.catalog.codegen.RoleField
import idlab.obelisk.definitions.catalog.codegen.UsageLimitField
import idlab.obelisk.definitions.catalog.codegen.UsagePlanField
import idlab.obelisk.plugins.metastore.mongo.codegen.AbstractMongoStorageBase
import idlab.obelisk.utils.mongo.MongoCollections
import idlab.obelisk.utils.mongo.query.`in`
import idlab.obelisk.utils.mongo.rxFind
import idlab.obelisk.utils.mongo.rxFindById
import idlab.obelisk.utils.service.utils.unpage
import io.reactivex.Maybe
import io.reactivex.Single
import io.reactivex.rxkotlin.toFlowable
import io.vertx.reactivex.ext.mongo.MongoClient

@GenerateStubsFor(
        [User::class, Team::class, Client::class, UsagePlan::class, UsageLimit::class, Dataset::class,
            Role::class, Metric::class, Thing::class, AccessRequest::class, DataStream::class, DataExport::class, Announcement::class]
)
class MongoDBMetaStore(private val mongoClient: MongoClient) : AbstractMongoStorageBase(mongoClient), MetaStore {

    override fun getRolesForUser(userId: String, datasetId: String): Maybe<List<Role>> {
        return getUser(userId)
                .flatMap { user ->
                    val rolesForDS = user.datasetMemberships.find { it.datasetId == datasetId }?.assignedRoleIds
                            ?: emptySet()
                    if (rolesForDS.isNotEmpty()) {
                        mongoClient.rxFind(MongoCollections.roles, `in`("_id", rolesForDS.toList()), Role::class.java)
                                .toList().toMaybe()
                    } else {
                        Maybe.empty()
                    }
                }
    }

    override fun getRolesForTeam(teamId: String, datasetId: String): Maybe<List<Role>> {
        return getTeam(teamId)
                .flatMap { team ->
                    val rolesForDS = team.datasetMemberships.find { it.datasetId == datasetId }?.assignedRoleIds
                            ?: emptySet()
                    if (rolesForDS.isNotEmpty()) {
                        mongoClient.rxFind(MongoCollections.roles, `in`("_id", rolesForDS.toList()), Role::class.java)
                                .toList().toMaybe()
                    } else {
                        Maybe.empty()
                    }
                }
    }

    override fun getDefaultUsagePlan(): Single<UsagePlan> {
        return queryUsagePlans(Eq(UsagePlanField.DEFAULT_PLAN, true), 1)
                .flattenAsFlowable { it.items }
                .firstOrError()
    }

    override fun getDefaultUsageLimit(): Single<UsageLimit> {
        return queryUsageLimits(Eq(UsageLimitField.DEFAULT_LIMIT, true), 1)
                .flattenAsFlowable { it.items }
                .firstOrError()
    }

    override fun getAggregatedUsageLimits(user: User, client: Client?): Single<UsageLimit> {
        return getUsageLimitOrDefault(user.usageLimitId)
                .flatMap { userLimit ->
                    if (user.teamMemberships.isNotEmpty()) {
                        // Load all teams the user is a member of
                        mongoClient.rxFind(
                                MongoCollections.teams,
                                `in`("_id", user.teamMemberships.map { it.teamId }),
                                Team::class.java
                        )
                                // Filter out duplicates and teams without a specific plan set (using Kotlin collection operators as Rx cannot handle nulls)
                                .toList()
                                .flattenAsFlowable { teams -> teams.mapNotNull { it.usagePlanId }.distinct() }
                                // Load the remaining (unique) usage plans
                                .flatMapMaybe { getUsagePlan(it) }
                                // Fall back on default plan if no team defined plan was found
                                .switchIfEmpty(getDefaultUsagePlan().toFlowable())
                                // Map the plan to the correct UsageLimit based on the plan and if we are aggregating for the User or a client (client != null)
                                .flatMapMaybe { plan ->
                                    if (client == null && plan.userUsageLimitId != null) {
                                        getUsageLimit(plan.userUsageLimitId!!)
                                    } else if (client != null && plan.clientUsageLimitId != null) {
                                        getUsageLimit(plan.clientUsageLimitId!!)
                                    } else {
                                        getDefaultUsageLimit().toMaybe()
                                    }
                                }
                                // Get the mapped UsageLimits for the teams as a list
                                .toList()
                                .map { teamLimits ->
                                    // Now combine all found limits into a max aggregate
                                    val combinedLimits = teamLimits.plus(userLimit)
                                    UsageLimit(
                                            name = "AggregatedSessionLimits",
                                            values = combinedLimits
                                                    .flatMap { it.values.entries }
                                                    .groupBy { it.key }
                                                    .mapValues { e -> e.value.maxOfOrNull { it.value } ?: 0 }
                                    )
                                }
                    } else {
                        // Just return the limit found in the previous step
                        Single.just(userLimit)
                    }
                }
    }

    // Calculate all the grants for a User (based on User & Team membership roles)
    override fun getAggregatedGrantsForUser(userId: String, datasetId: String?): Single<Map<String, Grant>> {
        return getAggregatedDatasetMembershipsForUser(userId, datasetId)
                .flatMap { memberships ->
                    // Group by dataset
                    memberships.values.toFlowable()
                            .flatMapSingle { membership ->
                                unpage { cursor ->
                                    queryRoles(
                                            In(RoleField.ID, membership.assignedRoleIds),
                                            cursor = cursor
                                    )
                                }.toList().map {
                                    Pair(
                                            membership.datasetId,
                                            it
                                    )
                                }
                            }
                            .toList()
                            .map { assignments ->
                                assignments.associate { assig ->
                                    Pair(assig.first, assig.second
                                            .map { it.grant }
                                            .reduceOrNull { g1, g2 ->
                                                val readFilters =
                                                        listOf(g1.readFilter, g2.readFilter).filterNot { it == SELECT_ALL }
                                                Grant(
                                                        g1.permissions.plus(g2.permissions), when (readFilters.size) {
                                                    0 -> SELECT_ALL
                                                    1 -> readFilters.first()
                                                    else -> Or(readFilters)
                                                }
                                                )
                                            } ?: Grant(setOf())
                                    )
                                }
                            }
                }
    }

    override fun getAggregatedDatasetMembershipsForUser(userId: String, datasetId: String?): Single<Map<String, DatasetMembership>> {
        return mongoClient.rxFindById(MongoCollections.users, userId, User::class.java)
                .toSingle()
                .flatMap { user ->
                    // First resolve all the Teams the user is a member of.
                    mongoClient.rxFind(
                            MongoCollections.teams,
                            `in`("_id", user.teamMemberships.map { it.teamId }),
                            Team::class.java
                    ).toList().map { Pair(user, it) }
                }
                .map { (user, teams) ->
                    // Merge user and via teams memberships
                    user.datasetMemberships.union(teams.map { it.datasetMemberships.toSet() }
                            .reduceOrNull { l1, l2 -> l1.union(l2) }.orEmpty())
                            .filter { if (datasetId != null) it.datasetId == datasetId else true }
                            .groupBy { it.datasetId }
                            .let { groupByDs ->
                                groupByDs.mapValues { entries ->
                                    entries.value.reduce { mem1, mem2 ->
                                        DatasetMembership(
                                                mem1.datasetId,
                                                mem1.assignedRoleIds.union(mem2.assignedRoleIds)
                                        )
                                    }
                                }
                            }
                }
    }

    // TODO: can be optimized (fetch all applicable roles in a single query!)
    override fun getAggregatedGrantsForTeam(teamId: String, datasetId: String?): Single<Map<String, Grant>> {
        return mongoClient.rxFindById(MongoCollections.teams, teamId, Team::class.java)
            .toSingle()
            .flatMap { team ->
                // Group by dataset
                team.datasetMemberships.toFlowable()
                    .flatMapSingle { membership ->
                        unpage { cursor ->
                            queryRoles(
                                In(RoleField.ID, membership.assignedRoleIds),
                                cursor = cursor
                            )
                        }.toList().map {
                            Pair(
                                membership.datasetId,
                                it
                            )
                        }
                    }
                    .toList()
                    .map { assignments ->
                        assignments.associate { assig ->
                            Pair(assig.first, assig.second
                                .map { it.grant }
                                .reduceOrNull { g1, g2 ->
                                    val readFilters =
                                        listOf(g1.readFilter, g2.readFilter).filterNot { it == SELECT_ALL }
                                    Grant(
                                        g1.permissions.plus(g2.permissions), when (readFilters.size) {
                                            0 -> SELECT_ALL
                                            1 -> readFilters.first()
                                            else -> Or(readFilters)
                                        }
                                    )
                                } ?: Grant(setOf())
                            )
                        }
                    }
            }
    }

}