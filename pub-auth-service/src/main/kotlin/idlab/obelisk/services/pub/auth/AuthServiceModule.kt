package idlab.obelisk.services.pub.auth

import idlab.obelisk.definitions.framework.OblxConfig
import idlab.obelisk.definitions.framework.OblxModule
import idlab.obelisk.plugins.accessmanager.basic.store.AuthStore
import idlab.obelisk.plugins.accessmanager.basic.store.RedisAuthStore
import idlab.obelisk.services.pub.auth.keys.KeyStore
import idlab.obelisk.services.pub.auth.keys.MongoDBKeyStore
import idlab.obelisk.services.pub.auth.oidc.OpenIdConnector
import idlab.obelisk.services.pub.auth.oidc.OpenIdConnectorImpl
import idlab.obelisk.services.pub.auth.oidc.provider.GoogleIdentityProvider
import idlab.obelisk.services.pub.auth.oidc.provider.IdentityProvider
import idlab.obelisk.services.pub.auth.oidc.provider.LocalIdentityProvider
import idlab.obelisk.services.pub.auth.token.Jose4JTokenMinter
import idlab.obelisk.services.pub.auth.token.TokenMinter
import io.vertx.core.json.JsonObject
import io.vertx.reactivex.core.Vertx
import io.vertx.reactivex.ext.mongo.MongoClient
import io.vertx.reactivex.ext.web.client.WebClient
import org.codejargon.feather.Provides
import javax.inject.Named
import javax.inject.Singleton

class AuthServiceModule : OblxModule {
    @Provides
    @Singleton
    @Named("google")
    fun googleIdp(googleIdp: GoogleIdentityProvider): IdentityProvider {
        return googleIdp
    }

    @Provides
    @Singleton
    @Named("local")
    fun localIdp(localIdp: LocalIdentityProvider): IdentityProvider {
        return localIdp
    }

    @Provides
    @Singleton
    @Named("basic")
    fun basicOpenIdConnector(openIdConnector: OpenIdConnectorImpl): OpenIdConnector {
        return openIdConnector
    }

    @Provides
    @Singleton
    fun webclient(vertx: Vertx): WebClient {
        return WebClient.create(vertx)
    }

    @Provides
    @Singleton
    fun jose4jTokenMinter(jose4jTokenMinter: Jose4JTokenMinter): TokenMinter {
        return jose4jTokenMinter
    }

    @Provides
    @Singleton
    fun keyStore(vertx: Vertx, config: OblxConfig): KeyStore {
        val mongoClient = MongoClient.createShared(
            vertx,
            JsonObject().put("connection_string", config.mongoConnectionUri).put("db_name", config.mongoDbName)
        )
        val mongoDBKeyStore = MongoDBKeyStore(mongoClient)
        mongoDBKeyStore.initialize();
        return mongoDBKeyStore
    }

    @Provides
    @Singleton
    fun redisAuthStore(redisAuthStore: RedisAuthStore): AuthStore {
        redisAuthStore.initInstrumentation()
        return redisAuthStore
    }
}