package idlab.obelisk.services.pub.auth

import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import idlab.obelisk.definitions.catalog.Client
import idlab.obelisk.definitions.catalog.MetaStore
import idlab.obelisk.definitions.catalog.User
import idlab.obelisk.definitions.framework.ENV_GOOGLE_IDP_CLIENT_ID
import idlab.obelisk.definitions.framework.ENV_GOOGLE_IDP_CLIENT_SECRET
import idlab.obelisk.definitions.framework.OblxConfig
import idlab.obelisk.plugins.accessmanager.basic.BasicAccessManagerModule
import idlab.obelisk.plugins.accessmanager.basic.utils.CodeChallenge
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
import io.reactivex.Single
import io.reactivex.rxkotlin.subscribeBy
import io.vertx.core.http.HttpHeaders
import io.vertx.core.json.JsonObject
import io.vertx.core.json.jackson.DatabindCodec
import io.vertx.ext.web.client.WebClientOptions
import io.vertx.junit5.VertxExtension
import io.vertx.junit5.VertxTestContext
import io.vertx.reactivex.core.Vertx
import io.vertx.reactivex.ext.mongo.MongoClient
import io.vertx.reactivex.ext.web.Router
import io.vertx.reactivex.ext.web.client.WebClient
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.function.Executable
import uk.org.webcompere.systemstubs.environment.EnvironmentVariables
import uk.org.webcompere.systemstubs.jupiter.SystemStubsExtension
import java.util.*
import kotlin.random.Random

@ExtendWith(VertxExtension::class)
@ExtendWith(SystemStubsExtension::class)
class ClientAuthTest {

    companion object {

        val SECRET = "abcdef"

        private lateinit var mongoClient: MongoClient
        private lateinit var identityStore: MetaStore
        private lateinit var httpClient: WebClient
        private lateinit var testUserId: String
        private lateinit var testClientPrivateId: String
        private lateinit var testClientPublicId: String
        private lateinit var basicOAuthProvider: BasicOAuthProvider

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

            launcher.rxBootstrap(AuthService::class.java).flatMap {
                ClientAuthTest.clean()
            }
                .flatMapSingle {
                    // Add test user
                    identityStore.createUser(
                        User(
                            email = "tester@oblx.io",
                            firstName = "Tester",
                            lastName = "McTesterson"
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
                            name = "ConfidentialAsItself Client",
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
                                    name = "PublicAsItself",
                                    redirectURIs = listOf("http://localhost:8804/receive")
                                )
                            )
                                .doOnSuccess { testClientPublicId = it }
                        }
                }
                .ignoreElement()
                .subscribeBy(onComplete = context::completeNow, onError = context::failNow)
        }

        @JvmStatic
        @AfterAll
        fun cleanUp(context: VertxTestContext) {
            clean().subscribeBy(onComplete = context::completeNow, onError = context::failNow)
        }

        private fun clean(): Completable {
            return ClientAuthTest.mongoClient
                .rxRemoveDocuments(
                    MongoCollections.users,
                    byExample("lastName" to "McTesterson")
                ).ignoreElement()
                .flatMap {
                    ClientAuthTest.mongoClient.rxRemoveDocuments(
                        MongoCollections.clients,
                        byExample("name" to "ConfidentialAsItself Client")
                    ).ignoreElement()
                }.flatMap {
                    ClientAuthTest.mongoClient.rxRemoveDocuments(
                        MongoCollections.clients,
                        byExample("name" to "PublicAsItself Client")
                    ).ignoreElement()
                }
        }
    }

    /**
     * Confidential client application as itself.
     *
     * * Uses client_secret and sends it in the body with client_id
     * * Directly hits /auth/token
     */
    @Test
    fun confClientUsingBody(context: VertxTestContext) {
        identityStore.getClient(testClientPrivateId)
            .flatMapSingle {
                val body = JsonObject()
                    .put("grant_type", "client_credentials")
                    .put("client_id", it.id)
                    .put("client_secret", SECRET)
                httpClient.post("/auth/token").rxSendJsonObject(body)
            }
            .subscribeBy(
                onSuccess = { res ->
                    context.verify {
                        assertIsValidTokenResponse(res.bodyAsJsonObject())
                    }.completeNow()
                },
                onError = context::failNow
            )
    }

    /**
     * Confidential client application as itself.
     *
     * * Uses client_secret and sends it in the header as Basic auth with client_id
     * * Directly hits /auth/token
     */
    @Test
    fun confClientUsingHeader(context: VertxTestContext) {
        identityStore.getClient(testClientPrivateId)
            .flatMapSingle {
                val body = JsonObject()
                    .put("grant_type", "client_credentials")
                httpClient.post("/auth/token")
                    .putHeader(HttpHeaders.AUTHORIZATION.toString(), "Basic " + "${it.id}:${SECRET}".encodeAsBase64())
                    .rxSendJsonObject(body)
            }
            .subscribeBy(
                onSuccess = { res ->
                    context.verify {
                        assertIsValidTokenResponse(res.bodyAsJsonObject())
                    }.completeNow()
                },
                onError = context::failNow
            )
    }

    /**
     * Confidential client application as itself.
     *
     * * Uses client_secret and sends it in the header as Basic auth with client_id
     * * Directly hits /auth/token
     *
     * BUT DO IT BY FORGETTING THE BASIC PREFIX
     */
    @Test
    fun confClientUsingHeaderWrong(context: VertxTestContext) {
        identityStore.getClient(testClientPrivateId)
            .flatMapSingle {
                val body = JsonObject()
                    .put("grant_type", "client_credentials")
                httpClient.post("/auth/token")
                    .putHeader(HttpHeaders.AUTHORIZATION.toString(), "${it.id}:${SECRET}".encodeAsBase64())
                    .rxSendJsonObject(body)
            }
            .subscribeBy(
                onSuccess = { res ->
                    context.verify {
                        Assertions.assertEquals(400, res.statusCode())
                    }.completeNow()
                },
                onError = context::failNow
            )
    }

    /**
     * Test Base64url Encoding without Padding.
     * According to [RFC7636#Appendix-A](https://tools.ietf.org/html/rfc7636#appendix-A)
     */
    @Test
    fun rfc7636AppendixA(context: VertxTestContext) {
        val result = "A-z_4ME"
        val oct: ByteArray = byteArrayOf(3.toByte(), 236.toByte(), 255.toByte(), 224.toByte(), 193.toByte());
        val str = CodeChallenge.base64urlEncode(oct)
        context.verify {
            Assertions.assertEquals(result, str)
        }
        val res = Base64.getUrlDecoder().decode(str)
        context.verify {
            Assertions.assertArrayEquals(oct, res)
        }.completeNow()
    }

    /**
     * Test Base64url Encoding without Padding.
     * According to [RFC7636#Appendix-B](https://tools.ietf.org/html/rfc7636#appendix-B)
     */
    @Test
    fun rfc7636AppendixB(context: VertxTestContext) {
        fun toBytes(vararg ints: Int) = ByteArray(ints.size) { pos -> ints[pos].toByte() }
        val resultOct: ByteArray = toBytes(
            19, 211, 30, 150, 26, 26, 216, 236, 47, 22, 177, 12, 76, 152, 46,
            8, 118, 168, 120, 173, 109, 241, 68, 86, 110, 225, 137, 74, 203,
            112, 249, 195
        )
        val resultVerifier = "dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk"
        val oct: ByteArray = toBytes(
            116, 24, 223, 180, 151, 153, 224, 37, 79, 250, 96, 125, 216, 173,
            187, 186, 22, 212, 37, 77, 105, 214, 191, 240, 91, 88, 5, 88, 83,
            132, 141, 121
        )
        val verifier = createCodeVerifier(oct)
        context.verify {
            Assertions.assertEquals(resultVerifier, verifier)
        }
        val hash = CodeChallenge.S256(verifier)
        context.verify {
            Assertions.assertArrayEquals(resultOct, hash)
        }.completeNow()
    }

    /**
     * Public client application as itself.
     *
     * * Uses code_challenge
     * * Hits /auth/auth first
     * * Hits /auth/token afterwards
     */
    @Test
    fun publicClient(context: VertxTestContext, vertx: Vertx) {
        val codeVerifier = createCodeVerifier(null);
        println(codeVerifier)
        // Setup HTTP server to receive the notifications
        val router = Router.router(vertx)
        router.get("/receive").handler { ctx ->
            println("Received: ${ctx.request().query()}")
            context.verify {
                Assertions.assertAll(
                    { Assertions.assertTrue(ctx.queryParams().contains("code")) },
                    { Assertions.assertEquals("nothing", ctx.queryParams().get("state")) }
                )
                // Now exchange for token
                val body = JsonObject()
                    .put("code", ctx.queryParams().get("code"))
                    .put("grant_type", "authorization_code")
                    .put("client_id", testClientPublicId)
                    .put("code_verifier", codeVerifier)
                httpClient.post("/auth/token").rxSendJsonObject(body).subscribeBy(
                    onSuccess = { res ->
                        context.verify {
                            println(res.bodyAsJsonObject().encodePrettily())
                            assertIsValidTokenResponse(res.bodyAsJsonObject())
                        }.completeNow()
                    },
                    onError = context::failNow
                )
            }
            ctx.end();
        }
        vertx.createHttpServer().requestHandler(router).rxListen(8804)
            .flatMapMaybe { identityStore.getClient(testClientPublicId) }
            .flatMapSingle {
                val body = JsonObject()
                    .put("response_type", "code")
                    .put("scope", "client")
                    .put("client_id", it.id)
                    .put("state", "nothing")
                    .put("redirect_uri", "http://localhost:8804/receive")
                    .put("code_challenge", CodeChallenge(codeVerifier, CodeChallenge.Algorithm.S256).toString())
                    .put("code_challenge_method", CodeChallenge.Algorithm.S256.text)

                httpClient.get("/auth/auth")
                    .addQueryParam("response_type", "code")
                    .addQueryParam("scope", "client")
                    .addQueryParam("client_id", it.id)
                    .addQueryParam("state", "nothing")
                    .addQueryParam("redirect_uri", "http://localhost:8804/receive")
                    .addQueryParam(
                        "code_challenge",
                        CodeChallenge(codeVerifier, CodeChallenge.Algorithm.S256).toString()
                    )
                    .addQueryParam("code_challenge_method", CodeChallenge.Algorithm.S256.text)
                    .rxSend()
            }
            .flatMap {
                context.verify {
                    Assertions.assertTrue(setOf(200, 302).contains(it.statusCode()))
                }
                if (it.statusCode() == 302) {
                    httpClient.getAbs(it.getHeader("Location")).rxSend()
                } else if (it.statusCode() != 200) {
                    Single.error(RuntimeException("Error ${it.statusCode()}: ${it.statusMessage()}"))
                } else {
                    Single.just(it)
                }
            }
            .subscribeBy(
                onSuccess = {
                    context.verify {
                        Assertions.assertEquals(200, it.statusCode())
                    }
                },
                onError = context::failNow
            )

    }

    private fun createCodeVerifier(input: ByteArray?): String {
        var oct32 = input ?: Random.nextBytes(32)
        var str = Base64.getUrlEncoder().withoutPadding().encodeToString(oct32)
        return str;
    }

    private fun assertIsValidTokenResponse(actual: JsonObject) {
        Assertions.assertAll(
            Executable { Assertions.assertTrue(actual.containsKey("token")) },
            Executable { Assertions.assertTrue(actual.containsKey("id_token")) },
            Executable { Assertions.assertTrue(actual.containsKey("max_valid_time")) },
            Executable { Assertions.assertTrue(actual.containsKey("max_idle_time")) }
        )
    }
}

