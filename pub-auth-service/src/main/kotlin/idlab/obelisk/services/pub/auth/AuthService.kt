package idlab.obelisk.services.pub.auth

import idlab.obelisk.definitions.catalog.Token
import idlab.obelisk.definitions.framework.OblxConfig
import idlab.obelisk.definitions.framework.OblxService
import idlab.obelisk.plugins.accessmanager.basic.BasicAccessManagerModule
import idlab.obelisk.plugins.accessmanager.basic.store.AuthStore
import idlab.obelisk.plugins.accessmanager.basic.store.model.Session
import idlab.obelisk.plugins.metastore.mongo.MongoDBMetaStoreModule
import idlab.obelisk.services.pub.auth.oauth.BasicOAuthProvider
import idlab.obelisk.utils.service.OblxBaseModule
import idlab.obelisk.utils.service.OblxLauncher
import idlab.obelisk.utils.service.http.BadRequestException
import idlab.obelisk.utils.service.http.UnauthorizedException
import idlab.obelisk.utils.service.http.writeHttpError
import io.micrometer.core.instrument.Counter
import io.reactivex.Completable
import io.reactivex.functions.Consumer
import io.vertx.micrometer.backends.BackendRegistries
import io.vertx.reactivex.core.Vertx
import io.vertx.reactivex.ext.web.Router
import io.vertx.reactivex.ext.web.RoutingContext
import org.redisson.api.map.event.EntryCreatedListener
import org.redisson.api.map.event.EntryExpiredListener
import org.redisson.api.map.event.EntryRemovedListener
import org.slf4j.Logger
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton

const val HTTP_BASE_PATH = "/auth";
const val ADMIN_EMAIL_PROP = "ADMIN_EMAIL";
const val ADMIN_EMAIL = "admin@obelisk.ilabt.imec.be";

fun main(args: Array<String>) {
    OblxLauncher.with(
        OblxBaseModule(),
        BasicAccessManagerModule(),
        AuthServiceModule(),
        MongoDBMetaStoreModule()
    )
        .bootstrap(AuthService::class.java)
}

@Singleton
class AuthService @Inject constructor(
    private val vertx: Vertx,
    private val config: OblxConfig,
    private val router: Router,
    private val provider: BasicOAuthProvider
) : OblxService {

    override fun start(): Completable {
        return provider.setup(vertx, config, router).ignoreElement();
    }

    override fun checkHealth(): Completable {
        /*
         This method can be called to check if the service is healthy (e.g. by a Kubernetes probe)
         Completing without errors indicates a healthy state.
         */
        return Completable.complete()
    }
}