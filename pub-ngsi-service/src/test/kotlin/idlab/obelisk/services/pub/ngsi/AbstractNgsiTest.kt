package idlab.obelisk.services.pub.ngsi

import idlab.obelisk.client.OblxClient
import idlab.obelisk.client.OblxClientOptions
import idlab.obelisk.definitions.AlreadyExistsException
import idlab.obelisk.definitions.DataRange
import idlab.obelisk.definitions.MetricName
import idlab.obelisk.definitions.catalog.Client
import idlab.obelisk.definitions.catalog.Dataset
import idlab.obelisk.definitions.catalog.MetaStore
import idlab.obelisk.definitions.catalog.Permission
import idlab.obelisk.definitions.data.DataStore
import idlab.obelisk.definitions.data.EventsQuery
import idlab.obelisk.definitions.framework.ENV_GOOGLE_IDP_CLIENT_ID
import idlab.obelisk.definitions.framework.ENV_GOOGLE_IDP_CLIENT_SECRET
import idlab.obelisk.definitions.ratelimiting.RateLimiter
import idlab.obelisk.plugins.accessmanager.basic.BasicAccessManagerModule
import idlab.obelisk.plugins.accessmanager.basic.utils.SecureSecret
import idlab.obelisk.plugins.datastore.clickhouse.ClickhouseDataStoreModule
import idlab.obelisk.plugins.messagebroker.pulsar.PulsarMessageBrokerModule
import idlab.obelisk.plugins.metastore.mongo.MongoDBMetaStoreModule
import idlab.obelisk.plugins.ratelimiter.gubernator.GubernatorRateLimiterModule
import idlab.obelisk.service.internal.sink.SinkService
import idlab.obelisk.services.pub.auth.AuthService
import idlab.obelisk.services.pub.auth.AuthServiceModule
import idlab.obelisk.utils.service.OblxBaseModule
import idlab.obelisk.utils.service.OblxLauncher
import idlab.obelisk.utils.service.reactive.flatMap
import io.reactivex.Completable
import io.reactivex.Flowable
import io.vertx.core.json.JsonObject
import io.vertx.reactivex.core.Vertx
import io.vertx.reactivex.core.buffer.Buffer
import io.vertx.reactivex.ext.web.client.HttpResponse
import mu.KotlinLogging
import org.junit.jupiter.api.extension.ExtendWith
import uk.org.webcompere.systemstubs.environment.EnvironmentVariables
import uk.org.webcompere.systemstubs.jupiter.SystemStub
import uk.org.webcompere.systemstubs.jupiter.SystemStubsExtension
import java.time.format.DateTimeFormatter


@ExtendWith(SystemStubsExtension::class)
abstract class AbstractNgsiTest {

    companion object {

        @SystemStub
        protected val environmentVariables = EnvironmentVariables()
            .set(ENV_GOOGLE_IDP_CLIENT_ID, "test-client-id")
            .set(ENV_GOOGLE_IDP_CLIENT_SECRET, "test-client-secret")

        @JvmStatic
        protected val launcher = OblxLauncher.with(
            OblxBaseModule(),
            PulsarMessageBrokerModule(initLocalPulsar = true),
            BasicAccessManagerModule(),
            MongoDBMetaStoreModule(),
            AuthServiceModule(),
            ClickhouseDataStoreModule(),
            GubernatorRateLimiterModule(),
            NgsiModule()
        )

        @JvmStatic
        protected lateinit var oblxClient: OblxClient

        @JvmStatic
        protected lateinit var datasetId: String

        @JvmStatic
        protected lateinit var clientId: String

        @JvmStatic
        protected val clientSecret: String = "blargh"

        @JvmStatic
        protected lateinit var dataStore: DataStore

        @JvmStatic
        protected lateinit var metaStore: MetaStore

        @JvmStatic
        protected lateinit var basePath: String

        @JvmStatic
        protected lateinit var rateLimiter: RateLimiter

        @JvmStatic
        protected lateinit var deleteAllTestData: EventsQuery

        @JvmStatic
        protected val dateTimeFormatter = DateTimeFormatter.ISO_DATE_TIME

        @JvmStatic
        protected val logger = KotlinLogging.logger { }

        @JvmStatic
        protected fun setup(datasetId: String, clientId: String): Completable {
            this.datasetId = datasetId
            this.clientId = clientId
            val vertx = launcher.getInstance(Vertx::class.java)
            oblxClient = OblxClient.create(
                vertx,
                OblxClientOptions(
                    apiUrl = "http://localhost:8080/",
                    clientId = clientId,
                    secret = clientSecret,
                )
            )
            dataStore = launcher.getInstance(DataStore::class.java)
            metaStore = launcher.getInstance(MetaStore::class.java)
            rateLimiter = launcher.getInstance(RateLimiter::class.java)
            basePath = "/ext/ngsi/${datasetId}/ngsi-ld/v1"
            deleteAllTestData = EventsQuery(
                dataRange = DataRange(
                    listOf(datasetId),
                    MetricName.wildcard()
                )
            )

            return launcher.rxBootstrap(
                NgsiLDService::class.java,
                SinkService::class.java,
                AuthService::class.java,
                NgsiLDSubscriptionMatcher::class.java,
                NgsiLDNotifier::class.java
            )
                .flatMap {
                    // delete previous data
                    dataStore.delete(deleteAllTestData)
                }
                .flatMap {
                    metaStore.createDataset(
                        Dataset(
                            id = datasetId,
                            name = datasetId
                        )
                    )
                        .ignoreElement()
                        .onErrorComplete { it is AlreadyExistsException }
                        .flatMap {
                            metaStore.createClient(
                                Client(
                                    id = clientId,
                                    name = clientId,
                                    userId = "0",
                                    scope = setOf(Permission.READ, Permission.WRITE),
                                    confidential = true,
                                    onBehalfOfUser = false,
                                    secretHash = SecureSecret.hash(clientSecret),
                                )
                            )
                                .ignoreElement()
                                .onErrorComplete { it is AlreadyExistsException }
                        }
                }
        }

        @JvmStatic
        protected fun teardown(): Completable {
            return dataStore.delete(deleteAllTestData)
        }

    }

    protected fun pageEntities(requestUri: String): Flowable<JsonObject> {
        println("Paging request GET $requestUri")
        return oblxClient.customHttpGet(requestUri)
            .flatMapPublisher { resp ->
                if (resp.statusCode() in (200..399)) {
                    val nextLink = getNextLink(resp)
                    val resultAsFlowable: Flowable<JsonObject> =
                        Flowable.fromIterable(resp.bodyAsJsonArray().map { JsonObject.mapFrom(it) })
                    if (nextLink != null) {
                        resultAsFlowable.concatWith(pageEntities(nextLink))
                    } else {
                        resultAsFlowable
                    }
                } else {
                    Flowable.error(RuntimeException("Error while retrieving entities: ${resp.bodyAsString()}"))
                }
            }
    }

    protected fun getNextLink(resp: HttpResponse<Buffer>): String? {
        val link = resp.getHeader("Link")
        return link?.split(',')?.find { it.contains("rel=next") }?.substringAfter('<')?.substringBefore('>')
    }
}
