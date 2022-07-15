import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import idlab.obelisk.definitions.catalog.MetaStore
import idlab.obelisk.definitions.catalog.Team
import idlab.obelisk.definitions.catalog.codegen.TeamNullableField
import idlab.obelisk.definitions.catalog.codegen.TeamUpdate
import idlab.obelisk.definitions.framework.OblxConfig
import idlab.obelisk.plugins.metastore.mongo.MongoDBMetaStore
import idlab.obelisk.plugins.metastore.mongo.initialize
import idlab.obelisk.utils.mongo.MongoCollections
import idlab.obelisk.utils.service.reactive.flatMapSingle
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
class UpdateTest {

    companion object {
        private lateinit var mongoClient: MongoClient
        private lateinit var metaStore: MetaStore

        @JvmStatic
        @BeforeAll
        fun init(vertx: Vertx, context: VertxTestContext) {
            DatabindCodec.mapper().registerKotlinModule()

            val config = OblxConfig()
            mongoClient = MongoClient.create(
                vertx,
                JsonObject().put("connection_string", config.mongoConnectionUri)
                    .put("db_name", config.mongoDbName + "-testUpdate")
            )
            metaStore = MongoDBMetaStore(mongoClient)

            // Cleanup potential previous state first
            cleanUp(context)

            initialize(metaStore as MongoDBMetaStore, mongoClient, config)
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
    fun testSetToNull(context: VertxTestContext) {
        val team = Team(name = "test1", description = "This is a test team!")
        metaStore.createTeam(team)
            .flatMap {
                // Try to set description to null
                metaStore.updateTeam(it, TeamUpdate(description = null), setOf(TeamNullableField.DESCRIPTION))
                    .flatMapSingle { metaStore.getTeam(it).toSingle() }
            }
            .subscribeBy(
                onSuccess = { context.verify { Assertions.assertNull(it.description) }.completeNow() },
                onError = context::failNow
            )
    }

}
