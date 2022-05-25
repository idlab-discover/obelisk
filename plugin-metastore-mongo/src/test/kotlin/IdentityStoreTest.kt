import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import idlab.obelisk.definitions.catalog.MetaStore
import idlab.obelisk.definitions.catalog.User
import idlab.obelisk.definitions.catalog.codegen.UserUpdate
import idlab.obelisk.definitions.framework.OblxConfig
import idlab.obelisk.plugins.metastore.mongo.MongoDBMetaStore
import idlab.obelisk.utils.mongo.MongoCollections
import idlab.obelisk.utils.mongo.query.byExample
import idlab.obelisk.utils.service.reactive.flatMap
import idlab.obelisk.utils.service.reactive.flatMapSingle
import io.reactivex.Completable
import io.reactivex.rxkotlin.subscribeBy
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
class IdentityStoreTest {

    companion object {

        private lateinit var mongoClient: MongoClient
        private lateinit var identityStore: MetaStore
        private lateinit var testUserId: String
        private lateinit var testGroupId: String

        @JvmStatic
        @BeforeAll
        fun init(vertx: Vertx, context: VertxTestContext) {
            DatabindCodec.mapper().registerKotlinModule()

            val config = OblxConfig()
            mongoClient = MongoClient.create(
                vertx,
                JsonObject().put("connection_string", config.mongoConnectionUri).put("db_name", config.mongoDbName)
            )
            identityStore = MongoDBMetaStore(mongoClient)

            // Cleanup first
            clean()
                .flatMap {
                    // Add test user
                    identityStore.createUser(
                        User(
                            email = "tester@oblx.io",
                            firstName = "Tester",
                            lastName = "McTesterson"
                        )
                    )
                        .doOnSuccess { testUserId = it }.ignoreElement()
                }
                .subscribeBy(onComplete = context::completeNow, onError = context::failNow)
        }

        @JvmStatic
        @AfterAll
        fun cleanUp(context: VertxTestContext) {
            clean().subscribeBy(onComplete = context::completeNow, onError = context::failNow)
        }

        fun clean(): Completable {
            return mongoClient.rxRemoveDocuments(MongoCollections.users, byExample("lastName" to "McTesterson"))
                .ignoreElement()
                .flatMap {
                    mongoClient.rxRemoveDocuments(MongoCollections.roles, byExample("name" to "test-specimens"))
                        .ignoreElement()
                }
        }

    }

    @Test
    fun testStoreAndGetUser(context: VertxTestContext) {
        val user = User(email = "tester2@oblx.io", firstName = "Tester2", lastName = "McTesterson")
        identityStore.createUser(user)
            .flatMap { id ->
                identityStore.getUser(id).toSingle()
            }
            .subscribeBy(
                onSuccess = {
                    context.verify {
                        Assertions.assertEquals(user.copy(id = it.id), it)
                    }.completeNow()
                },
                onError = context::failNow
            )
    }

    @Test
    fun testUpdateUser(context: VertxTestContext) {
        val newFirstName = "Specules"
        identityStore.updateUser(testUserId, UserUpdate(firstName = newFirstName))
            .flatMapSingle { identityStore.getUser(testUserId).toSingle() }
            .subscribeBy(
                onSuccess = {
                    context.verify { Assertions.assertEquals(newFirstName, it.firstName) }.completeNow()
                },
                onError = context::failNow
            )
    }

}
