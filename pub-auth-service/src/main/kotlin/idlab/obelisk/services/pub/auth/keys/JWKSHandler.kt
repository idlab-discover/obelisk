package idlab.obelisk.services.pub.auth.keys

import io.vertx.core.Handler
import io.vertx.reactivex.ext.web.RoutingContext
import org.jose4j.jwk.JsonWebKey

class JWKSHandler : Handler<RoutingContext> {
    private val keyStore: KeyStore
    private var cache: String? = null

    private constructor(keyStore: KeyStore) {
        this.keyStore = keyStore
    }

    companion object {
        /**
         * Create a new JWKSHandler.
         * This handler retrieves the cached JWKS from the keystore and then caches the serialized version for itself.
         * This cached version is served as a String from memory.
         */
        fun create(keyStore: KeyStore): JWKSHandler {
            return JWKSHandler(keyStore)
        }
    }

    /**
     * Return the cached JWKS as JsonString. Will prepare the cache if still empty!
     */
    private fun getCached(): String {
        if (cache == null) {
            cache = keyStore.getJWKS().toJson(JsonWebKey.OutputControlLevel.PUBLIC_ONLY)
        }
        return cache!!;
    }

    override fun handle(ctx: RoutingContext) {
        ctx.end(getCached())
    }
}