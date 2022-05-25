package idlab.obelisk.services.pub.auth

import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import idlab.obelisk.definitions.catalog.*
import idlab.obelisk.definitions.catalog.codegen.ClientUpdate
import idlab.obelisk.definitions.framework.ENV_GOOGLE_IDP_CLIENT_ID
import idlab.obelisk.definitions.framework.ENV_GOOGLE_IDP_CLIENT_SECRET
import idlab.obelisk.definitions.framework.OblxConfig
import idlab.obelisk.plugins.accessmanager.basic.BasicAccessManager
import idlab.obelisk.plugins.accessmanager.basic.BasicAccessManagerModule
import idlab.obelisk.plugins.accessmanager.basic.store.AuthStore
import idlab.obelisk.plugins.accessmanager.basic.store.RedisAuthStore
import idlab.obelisk.plugins.accessmanager.basic.utils.SecureSecret
import idlab.obelisk.plugins.metastore.mongo.MongoDBMetaStoreModule
import idlab.obelisk.services.pub.auth.oauth.BasicOAuthProvider
import idlab.obelisk.utils.mongo.MongoCollections
import idlab.obelisk.utils.mongo.query.byExample
import idlab.obelisk.utils.service.OblxBaseModule
import idlab.obelisk.utils.service.OblxLauncher
import idlab.obelisk.utils.service.reactive.flatMap
import idlab.obelisk.utils.service.reactive.flatMapSingle
import idlab.obelisk.utils.service.utils.Base64.encodeAsBase64
import io.reactivex.Completable
import io.reactivex.Observable
import io.reactivex.Single
import io.reactivex.rxkotlin.subscribeBy
import io.vertx.core.http.HttpHeaders
import io.vertx.core.json.Json
import io.vertx.core.json.JsonObject
import io.vertx.core.json.jackson.DatabindCodec
import io.vertx.ext.web.client.WebClientOptions
import io.vertx.junit5.VertxExtension
import io.vertx.junit5.VertxTestContext
import io.vertx.reactivex.core.Vertx
import io.vertx.reactivex.ext.mongo.MongoClient
import io.vertx.reactivex.ext.web.client.WebClient
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import uk.org.webcompere.systemstubs.environment.EnvironmentVariables
import uk.org.webcompere.systemstubs.jupiter.SystemStubsExtension

@ExtendWith(VertxExtension::class)
@ExtendWith(SystemStubsExtension::class)
class AuthStoreTest {
    companion object {

        val SECRET = "abcdef"
        val CONF_SELF_NAME = "ConfidentialAsItself"
        val PUBLIC_SELF_NAME = "PublicAsItself"

        private lateinit var mongoClient: MongoClient
        private lateinit var identityStore: MetaStore
        private lateinit var authStore: AuthStore
        private lateinit var httpClient: WebClient
        private lateinit var testUserId: String
        private lateinit var testClientPrivateId: String
        private lateinit var testClientIds: Set<String>
        private lateinit var testClientPublicId: String
        private lateinit var basicOAuthProvider: BasicOAuthProvider
        private lateinit var bam: BasicAccessManager

        @JvmStatic
        @BeforeAll
        fun init(vertx: Vertx, context: VertxTestContext, env: EnvironmentVariables) {
            DatabindCodec.mapper().registerKotlinModule()

            env.set(ENV_GOOGLE_IDP_CLIENT_ID, "test-client-id")
            env.set(ENV_GOOGLE_IDP_CLIENT_SECRET, "test-client-secret")
            val config = OblxConfig()

            val launcher =
                OblxLauncher.with(
                    OblxBaseModule(),
                    MongoDBMetaStoreModule(),
                    AuthServiceModule(),
                    BasicAccessManagerModule()
                )

            mongoClient = MongoClient.create(
                vertx,
                JsonObject().put("connection_string", config.mongoConnectionUri).put("db_name", config.mongoDbName)
            )
            httpClient = WebClient.create(
                launcher.getInstance(Vertx::class.java),
                WebClientOptions().setDefaultHost("localhost").setDefaultPort(8080).setFollowRedirects(true)
            )
            identityStore = launcher.getInstance(MetaStore::class.java)
            basicOAuthProvider = launcher.getInstance(BasicOAuthProvider::class.java)
            bam = launcher.getInstance(BasicAccessManager::class.java)
            authStore = launcher.getInstance(RedisAuthStore::class.java)

            launcher.rxBootstrap(AuthService::class.java).flatMap {
                clean()
            }
                .flatMapSingle {
                    // Add test user
                    identityStore.createUser(
                        User(
                            email = "tester@oblx.io",
                            firstName = "Tester",
                            lastName = "McTesterson",
                        )
                    )
                        .doOnSuccess { testUserId = it }
                }
                .flatMap { userId ->
                    identityStore.createClient(
                        Client(
                            userId = userId,
                            confidential = true,
                            onBehalfOfUser = false,
                            name = CONF_SELF_NAME,
                            secretHash = SecureSecret.hash(SECRET)
                        )
                    )
                        .doOnSuccess { testClientPrivateId = it }
                        .flatMap {
                            identityStore.createClient(
                                Client(
                                    userId = userId,
                                    confidential = false,
                                    onBehalfOfUser = false,
                                    name = PUBLIC_SELF_NAME,
                                    redirectURIs = listOf("http://localhost:8804/receive")
                                )
                            )
                                .doOnSuccess { testClientPublicId = it }
                        }
                }
                .flatMapObservable { Observable.range(0, 20) }
                .flatMapSingle { idx ->
                    identityStore.createClient(
                        Client(
                            userId = testUserId,
                            confidential = true,
                            onBehalfOfUser = false,
                            name = "__TestClient $idx",

                            secretHash = SecureSecret.hash(SECRET)
                        )
                    )
                }
                .toList()
                .doOnSuccess { testClientIds = it.toSet() }
                .ignoreElement()
                .subscribeBy(onComplete = context::completeNow, onError = context::failNow)
        }

        @JvmStatic
        @AfterAll
        fun cleanUp(context: VertxTestContext) {
            println("CLEANING")
            clean().subscribeBy(onComplete = context::completeNow, onError = context::failNow)
        }

        private fun clean(): Completable {
            return mongoClient
                .rxRemoveDocuments(
                    MongoCollections.users,
                    byExample("lastName" to "McTesterson")
                )
                .flatMap {
                    mongoClient.rxRemoveDocuments(
                        MongoCollections.clients,
                        byExample("name" to CONF_SELF_NAME)
                    )
                }.flatMap {
                    mongoClient.rxRemoveDocuments(
                        MongoCollections.clients,
                        byExample("name" to PUBLIC_SELF_NAME)
                    )
                }.flatMap {
                    mongoClient.rxRemoveDocuments(
                        MongoCollections.clients,
                        JsonObject().put("name", JsonObject().put("\$regex", "^__TestClient"))
                    )
                }.flatMap {
                    mongoClient.rxRemoveDocuments(
                        MongoCollections.clients,
                        JsonObject().put("name", JsonObject().put("\$regex", "^s?changedit"))
                    )
                }.ignoreElement()
        }
    }

    @Test
    fun singleInvalidateTest(context: VertxTestContext) {
        clientLogin(testClientPrivateId)
            .flatMap {
                identityStore
                    .updateClient(testClientPrivateId, ClientUpdate(name = "changedit"))
                    .flatMapSingle { authStore.rxGetToken(it.getString("token")).toSingle() }
            }
            .flatMap { oldToken ->
                bam.invalidateToken(oldToken).andThen(authStore.rxGetToken(oldToken.opaqueToken).toSingle())
                    .map { Pair(oldToken, it) }
            }
            .subscribeBy(
                onSuccess = { tokens ->
                    val old = tokens.first
                    val nw = tokens.second
                    context.verify {
                        Assertions.assertAll(
                            { Assertions.assertNotEquals(old.client?.name, nw.client?.name) },
                            { Assertions.assertEquals("changedit", nw.client?.name) }
                        )
                    }.completeNow()
                },
                onError = context::failNow
            )
    }

    @Test
    fun batchInvalidateTest(context: VertxTestContext) {
        Observable.fromIterable(testClientIds)
            .flatMapSingle { id ->
                clientLogin(id).map { Pair(id, it) }
            }
            .flatMapSingle {
                identityStore
                    .updateClient(it.first, ClientUpdate(name = "changedit ${it.first}"))
                    .flatMapSingle { authStore.rxGetToken(it.second.getString("token")).toSingle() }
            }
            .toList()
            .flatMap { oldTokens ->
                val subs = testClientIds.map { "c$it" }.toSet()
                bam.invalidateSessions(clientIds = testClientIds)
                    .andThen(authStore.rxGetTokensBySubs(subs))
                    .map { Pair(oldTokens, it) }
            }
            .subscribeBy(
                onSuccess = { tokens ->
                    val olds = tokens.first
                    val nws = tokens.second
                    context.verify {
                        Assertions.assertEquals(olds.size, nws.size)
                        nws.forEachIndexed { idx, nw ->
                            val old = olds[idx]
                            Assertions.assertNotEquals(old.client?.name, nw.client?.name)
                            Assertions.assertEquals("changedit ${nw.client?.id}", nw.client?.name)
                        }
                    }.completeNow()
                },
                onError = context::failNow
            )
    }

    private fun clientLogin(id: String): Single<JsonObject> {
        return identityStore.getClient(id)
            .flatMapSingle {
                val body = JsonObject()
                    .put("grant_type", "client_credentials")
                httpClient.post("/auth/token")
                    .putHeader(
                        HttpHeaders.AUTHORIZATION.toString(),
                        "Basic " + "${it.id}:$SECRET".encodeAsBase64()
                    )
                    .rxSendJsonObject(body)
            }.map { it.bodyAsJsonObject() }
    }
}
