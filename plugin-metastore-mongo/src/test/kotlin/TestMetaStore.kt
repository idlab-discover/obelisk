package idlab.obelisk.plugins.metastore.mongo

import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import idlab.obelisk.definitions.catalog.*
import idlab.obelisk.definitions.framework.OblxConfig
import idlab.obelisk.utils.mongo.MongoCollections
import io.reactivex.rxkotlin.subscribeBy
import io.vertx.core.json.Json
import io.vertx.core.json.JsonObject
import io.vertx.core.json.jackson.DatabindCodec
import io.vertx.junit5.VertxExtension
import io.vertx.junit5.VertxTestContext
import io.vertx.reactivex.core.Vertx
import io.vertx.reactivex.ext.mongo.MongoClient
import org.junit.jupiter.api.*
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(VertxExtension::class)
class TestMetaStore {

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
                    .put("db_name", config.mongoDbName + "-test")
            )
            metaStore = MongoDBMetaStore(mongoClient)

            context.completeNow()
        }
    }

    @BeforeEach
    fun clean(context: VertxTestContext) {
        mongoClient.rxDropCollection(MongoCollections.datasets)
            .concatWith(mongoClient.rxDropCollection(MongoCollections.roles))
            .concatWith(mongoClient.rxDropCollection(MongoCollections.users))
            .concatWith(mongoClient.rxDropCollection(MongoCollections.streams))
            .subscribe(
                { context.completeNow() },
                { context.failNow(it) }
            )
    }

    @Test
    fun agrGrantTest(context: VertxTestContext) {
        val ds = Dataset("123", "TestDataset", "Some Description, ")
        val roleReadWrite = Role(
            "id-read-write",
            "ReadWrite",
            datasetId = ds.id!!,
            grant = Grant(setOf(Permission.READ, Permission.WRITE))
        )
        val roleWriteManage = Role(
            "id-write-manage",
            "WriteManage",
            datasetId = ds.id!!,
            grant = Grant(setOf(Permission.WRITE, Permission.MANAGE))
        )
        val user = User(
            "Me", "ik@somewhere.com", "Me", "Ikke", false,
            datasetMemberships = listOf(
                DatasetMembership(ds.id!!, setOf(roleReadWrite.id!!, roleWriteManage.id!!)),
            )
        )

        metaStore.createDataset(ds)
            .flatMap { metaStore.createRole(roleReadWrite) }
            .flatMap { metaStore.createRole(roleWriteManage) }
            .flatMap { metaStore.createUser(user) }
            .flatMap { metaStore.getAggregatedGrantsForUser(user.id!!, ds.id!!) }
            .subscribe(
                {
                    context.verify {
                        val grant = it[ds.id!!]!!
                        Assertions.assertTrue(
                            grant.permissions.containsAll(listOf(Permission.READ, Permission.WRITE, Permission.MANAGE))
                                    && grant.permissions.size == 3
                        )
                    }.completeNow()
                },
                context::failNow
            )
    }

    @Test
    fun agrGrantTestWithOneEmptyRoleMembership(context: VertxTestContext) {
        val ds = Dataset("123", "TestDataset", "Some Description, ")
        val roleEmpty = Role("id-empty", "Empty", datasetId = ds.id!!, grant = Grant(setOf()))
        val roleReadWrite = Role(
            "id-read-write",
            "ReadWrite",
            datasetId = ds.id!!,
            grant = Grant(setOf(Permission.READ, Permission.WRITE))
        )
        val roleWriteManage = Role(
            "id-write-manage",
            "WriteManage",
            datasetId = ds.id!!,
            grant = Grant(setOf(Permission.WRITE, Permission.MANAGE))
        )
        val user = User(
            "Me", "ik@somewhere.com", "Me", "Ikke", false,
            datasetMemberships = listOf(
                DatasetMembership(ds.id!!, setOf(roleEmpty.id!!, roleReadWrite.id!!, roleWriteManage.id!!))
            )
        )

        metaStore.createDataset(ds)
            .flatMap { metaStore.createRole(roleEmpty) }
            .flatMap { metaStore.createRole(roleReadWrite) }
            .flatMap { metaStore.createRole(roleWriteManage) }
            .flatMap { metaStore.createUser(user) }
            .flatMap { metaStore.getAggregatedGrantsForUser(user.id!!, ds.id!!) }
            .subscribe(
                {
                    context.verify {
                        val grant = it[ds.id!!]!!
                        Assertions.assertTrue(
                            grant.permissions.containsAll(listOf(Permission.READ, Permission.WRITE, Permission.MANAGE))
                                    && grant.permissions.size == 3
                        )
                    }.completeNow()
                },
                context::failNow
            )
    }

    @Test
    fun agrGrantTestWithAllEmptyRoleMembership(context: VertxTestContext) {
        val ds = Dataset("123", "TestDataset", "Some Description, ")
        val roleEmpty1 = Role("id-empty1", "Empty1", datasetId = ds.id!!, grant = Grant(setOf()))
        val roleEmpty2 = Role("id-empty2", "Empty2", datasetId = ds.id!!, grant = Grant(setOf()))
        val user = User(
            "Me", "ik@somewhere.com", "Me", "Ikke", false,
            datasetMemberships = listOf(
                DatasetMembership(ds.id!!, setOf(roleEmpty1.id!!, roleEmpty2.id!!))
            )
        )

        metaStore.createDataset(ds)
            .flatMap { metaStore.createRole(roleEmpty1) }
            .flatMap { metaStore.createRole(roleEmpty2) }
            .flatMap { metaStore.createUser(user) }
            .flatMap { metaStore.getAggregatedGrantsForUser(user.id!!, ds.id!!) }
            .subscribe(
                {
                    context.verify {
                        val grant = it[ds.id!!]!!
                        Assertions.assertTrue(grant.permissions.isEmpty())
                    }.completeNow()
                },
                context::failNow
            )
    }

    @Test
    fun agrGrantTestWithEmptyMembership(context: VertxTestContext) {
        val ds = Dataset("123", "TestDataset", "Some Description, ")
        val user = User(
            "Me", "ik@somewhere.com", "Me", "Ikke", false,
            datasetMemberships = listOf(
                DatasetMembership(ds.id!!, setOf())
            )
        )

        metaStore.createDataset(ds)
            .flatMap { metaStore.createUser(user) }
            .flatMap { metaStore.getAggregatedGrantsForUser(user.id!!, ds.id!!) }
            .subscribe(
                {
                    context.verify {
                        val grant = it[ds.id!!]!!
                        Assertions.assertTrue(grant.permissions.isEmpty())
                    }.completeNow()
                },
                context::failNow
            )
    }


    @Test
    fun testDotInKeys(context: VertxTestContext) {
        val id = "junit.test.ds1"
        val json = """
            {
               "_id":"$id",
               "name":"$id",
               "userId":"0",
               "properties":{
                  "id":"urn:ngsi-ld:Subscription:mySubscription",
                  "type":"Subscription",
                  "entities":[
                     {
                        "type":"Vehicle"
                     }
                  ],
                  "watchedAttributes":[
                     "speed"
                  ],
                  "q":"speed>50",
                  "isActive":true,
                  "notification":{
                     "format":"normalized",
                     "endpoint":{
                        "uri":"http://127.0.0.1:8804/notify",
                        "accept":"application/json"
                     }
                  }
               },
               "dataRange":{
                  "datasets":[
                     "5f2939f7380a3902ccd36f77"
                  ],
                  "metrics":[
                     "*::json"
                  ]
               },
               "timestampPrecision":"milliseconds",
               "fields":[
                  "dataset",
                  "metric",
                  "value",
                  "source",
                  "tags",
                  "tsReceived",
                  "location",
                  "producer"
               ],
               "filter":{
                  "_and":[
                     {
                        "_withAnyTag":[
                           "_type=https://uri.etsi.org/ngsi-ld/default-context/Vehicle"
                        ]
                     },
                     {
                        "value->https://uri.etsi.org/ngsi-ld/hasValue->[1]->@value":{
                           "_gt":50.0
                        }
                     }
                  ]
               }
            }
        """.trimIndent()

        val dataStream = Json.decodeValue(json, DataStream::class.java)
        metaStore.createDataStream(dataStream)
            .flatMap {
                metaStore.getDataStream(id).toSingle()
            }
            .subscribeBy(
                onSuccess = { result ->
                    context.verify {
                        Assertions.assertEquals(dataStream, result)
                    }.completeNow()
                },
                onError = context::failNow
            )
    }
}
