import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import idlab.obelisk.definitions.AlreadyExistsException
import idlab.obelisk.definitions.catalog.Client
import idlab.obelisk.definitions.catalog.MetaStore
import idlab.obelisk.definitions.catalog.User
import idlab.obelisk.definitions.catalog.codegen.UserUpdate
import idlab.obelisk.definitions.framework.OblxConfig
import idlab.obelisk.plugins.metastore.mongo.MongoDBMetaStore
import idlab.obelisk.plugins.metastore.mongo.initialize
import idlab.obelisk.utils.mongo.MongoCollections
import idlab.obelisk.utils.service.reactive.flatMap
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
import kotlin.random.Random

@ExtendWith(VertxExtension::class)
class UniquenessTest {

    companion object {
        private const val adminEmail = "admin@obelisk.ilabt.imec.be"

        private lateinit var mongoClient: MongoClient
        private lateinit var identityStore: MetaStore
        private val config = OblxConfig()

        @JvmStatic
        @BeforeAll
        fun init(vertx: Vertx) {
            DatabindCodec.mapper().registerKotlinModule()

            mongoClient = MongoClient.create(
                vertx,
                JsonObject().put("connection_string", config.mongoConnectionUri)
                    .put("db_name", config.mongoDbName + "-testUniqueness")
            )
            identityStore = MongoDBMetaStore(mongoClient)
            initialize(identityStore as MongoDBMetaStore, mongoClient, config)
        }

        @JvmStatic
        @AfterAll
        fun destroy(context: VertxTestContext) {
            Completable.mergeArray(
                mongoClient.rxDropCollection(MongoCollections.users),
                mongoClient.rxDropCollection(MongoCollections.clients)
            ).subscribeBy(
                onComplete = context::completeNow,
                onError = context::failNow
            )
        }
    }

    @Test
    fun testInsert(context: VertxTestContext) {
        // Try creating a user with an existing email
        identityStore.createUser(User(email = adminEmail))
            .subscribeBy(
                onSuccess = context::failNow,
                onError = { err ->
                    context.verify { Assertions.assertTrue { err is AlreadyExistsException } }.completeNow()
                }
            )
    }

    @Test
    fun testUpdate(context: VertxTestContext) {
        // Create random new user
        identityStore.createUser(User(email = "${System.currentTimeMillis()}-${Random.nextInt()}@oblx.io"))
            .flatMapCompletable { userId ->
                // Try to update the email to an existing one
                identityStore.updateUser(userId, UserUpdate(email = adminEmail))
            }
            .subscribeBy(
                onComplete = { context.failNow("This operation should fail!") },
                onError = { err ->
                    context.verify { Assertions.assertTrue { err is AlreadyExistsException } }.completeNow()
                }
            )
    }

    @Test
    fun testInsertCompositeKey(context: VertxTestContext) {
        // Try creating a new client with an existing name for the admin
        identityStore.createClient(Client(name = config.webClientsClientId, userId = "0"))
            .subscribeBy(
                onSuccess = context::failNow,
                onError = { err ->
                    context.verify { Assertions.assertTrue { err is AlreadyExistsException } }.completeNow()
                }
            )
    }

}
