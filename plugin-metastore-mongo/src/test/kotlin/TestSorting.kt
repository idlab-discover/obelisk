import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import idlab.obelisk.definitions.Ordering
import idlab.obelisk.definitions.SELECT_ALL
import idlab.obelisk.definitions.catalog.User
import idlab.obelisk.definitions.catalog.codegen.UserField
import idlab.obelisk.definitions.framework.OblxConfig
import idlab.obelisk.plugins.metastore.mongo.MongoDBMetaStore
import idlab.obelisk.utils.mongo.MongoCollections
import io.reactivex.rxkotlin.toFlowable
import io.vertx.core.json.JsonObject
import io.vertx.core.json.jackson.DatabindCodec
import io.vertx.junit5.VertxExtension
import io.vertx.reactivex.core.Vertx
import io.vertx.reactivex.ext.mongo.MongoClient
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.ajbrown.namemachine.NameGenerator
import org.junit.jupiter.api.Assertions

@ExtendWith(VertxExtension::class)
class TestSorting {

    @Test
    fun testSort(vertx: Vertx) {
        DatabindCodec.mapper().registerKotlinModule()

        val config = OblxConfig()
        val mongoClient = MongoClient.create(
            vertx,
            JsonObject().put("connection_string", config.mongoConnectionUri)
                .put("db_name", config.mongoDbName + "-testSorting")
        )
        val metaStore = MongoDBMetaStore(mongoClient)

        mongoClient.rxDropCollection(MongoCollections.users).blockingAwait()

        val nameGenerator = NameGenerator()
        val users = (0..15).map {
            val name = nameGenerator.generateName()
            User(
                email = "${name.firstName}.${name.lastName}@oblx.io",
                firstName = name.firstName,
                lastName = name.lastName
            )
        }

        users.toFlowable().flatMapCompletable { metaStore.createUser(it).ignoreElement() }.blockingAwait()
        val sortedUsers =
            metaStore.queryUsers(SELECT_ALL, sort = mapOf(UserField.FIRST_NAME to Ordering.desc)).blockingGet().items

        users.sortedByDescending { it.firstName }.zip(sortedUsers)
            .forEach { (orig, stored) -> Assertions.assertEquals(orig.firstName, stored.firstName) }
    }

}
