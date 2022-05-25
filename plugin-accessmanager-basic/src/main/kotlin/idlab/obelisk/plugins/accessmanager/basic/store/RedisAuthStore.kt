package idlab.obelisk.plugins.accessmanager.basic.store

import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import hu.akarnokd.rxjava3.bridge.RxJavaBridge
import idlab.obelisk.definitions.catalog.Token
import idlab.obelisk.definitions.control.ControlKeys
import idlab.obelisk.definitions.framework.OblxConfig
import idlab.obelisk.plugins.accessmanager.basic.store.model.LoginState
import idlab.obelisk.plugins.accessmanager.basic.store.model.Session
import idlab.obelisk.utils.service.utils.TTL
import io.reactivex.Completable
import io.reactivex.Maybe
import io.reactivex.Single
import io.vertx.core.json.Json
import io.vertx.micrometer.backends.BackendRegistries
import mu.KotlinLogging
import org.redisson.api.RMapCache
import org.redisson.api.RMapCacheRx
import org.redisson.api.RedissonClient
import org.redisson.api.map.event.EntryCreatedListener
import org.redisson.api.map.event.EntryExpiredListener
import org.redisson.api.map.event.EntryRemovedListener
import org.redisson.api.map.event.MapEntryListener
import org.redisson.codec.JsonJacksonCodec
import java.util.*
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject

class RedisAuthStore @Inject constructor(
    val redis: RedissonClient,
    val config: OblxConfig
) : AuthStore {
    private val codec: JsonJacksonCodec
    private val logger = KotlinLogging.logger { }

    private val tokenTTL: TTL
    private val tokenIdleTTL: TTL
    private val loginTTL: TTL
    private val sessionTTL: TTL
    private val sessionIdleTTL: TTL

    private val GAUGE_TOKENS = "oblx.auth.active.tokens";
    private val GAUGE_SESSSIONS = "oblx.auth.active.sessions";
    private val GAUGE_LOGINS = "oblx.auth.active.logins";

    init {
        // Setup codec
        codec = JsonJacksonCodec.INSTANCE
        codec.objectMapper.registerKotlinModule()

        // Init TTLs
        tokenTTL = TTL(config.getString(TTL.TOKEN_TTL_PROP, TTL.TOKEN_TTL))
        tokenIdleTTL = TTL(config.getString(TTL.TOKEN_IDLE_TTL_PROP, TTL.TOKEN_IDLE_TTL))
        loginTTL = TTL(config.getString(TTL.LOGIN_TTL_PROP, TTL.LOGIN_TTL))
        sessionTTL = TTL(config.getString(TTL.SESSION_TTL_PROP, TTL.SESSION_TTL))
        sessionIdleTTL = TTL(config.getString(TTL.SESSION_IDLE_TTL_PROP, TTL.SESSION_IDLE_TTL))
    }

    override fun rxGetToken(id: String?): Maybe<Token> {
        return if (id == null) Maybe.empty() else redis.tokensRx()[id].map { Json.decodeValue(it, Token::class.java) }
            .to(RxJavaBridge.toV2Maybe())
    }

    override fun rxGetAccessTokensBySubs(subs: Set<String>?): Single<Map<String, String?>> {
        return if (subs.isNullOrEmpty()) {
            Single.just(emptyMap())
        } else {
            return rxGetSessions(subs).map { sessions ->
                sessions.associateBy({ it.sub }, { it.access_token })
            }

        }
    }

    override fun rxGetTokensBySubs(subs: Set<String>?): Single<Set<Token>> {
        return if (subs.isNullOrEmpty()) {
            Single.just(emptySet())
        } else {
            rxGetSessions(subs)
                .map { sessions ->
                    sessions
                        .filter { !it.access_token.isNullOrEmpty() }
                        .map { it.access_token!! }.toSet()
                }
                .flatMap { rxGetTokens(it) }

        }
    }

    private fun rxGetTokens(opaqueTokens: Set<String>?): Single<Set<Token>> {
        return if (opaqueTokens.isNullOrEmpty()) {
            Single.just(emptySet())
        } else {
            return redis.tokensRx()
                .getAll(opaqueTokens)
                .to(RxJavaBridge.toV2Single())
                .map { it.values.map { json -> Json.decodeValue(json, Token::class.java) }.toSet() }
        }
    }

    override fun rxSaveToken(token: Token): Single<Boolean> {
        return redis.tokensRx().fastPut(
            token.opaqueToken,
            Json.encode(token),
            tokenTTL.value,
            tokenTTL.unit,
            tokenIdleTTL.value,
            tokenIdleTTL.unit
        ).to(RxJavaBridge.toV2Single())
    }

    override fun rxRemoveToken(opaqueToken: String?): Single<Boolean> {
        return redis.tokensRx().fastRemove(opaqueToken).map { it > 0 }.to(RxJavaBridge.toV2Single());
    }

    override fun rxGetLogin(id: String?): Maybe<LoginState> {
        return redis.loginsRx()[id].to(RxJavaBridge.toV2Maybe())
    }

    override fun rxSaveLogin(key: String?, login: LoginState): Single<Boolean> {
        return redis.loginsRx().fastPut(key, login, loginTTL.value, loginTTL.unit).to(RxJavaBridge.toV2Single())
    }

    override fun rxRemoveLogin(key: String?): Single<Boolean> {
        return redis.loginsRx().fastRemove(key).map { it > 0 }.to(RxJavaBridge.toV2Single())
    }

    override fun rxReplaceLogin(key: String?, oldLogin: LoginState, newLogin: LoginState): Single<Boolean> {
        return rxRemoveLogin(key)
            .flatMap { removeWorked ->
                if (removeWorked) {
                    rxSaveLogin(key, newLogin)
                } else {
                    Single.just(false)
                }
            }
    }

    override fun rxGetSessionBySid(sid: String?): Maybe<Session> {
        if (sid == null) {
            return Maybe.empty();
        }
        return redis.sessionMapRx().get(sid)
            .to(RxJavaBridge.toV2Maybe())
            .flatMap { rxGetSession(it) }
            .switchIfEmpty(redis.sessionMapRx().remove(sid).to(RxJavaBridge.toV2Maybe()).flatMap { Maybe.empty() })
    }

    override fun rxSaveSession(session: Session): Single<Boolean> {
        logger.trace(
            "[T {}] Storing session with ref to access_token {} for sub {}",
            Thread.currentThread().name, session.access_token, session.sub
        )
        return redis.sessionsRx().fastPut(
            session.sub,
            session,
            sessionTTL.value,
            sessionTTL.unit,
            sessionIdleTTL.value,
            sessionIdleTTL.unit,
        )
            .to(RxJavaBridge.toV2Single())
    }

    override fun rxGetNewSessionSid(session: Session): Single<String> {
        val sid = generateRandomString()
        return redis.sessionMapRx().fastPut(
            sid,
            session.sub,
            sessionTTL.value,
            sessionTTL.unit,
            sessionIdleTTL.value,
            sessionIdleTTL.unit,
        ).map { sid }
            .to(RxJavaBridge.toV2Single())

    }

    override fun rxGetSession(sub: String?): Maybe<Session> {
        if (sub == null) {
            return Maybe.empty()
        }
        return redis.sessionsRx().get(sub)
            .to(RxJavaBridge.toV2Maybe())
    }

    override fun rxRemoveSessionBySub(sub: String?): Single<Boolean> {
        return redis.sessionsRx().fastRemove(sub).map { it > 0 }.to(RxJavaBridge.toV2Single())
    }

    /**
     * Returns all found sessions for a list of subs
     */
    private fun rxGetSessions(subs: Set<String>?): Single<Set<Session>> {
        if (subs == null) {
            return Single.just(emptySet())
        } else {
            return redis.sessionsRx().getAll(subs).map { it.values.toSet() }
                .to(RxJavaBridge.toV2Single())
        }
    }

    /**
     * Initialise instrumentation for this Redis AuthStore implementation
     */
    fun initInstrumentation() {
        try {

            val microMeterRegistry = BackendRegistries.getDefaultNow()

            // Number of active login procedures. (Logins are generated in the OAuth handshake with short expiration)
            val authActiveLogins =
                microMeterRegistry.gauge(GAUGE_LOGINS, AtomicInteger(syncCountLogins()))!!

            // Number of active auth tokens. (Token is used to do API calls)
            val authActiveTokens =
                microMeterRegistry.gauge(GAUGE_TOKENS, AtomicInteger(syncCountTokens()))!!

            // Number of active auth sessions. (Session is stored for easy auth continuation)
            val authActiveSessions =
                microMeterRegistry.gauge(GAUGE_SESSSIONS, AtomicInteger(syncCountSessions()))!!

            addLoginsListener(EntryCreatedListener<String, String> { authActiveLogins.incrementAndGet() })
            addLoginsListener(EntryExpiredListener<String, String> { authActiveLogins.decrementAndGet() })
            addLoginsListener(EntryRemovedListener<String, String> { authActiveLogins.decrementAndGet() })
            addSessionsListener(EntryCreatedListener<String, Session> { authActiveSessions.incrementAndGet() })
            addSessionsListener(EntryExpiredListener<String, Session> { authActiveSessions.decrementAndGet() })
            addSessionsListener(EntryRemovedListener<String, Session> { authActiveSessions.decrementAndGet() })
            addTokensListener(EntryCreatedListener<String, Token> { authActiveTokens.incrementAndGet() })
            addTokensListener(EntryExpiredListener<String, Token> { authActiveTokens.decrementAndGet() })
            addTokensListener(EntryRemovedListener<String, Token> { authActiveTokens.decrementAndGet() })

        } catch (ex: Exception) {
            throw RuntimeException("Error while registering instrumentation for AuthStore (when starting AuthService)");
        }
    }

    override fun rxReplaceToken(oldToken: Token, newToken: Token): Single<Boolean> {
        return if (oldToken.opaqueToken != newToken.opaqueToken) {
            Single.error(IllegalArgumentException("Both newToken and oldToken need to have the same opaqueToken since it is used as the redis key"))
        } else {
            redis.tokensRx().fastRemove(oldToken.opaqueToken).to(RxJavaBridge.toV2Single())
                .flatMap { removedKeys ->
                    if (removedKeys > 0) {
                        rxSaveToken(newToken)
                    } else {
                        Single.just(false);
                    }
                }
        }
    }

    override fun rxBatchPut(newTokenEntries: MutableMap<String, Token>): Completable {
        return redis.tokensRx().putAll(newTokenEntries.mapValues { Json.encode(it.value) })
            .to(RxJavaBridge.toV2Completable())
    }

    private fun addTokensListener(listener: MapEntryListener): Int {
        return redis.tokensSync().addListener(listener)
    }

    private fun addLoginsListener(listener: MapEntryListener): Int {
        return redis.loginsSync().addListener(listener)
    }

    private fun addSessionsListener(listener: MapEntryListener): Int {
        return redis.sessionsSync().addListener(listener)
    }

    private fun syncCountTokens(): Int {
        return this.redis.tokensSync().count();
    }

    private fun syncCountSessions(): Int {
        return this.redis.sessionsSync().count();
    }

    private fun syncCountLogins(): Int {
        return this.redis.loginsSync().count();
    }

    private fun printTokens() {
        var result = ">>>>> TOKENS LIST <<<<<<<\n"
        var i = 1;
        redis.tokensRx().entryIterator().toList().subscribe { entries ->
            for (e in entries) {
                val key = e.key
                val tok = Json.decodeValue(e.value, Token::class.java);
                val okey = tok.opaqueToken
                val user = tok.user.email
                val client = tok.client?.name ?: '-'
                val nr = i.toString().padEnd(2)
                result += ("$nr. $key -> [$okey, $user, $client]\n")
                i++
            }
            logger.trace(result)
        }
    }

    private fun printSessions() {
        var result = ">>>>> SESSION LIST <<<<<<<\n"
        var i = 1;
        redis.sessionsRx().entryIterator().toList().subscribe { entries ->
            for (e in entries) {
                val key = e.key
                val session = e.value
                val tok = session.access_token
                val sub = session.idToken!!["sub"] as String
                val nr = i.toString().padEnd(2)
                result += ("$nr. $key -> [sub: $sub, access_token: $tok]\n")
                i++
            }
            logger.trace(result)
        }
    }

    /**
     * Generates a random string
     */
    private fun generateRandomString(): String {
        val leftLimit = 48 // numeral '0'
        val rightLimit = 122 // letter 'z'
        val targetStringLength = 16
        val random = Random()
        return random.ints(leftLimit, rightLimit + 1)
            .filter { (it <= 57 || it >= 65) && (it <= 90 || it >= 97) }
            .limit(targetStringLength.toLong())
            .collect(
                { StringBuilder() },
                { obj, codePoint -> obj.appendCodePoint(codePoint) }) { obj, s -> obj.append(s) }
            .toString()
    }

    /** Redis collections **/

    private fun RedissonClient.tokensRx(): RMapCacheRx<String, String> {
        return this.rxJava().getMapCache(ControlKeys.REDIS_TOKENS);
    }

    private fun RedissonClient.sessionMapRx(): RMapCacheRx<String, String> {
        return this.rxJava().getMapCache(ControlKeys.REDIS_SID_SUB)
    }

    private fun RedissonClient.sessionsRx(): RMapCacheRx<String, Session> {
        return this.rxJava().getMapCache(ControlKeys.REDIS_SESSIONS, codec)
    }

    private fun RedissonClient.loginsRx(): RMapCacheRx<String, LoginState> {
        return this.rxJava().getMapCache(ControlKeys.REDIS_LOGINS, codec)
    }

    private fun RedissonClient.tokensSync(): RMapCache<String, String> {
        return this.getMapCache(ControlKeys.REDIS_TOKENS);
    }

    private fun RedissonClient.sessionMapSync(): RMapCache<String, String> {
        return this.getMapCache(ControlKeys.REDIS_SID_SUB)
    }

    private fun RedissonClient.sessionsSync(): RMapCache<String, Session> {
        return this.getMapCache(ControlKeys.REDIS_SESSIONS, codec)
    }

    private fun RedissonClient.loginsSync(): RMapCache<String, LoginState> {
        return this.getMapCache(ControlKeys.REDIS_LOGINS, codec)
    }


}