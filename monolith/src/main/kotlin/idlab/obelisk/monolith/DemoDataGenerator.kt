package idlab.obelisk.monolith

import idlab.obelisk.definitions.And
import idlab.obelisk.definitions.Eq
import idlab.obelisk.definitions.MetricName
import idlab.obelisk.definitions.catalog.*
import idlab.obelisk.definitions.catalog.codegen.*
import idlab.obelisk.definitions.data.DataStore
import idlab.obelisk.definitions.data.MetricEvent
import idlab.obelisk.definitions.data.Producer
import idlab.obelisk.definitions.framework.OblxConfig
import idlab.obelisk.definitions.framework.OblxService
import idlab.obelisk.services.pub.catalog.impl.DefaultRoles
import idlab.obelisk.utils.service.reactive.firstOrEmpty
import io.reactivex.Completable
import io.reactivex.Flowable
import io.reactivex.Observable
import io.reactivex.Single
import io.reactivex.rxkotlin.subscribeBy
import io.vertx.reactivex.core.Vertx
import mu.KotlinLogging
import java.util.*
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random

private const val OFFICE_LAB_PERIOD_MS = 1000L
private const val AQ_PERIOD_MS = 250L
private const val ADMIN_ID = "0";

@Singleton
class DemoDataGenerator @Inject constructor(
    private val vertx: Vertx,
    private val metaStore: MetaStore,
    private val dataStore: DataStore,
    private val config: OblxConfig
) : OblxService {
    private val logger = KotlinLogging.logger { }


    private val powerUsers = (1..5).map { "user$it" };
    private val dataset1 = "demo.office-lab"
    private val dataset2 = "demo.airquality"
    private val datasets = mutableMapOf<String, Dataset>()
    private var officeLabClient: Client? = null
    private var airqualityClient: Client? = null
    private var timer: Observable<Long>? = null;

    override fun start(): Completable {
        timer = System.getenv("POPULATE_DEMO_DATA_MINUTES")?.toLong()
            .let { if (it != null) Observable.timer(it, TimeUnit.MINUTES) else Observable.never() };
        return Completable.concatArray(
            setupDataset(dataset1), // Make sure there is a dataset 'demo.office-lab' with the appropriate grants
            setupDataset(dataset2), // Make sure there is a dataset 'demo.airquality' with the appropriate grants
            getAdmin().flatMapCompletable {
                clientGetOrCreate(
                    it.id!!,
                    "power-meter-cred"
                ).doOnSuccess { officeLabClient = it }.ignoreElement()
            }, // Create office-lab clients for all users
            getAdmin().flatMapCompletable {
                clientGetOrCreate(
                    it.id!!,
                    "aq-gateway-cred"
                ).doOnSuccess { airqualityClient = it }.ignoreElement()
            } // Create single client for airquality dataset
        ).doOnComplete {
            // Setup data generators
            setupAQSim()
            setupOfficeLabSims()
        }
    }

    private fun setupDataset(dataset: String): Completable {
        return datasetGetOrCreate(dataset).ignoreElement()
    }

    private fun getAdmin(): Single<User> {
        return metaStore.getUser(ADMIN_ID).toSingle();
    }

    private fun datasetGetOrCreate(name: String): Single<Dataset> {
        return metaStore.queryDatasets(Eq(DatasetField.NAME, name)).firstOrEmpty()
            .toSingle()
            .onErrorResumeNext { err ->
                if (err is NoSuchElementException) {
                    metaStore
                        .createDataset(
                            Dataset(
                                name = name,
                                published = true,
                                openData = true,
                                description = "This is a standard description for a dataset. It explains what this dataset is all about.",
                                keywords = setOf("demo", "generated", "new"),
                                contactPoint = "admin@obelisk.ilabt.imec.be"
                            )
                        )
                        .flatMap { datasetId ->
                            metaStore.createRole(
                                Role(
                                    name = DefaultRoles.manager.toString(),
                                    description = DefaultRoles.manager.description,
                                    datasetId = datasetId,
                                    grant = Grant(permissions = Permission.all())
                                )
                            ).map { roleId -> Pair(datasetId, roleId) }
                        }
                        .flatMap { pair ->
                            getAdmin()
                                .flatMap { user ->
                                    metaStore.updateUser(
                                        user.id!!, UserUpdate(
                                            datasetMemberships = user.datasetMemberships.plus(
                                                listOf(
                                                    DatasetMembership(pair.first, setOf(pair.second))
                                                )
                                            )
                                        )
                                    ).toSingleDefault(pair)
                                }
                        }
                        .flatMap { pair -> metaStore.getDataset(pair.first).toSingle() }
                } else {
                    Single.error { err }
                }
            }
            .doOnSuccess { datasets[name] = it }
    }

    private fun clientGetOrCreate(userId: String, clientName: String): Single<Client> {
        return metaStore.queryClients(And(Eq(ClientField.USER_ID, userId), Eq(ClientField.NAME, clientName)))
            .flattenAsFlowable { it.items }
            .singleOrError()
            .onErrorResumeNext { err ->
                if (err is NoSuchElementException) {
                    metaStore.createClient(
                        Client(
                            userId = userId,
                            name = clientName,
                            confidential = true,
                            onBehalfOfUser = false
                        )
                    )
                        .flatMap { metaStore.getClient(it).toSingle() }
                } else {
                    Single.error { err }
                }
            }
    }

    private fun setupOfficeLabSims() {
        var timerId = vertx.setPeriodic(OFFICE_LAB_PERIOD_MS) {
            Flowable.fromIterable(powerUsers)
                .map { user ->
                    MetricEvent(
                        timestamp = nowMus(),
                        tsReceived = System.currentTimeMillis(),
                        value = Random.nextDouble(0.0, 120.0),
                        dataset = datasets[dataset1]!!.id!!,
                        metric = MetricName("power::number"),
                        producer = Producer(officeLabClient!!.userId, officeLabClient!!.id!!),
                        source = "emonpi.test.emontx1.power2.${user}"
                    )
                }
                .toList()
                .flatMapCompletable { dataStore.ingest(it).ignoreElement() }
                .subscribeBy(onError = { logger.warn(it) { "Could not ingest demo data for $dataset1" } })
        }
        // Stop posting after POPULATE_DEMO_DATA_MINUTES minutes
        System.getenv("POPULATE_DEMO_DATA_MINUTES")?.toLong()
            .let { if (it != null) vertx.setTimer(it * 60 * 1000, { vertx.cancelTimer(timerId) }) };
    }

    private fun setupAQSim() {
        val sourceIds = (0..5).map { UUID.randomUUID().toString() }
        val timerId = vertx.setPeriodic(AQ_PERIOD_MS) {
            Flowable.fromArray("airquality.no2::number", "airquality.co2::number", "airquality.ppm25::number")
                .map { MetricName(it) }
                .map {
                    MetricEvent(
                        timestamp = nowMus(),
                        tsReceived = System.currentTimeMillis(),
                        value = Random.nextDouble(0.0, 500.0),
                        dataset = datasets[dataset2]!!.id!!,
                        metric = it,
                        producer = Producer(airqualityClient!!.userId, airqualityClient!!.id!!),
                        source = sourceIds.random()
                    )
                }
                .toList()
                .flatMapCompletable { dataStore.ingest(it).ignoreElement() }
                .subscribeBy(onError = { logger.warn(it) { "Could not ingest demo data for $dataset1" } })
        }
        // Stop posting after POPULATE_DEMO_DATA_MINUTES minutes
        System.getenv("POPULATE_DEMO_DATA_MINUTES")?.toLong()
            .let { if (it != null) vertx.setTimer(it * 60 * 1000, { vertx.cancelTimer(timerId) }) };
    }

    private fun nowMus(): Long {
        return TimeUnit.MICROSECONDS.convert(System.currentTimeMillis(), TimeUnit.MILLISECONDS)
    }
}
