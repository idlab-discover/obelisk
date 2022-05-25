package idlab.obelisk.services.pub.auth.keys

import idlab.obelisk.utils.mongo.MongoCollections
import idlab.obelisk.utils.mongo.rxDeleteById
import idlab.obelisk.utils.mongo.rxSave
import idlab.obelisk.utils.service.reactive.flatMapSingle
import idlab.obelisk.utils.service.reactive.toSingleNullSafe
import io.reactivex.Completable
import io.reactivex.Single
import io.vertx.core.json.JsonObject
import io.vertx.reactivex.ext.mongo.MongoClient
import mu.KotlinLogging
import org.jose4j.jwk.*
import org.jose4j.jws.AlgorithmIdentifiers
import java.util.*
import kotlin.streams.toList

class MongoDBKeyStore(private val client: MongoClient) : KeyStore {
    private val logger = KotlinLogging.logger { }
    private lateinit var cachedJwks: JsonWebKeySet
    private lateinit var youngestKey: PublicJsonWebKey

    fun initialize(): Unit {
        // Make sure there are at least 2 keys
        return this.countKeys()
            .flatMapCompletable {
                if (it.toInt() < 2) {
                    val keys = mutableListOf<PublicJsonWebKey>()
                    for (x in it.toInt() until 2) {
                        keys.add(generateKey())
                    }
                    storeKeys(*keys.toTypedArray()).ignoreElement()
                } else {
                    Completable.complete()
                }
            }
            .flatMapSingle { listKeys() }
            .doOnSuccess { updateCache(it) }
            .ignoreElement()
            .blockingAwait()
    }

    override fun storeKey(key: PublicJsonWebKey): Single<String> {
        val jwk = key.toJson(JsonWebKey.OutputControlLevel.INCLUDE_PRIVATE);
        val record = KeyRecord(null, jwk, System.currentTimeMillis())
        return client.rxSave(MongoCollections.keys, record).toSingleNullSafe(record.id)
    }

    override fun storeKeys(vararg keys: PublicJsonWebKey): Single<List<String>> {
        val list = mutableListOf<Single<String>>();
        for (key in keys) {
            val jwk = key.toJson(JsonWebKey.OutputControlLevel.INCLUDE_PRIVATE);
            val record = KeyRecord(null, jwk, System.currentTimeMillis())
            list.add(client.rxSave(MongoCollections.keys, record).toSingleNullSafe(record.id))
        }
        return Single.concat(list).toList()
    }

    override fun countKeys(): Single<Long> {
        return client.rxCount(MongoCollections.keys, JsonObject())
    }

    override fun listKeys(): Single<List<KeyRecord>> {
        return client.rxFind(MongoCollections.keys, JsonObject()).map {
            it.stream()
                .map { json -> KeyRecord.fromJson(json) }
                .toList()
        }
    }

    override fun deleteKey(id: String): Completable {
        return client.rxDeleteById(MongoCollections.keys, id)
    }

    override fun getJWKS(): JsonWebKeySet {
        return cachedJwks
    }

    override fun getYoungestKey(): PublicJsonWebKey {
        return youngestKey;
    }

    private fun generateKey(): PublicJsonWebKey {
        val key: PublicJsonWebKey = RsaJwkGenerator.generateJwk(2048);
        key.use = Use.SIGNATURE
        key.keyId = UUID.randomUUID().toString()
        key.algorithm = AlgorithmIdentifiers.RSA_USING_SHA256
        return key
    }

    private fun updateCache(keys: List<KeyRecord>): Unit {
        val jwks = JsonWebKeySet()
        var maxCreatedAt = 0L
        var youngest: PublicJsonWebKey? = null
        keys.forEach {
            val key = PublicJsonWebKey.Factory.newPublicJwk(it.key)
            jwks.addJsonWebKey(key)
            if (it.createdAt > maxCreatedAt) {
                maxCreatedAt = it.createdAt
                youngest = key
            }
        }
        cachedJwks = jwks
        youngestKey = youngest!!
    }
}