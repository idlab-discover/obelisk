import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import idlab.obelisk.definitions.catalog.*
import idlab.obelisk.definitions.framework.OblxConfig
import idlab.obelisk.plugins.metastore.mongo.DEFAULT_USAGE_LIMITS
import idlab.obelisk.plugins.metastore.mongo.MongoDBMetaStore
import idlab.obelisk.plugins.metastore.mongo.initialize
import idlab.obelisk.utils.mongo.MongoCollections
import io.reactivex.rxkotlin.subscribeBy
import io.reactivex.rxkotlin.toFlowable
import io.vertx.core.json.JsonObject
import io.vertx.core.json.jackson.DatabindCodec
import io.vertx.junit5.VertxExtension
import io.vertx.junit5.VertxTestContext
import io.vertx.reactivex.core.Vertx
import io.vertx.reactivex.ext.mongo.MongoClient
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(VertxExtension::class)
class TestUsageLimits {

    companion object {

        private lateinit var mongoClient: MongoClient
        private lateinit var identityStore: MetaStore

        @JvmStatic
        @BeforeAll
        fun init(vertx: Vertx, context: VertxTestContext) {
            DatabindCodec.mapper().registerKotlinModule()

            val config = OblxConfig()
            mongoClient = MongoClient.create(
                vertx,
                JsonObject().put("connection_string", config.mongoConnectionUri)
                    .put("db_name", "${config.mongoDbName}-test")
            )
            identityStore = MongoDBMetaStore(mongoClient)

            // Cleanup potential previous state first
            cleanUp(context)

            initialize(identityStore as MongoDBMetaStore, mongoClient,  config)
        }

        @JvmStatic
        @AfterAll
        fun cleanUp(context: VertxTestContext) {
            listOf(
                MongoCollections.clients,
                MongoCollections.usageLimits,
                MongoCollections.usagePlans,
                MongoCollections.users,
                MongoCollections.teams
            )
                .toFlowable()
                .flatMapCompletable { mongoClient.rxDropCollection(it) }
                .subscribeBy(
                    onComplete = context::completeNow,
                    onError = context::failNow
                )
        }
    }


    @Test
    fun testUserDefault(context: VertxTestContext) {
        val user = User(
            email = "tester@oblx.io",
            firstName = "Tester",
            lastName = "McTesterson"
        )
        identityStore.getAggregatedUsageLimits(user)
            .subscribeBy(
                onSuccess = { limit ->
                    context.verify { Assertions.assertEquals(DEFAULT_USAGE_LIMITS, limit) }.completeNow()
                },
                onError = context::failNow
            )
    }

    @Test
    fun testTeamPlan(context: VertxTestContext) {
        val upgradedLimit = DEFAULT_USAGE_LIMITS.copy(
            id = null,
            name = "UpgradedLimits",
            defaultLimit = false,
            values = DEFAULT_USAGE_LIMITS.values.mapValues { if(it.key == UsageLimitId.maxHourlyPrimitiveEventsStored) it.value * 10 else it.value }
        )

        // Created upgraded limits
        identityStore.createUsageLimit(upgradedLimit)
            .flatMap { limitId ->
                // Assign to a Usage Plan and create it
                identityStore.createUsagePlan(
                    UsagePlan(
                        name = "UpgradedPlan",
                        maxUsers = 10,
                        userUsageLimitId = limitId,
                        maxClients = 10
                    )
                )
            }
            .flatMap { planId ->
                // Assign the plan to a new Team and create it
                identityStore.createTeam(Team(name = "TestTeam1", usagePlanId = planId))
            }
            .flatMap { teamId ->
                // Assign the team to a new User
                identityStore.createUser(
                    User(
                        email = "test@oblx.io",
                        teamMemberships = listOf(TeamMembership(teamId = teamId))
                    )
                )
            }
            .flatMap { userId ->
                // Retrieve user and get aggregate usagelimits
                identityStore.getUser(userId)
                    .toSingle().flatMap { identityStore.getAggregatedUsageLimits(it) }
            }
            .subscribeBy(
                onSuccess = { limit ->
                    // Check if the limit for the User was upgraded through the team relationship
                    context.verify {
                        Assertions.assertEquals(
                            upgradedLimit.values[UsageLimitId.maxHourlyPrimitiveEventsStored],
                            limit.values[UsageLimitId.maxHourlyPrimitiveEventsStored]
                        )
                    }.completeNow()
                },
                onError = context::failNow
            )
    }

    @Test
    fun testAggregateLimit(context: VertxTestContext) {
        val upgradedIngestLimit = DEFAULT_USAGE_LIMITS.copy(
            id = null,
            name = "UpgradedIngestLimits",
            defaultLimit = false,
            values = DEFAULT_USAGE_LIMITS.values.mapValues { if(it.key == UsageLimitId.maxHourlyPrimitiveEventsStored) it.value * 10 else it.value }
        )
        val upgradedQueryLimit = DEFAULT_USAGE_LIMITS.copy(
            id = null,
            name = "UpgradedQueryLimits",
            defaultLimit = false,
            values = DEFAULT_USAGE_LIMITS.values.mapValues { if(it.key == UsageLimitId.maxHourlyPrimitiveEventQueries) it.value * 10 else it.value }
        )
        val upgradedIngestLimitID = identityStore.createUsageLimit(upgradedIngestLimit)
            .blockingGet() // Yes, I use blocking gets. These are test cases, sue me :P

        val teamWithUpgradedQueries = identityStore.createUsageLimit(upgradedQueryLimit)
            .flatMap { limitId ->
                // Assign to a Usage Plan and create it
                identityStore.createUsagePlan(
                    UsagePlan(
                        name = "UpgradedQueryPlan",
                        maxUsers = 10,
                        userUsageLimitId = limitId,
                        maxClients = 10
                    )
                )
            }
            .flatMap { planId ->
                // Assign the plan to a new Team and create it
                identityStore.createTeam(Team(name = "TestTeam2", usagePlanId = planId))
            }.blockingGet()

        val userWithUpgradedIngest = User(
            email = "tester@oblx.io",
            firstName = "Tester",
            lastName = "McTesterson",
            usageLimitId = upgradedIngestLimitID,
            teamMemberships = listOf(TeamMembership(teamWithUpgradedQueries))
        )

        identityStore.getAggregatedUsageLimits(userWithUpgradedIngest)
            .subscribeBy(
                onSuccess = { limit ->
                    context.verify {
                        Assertions.assertEquals(upgradedIngestLimit.values[UsageLimitId.maxHourlyPrimitiveEventsStored], limit.values[UsageLimitId.maxHourlyPrimitiveEventsStored])
                        Assertions.assertEquals(upgradedQueryLimit.values[UsageLimitId.maxHourlyPrimitiveEventQueries], limit.values[UsageLimitId.maxHourlyPrimitiveEventQueries])
                    }.completeNow()
                },
                onError = context::failNow
            )
    }


}
