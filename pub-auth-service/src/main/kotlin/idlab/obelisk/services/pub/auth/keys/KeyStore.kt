package idlab.obelisk.services.pub.auth.keys

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty
import io.reactivex.Completable
import io.reactivex.Flowable
import io.reactivex.Single
import io.vertx.core.json.Json
import io.vertx.core.json.JsonObject
import org.jose4j.jwk.JsonWebKey
import org.jose4j.jwk.JsonWebKeySet
import org.jose4j.jwk.PublicJsonWebKey

interface KeyStore {

    /**
     * Store a new key in the database
     */
    fun storeKey(key: PublicJsonWebKey): Single<String>

    /**
     * Store multiple keys in the database
     */
    fun storeKeys(vararg keys: PublicJsonWebKey): Single<List<String>>

    /**
     * List all keys in the database as KeyRecords
     */
    fun listKeys(): Single<List<KeyRecord>>

    /**
     * Get the cached JWKS
     */
    fun getJWKS(): JsonWebKeySet

    /**
     * Get the youngest key.
     * DO NOT SPREAD THIS KEY PUBLICLY! It contains both public and PRIVATE keys.
     */
    fun getYoungestKey(): PublicJsonWebKey;

    /**
     * Delete a key from the database
     * @param id The id of the KeyRecord! (not the JWK)
     */
    fun deleteKey(id: String): Completable

    /**
     * Count the amount of KeyRecords in the database
     */
    fun countKeys(): Single<Long>
}

@JsonInclude(JsonInclude.Include.NON_EMPTY)
class KeyRecord {
    @JsonProperty("_id")
    var id: String?
    var key: String
    var createdAt: Long

    constructor(id: String?, key: String, createdAt: Long) {
        this.id = id;
        this.key = key
        this.createdAt = createdAt
    }

    companion object {
        fun fromJson(json: JsonObject): KeyRecord {
            return Json.decodeValue(json.encode(), KeyRecord::class.java)
        }
    }

}