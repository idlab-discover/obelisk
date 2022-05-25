package idlab.obelisk.utils.service

import com.fasterxml.jackson.databind.MapperFeature
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.uber.rxdogtag.RxDogTag
import idlab.obelisk.definitions.framework.OblxModule
import idlab.obelisk.definitions.framework.OblxService
import io.reactivex.Completable
import io.reactivex.Flowable
import io.reactivex.plugins.RxJavaPlugins
import io.reactivex.rxkotlin.subscribeBy
import io.vertx.core.json.jackson.DatabindCodec
import io.vertx.reactivex.core.Vertx
import io.vertx.reactivex.ext.web.Router
import mu.KotlinLogging
import org.codejargon.feather.Feather
import org.codejargon.feather.Provides
import javax.inject.Singleton
import kotlin.system.exitProcess

const val READINESS_ENDPOINT = "/_status"
const val LIVENESS_ENDPOINT = "/_health"

class OblxLauncher private constructor(val modules: Iterable<OblxModule>) {

    private val feather: Feather = Feather.with(modules.plus(object : OblxModule {
        @Provides
        @Singleton
        fun launcher(): OblxLauncher {
            return this@OblxLauncher
        }
    }))

    companion object {

        private val logger = KotlinLogging.logger {}

        init {
            DatabindCodec.mapper().registerKotlinModule().enable(MapperFeature.ACCEPT_CASE_INSENSITIVE_ENUMS)
            DatabindCodec.prettyMapper().registerKotlinModule().enable(MapperFeature.ACCEPT_CASE_INSENSITIVE_ENUMS)

            // RxDogTag provides better rx stacktraces
            RxDogTag.install()
            // Set default rxjava error handler
            RxJavaPlugins.setErrorHandler { err -> logger.warn(err) { "An uncaught exception occurred in one of the rxjava flows..." } }
        }

        @JvmStatic
        fun with(vararg modules: OblxModule): OblxLauncher {
            return OblxLauncher(modules.asIterable())
        }

        @JvmStatic
        fun with(modules: Iterable<OblxModule>): OblxLauncher {
            return OblxLauncher(modules)
        }
    }

    fun <T> getInstance(clazz: Class<T>): T {
        try {
            return feather.instance(clazz)
        } catch (t: Throwable) {
            logger.error(t) { "Error while fetching instance of class ${clazz.name}..." }
            exitProcess(1)
        }
    }

    fun bootstrap(vararg serviceClasses: Class<out OblxService>) {
        rxBootstrap(*serviceClasses).subscribe()
    }

    fun rxBootstrap(vararg serviceClasses: Class<out OblxService>): Completable {
        // Register liveness and readiness endpoints (e.g. for Kubernetes probes)
        val router = getInstance(Router::class.java)
        val vertx = getInstance(Vertx::class.java)

        return vertx.rxExecuteBlocking<List<OblxService>> { promise ->
            try {
                promise.complete(serviceClasses.map { getInstance(it) })
            } catch (t: Throwable) {
                promise.fail(t)
            }
        }.flatMapCompletable { serviceInstances ->
            Flowable.fromIterable(serviceInstances).concatMapCompletable { serviceInstance ->
                serviceInstance.start()
                    .doOnComplete {
                        logger.info { "${serviceInstance::class.simpleName} successfully started!" }
                    }
                    .doOnError {
                        logger.error(it) { "${serviceInstance::class.simpleName} could not be started!" }
                        exitProcess(1)
                    }
            }.doOnComplete {
                // Now all services are successfully started, the readiness endpoint may return a ready state
                router.get(READINESS_ENDPOINT).handler { ctx ->
                    ctx.response().setStatusCode(200).end("Service is running!")
                }

                // And we can register the liveness endpoint
                router.get(LIVENESS_ENDPOINT).handler { ctx ->
                    Flowable.fromIterable(serviceInstances).concatMapCompletable { serviceInstance ->
                        serviceInstance.checkHealth()
                    }.subscribeBy(
                        onComplete = { ctx.response().setStatusCode(200).end("Service is healthy!") },
                        onError = {
                            ctx.response().setStatusCode(500).end("Service is not operating correctly: ${it.message}")
                        }
                    )
                }
            }
        }
    }
}
