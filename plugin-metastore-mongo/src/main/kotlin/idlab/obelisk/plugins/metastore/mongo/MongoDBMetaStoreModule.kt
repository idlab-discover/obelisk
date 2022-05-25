package idlab.obelisk.plugins.metastore.mongo

import idlab.obelisk.annotations.api.GenerateStubsFor
import idlab.obelisk.annotations.api.OblxType
import idlab.obelisk.definitions.AlreadyExistsException
import idlab.obelisk.definitions.Eq
import idlab.obelisk.definitions.catalog.*
import idlab.obelisk.definitions.catalog.codegen.UsageLimitField
import idlab.obelisk.definitions.catalog.codegen.UsagePlanField
import idlab.obelisk.definitions.framework.OblxConfig
import idlab.obelisk.definitions.framework.OblxModule
import idlab.obelisk.utils.mongo.MongoCollections
import io.reactivex.Completable
import io.reactivex.rxkotlin.toObservable
import io.vertx.core.json.JsonObject
import io.vertx.ext.mongo.IndexOptions
import io.vertx.reactivex.core.Vertx
import io.vertx.reactivex.ext.mongo.MongoClient
import org.codejargon.feather.Provides
import javax.inject.Singleton
import kotlin.reflect.full.findAnnotation

const val DEFAULT_USAGE_LIMITS_ID = "generatedLimits"
val DEFAULT_USAGE_LIMITS = UsageLimit(
    id = DEFAULT_USAGE_LIMITS_ID,
    name = "Standard Limits",
    defaultLimit = true,
    values = mapOf(
        UsageLimitId.maxHourlyPrimitiveEventsStored to 250000,
        UsageLimitId.maxHourlyPrimitiveEventsStreamed to 100000,
        UsageLimitId.maxHourlyComplexEventsStored to 100000,
        UsageLimitId.maxHourlyComplexEventsStreamed to 25000,
        UsageLimitId.maxHourlyPrimitiveEventQueries to 10000,
        UsageLimitId.maxHourlyPrimitiveStatsQueries to 2500,
        UsageLimitId.maxHourlyComplexEventQueries to 2500,
        UsageLimitId.maxHourlyComplexStatsQueries to 100,
        UsageLimitId.maxDataExportRecords to 5000000,
        UsageLimitId.maxDataExports to 5,
        UsageLimitId.maxDataStreams to 3
    )
)
val DEFAULT_USAGE_PLAN = UsagePlan(
    id = "generatedPlan",
    name = "Standard Plan",
    defaultPlan = true,
    maxClients = 25,
    maxUsers = 250,
    clientUsageLimitId = DEFAULT_USAGE_LIMITS_ID,
    userUsageLimitId = DEFAULT_USAGE_LIMITS_ID
)


class MongoDBMetaStoreModule : OblxModule {

    @Provides
    @Singleton
    fun metaStore(vertx: Vertx, config: OblxConfig): MetaStore {
        val mongoClient = MongoClient.createShared(
            vertx,
            JsonObject().put("connection_string", config.mongoConnectionUri).put("db_name", config.mongoDbName)
        )

        val metaStore = MongoDBMetaStore(mongoClient)

        initialize(metaStore, mongoClient, config)

        return metaStore
    }

}

// TODO: find another way for doing this
// Forgive me father, for I have sinned (blocking calls ahead)
internal fun initialize(metaStore: MongoDBMetaStore, mongoClient: MongoClient, config: OblxConfig) {
// INIT IDENTITYSTORE HERE
    // Try to create unique indices
    MongoDBMetaStore::class.findAnnotation<GenerateStubsFor>()
        ?.let { annotation ->
            annotation.oblxTypes.toObservable().flatMapCompletable { type ->
                val uniqueFields = type.findAnnotation<OblxType>()?.uniqueFields
                if (uniqueFields != null && uniqueFields.isNotEmpty()) {
                    uniqueFields.toObservable().flatMapCompletable { field ->
                        val index = field.split(",").map { Pair(it.trim(), 1) }.toMap()
                        mongoClient.rxCreateIndexWithOptions(
                            "${type.simpleName!!.decapitalize()}s",
                            JsonObject(index),
                            IndexOptions().unique(true)
                        )
                    }
                } else {
                    Completable.complete()
                }
            }.blockingAwait()
        }

    metaStore.getUser("0").toSingle().ignoreElement()
        .onErrorResumeNext { err ->
            metaStore.createUser(
                User(
                    id = "0",
                    email = "admin@obelisk.ilabt.imec.be",
                    firstName = "Admin",
                    lastName = "Account",
                    platformManager = true
                )
            ).ignoreElement()
        }
        .onErrorComplete { it is AlreadyExistsException } // Ignore AlreadyExistException
        .blockingAwait()
    // TODO: we should get rid of admin being user with id 0 (should be more dynamic, config driven)
    metaStore.getClient("0")
        .toSingle()
        .ignoreElement()
        .onErrorResumeNext { err ->
            if (err is NoSuchElementException) {
                // Only init client if it doesn't exist (reason: don't conflict with init in Auth)
                metaStore.createClient(Client(id = "0", name = config.webClientsClientId, userId = "0"))
                    .ignoreElement()
            } else {
                Completable.error { err }
            }
        }
        .blockingAwait()

    // Adding default usage plan & limits
    metaStore.queryUsagePlans(filter = Eq(UsagePlanField.DEFAULT_PLAN, true))
        .flatMapCompletable { plans ->
            if (plans.items.isEmpty()) {
                // If a default plan cannot be found, create one:
                metaStore.createUsagePlan(DEFAULT_USAGE_PLAN).ignoreElement()
            } else {
                Completable.complete()
            }
        }
        .blockingAwait()

    metaStore.queryUsageLimits(filter = Eq(UsageLimitField.DEFAULT_LIMIT, true))
        .flatMapCompletable { limitsResults ->
            if (limitsResults.items.isEmpty()) {
                // If a default limits instance cannot be found, create one:
                metaStore.createUsageLimit(DEFAULT_USAGE_LIMITS).ignoreElement()
            } else {
                Completable.complete()
            }
        }
        .blockingAwait()

}