package idlab.obelisk.utils.service

import idlab.obelisk.definitions.framework.OblxConfig
import idlab.obelisk.definitions.framework.OblxModule
import idlab.obelisk.utils.service.mail.MailService
import idlab.obelisk.utils.service.mail.SmtpMailService
import io.micrometer.core.instrument.binder.jvm.JvmGcMetrics
import io.micrometer.core.instrument.binder.jvm.JvmMemoryMetrics
import io.micrometer.core.instrument.binder.jvm.JvmThreadMetrics
import io.micrometer.core.instrument.binder.system.ProcessorMetrics
import io.vertx.core.VertxOptions
import io.vertx.core.dns.AddressResolverOptions
import io.vertx.core.http.HttpHeaders
import io.vertx.core.http.HttpMethod
import io.vertx.core.http.HttpServerOptions
import io.vertx.ext.mail.MailConfig
import io.vertx.ext.mail.StartTLSOptions
import io.vertx.micrometer.MetricsDomain
import io.vertx.micrometer.MicrometerMetricsOptions
import io.vertx.micrometer.VertxPrometheusOptions
import io.vertx.micrometer.backends.BackendRegistries
import io.vertx.reactivex.config.ConfigRetriever
import io.vertx.reactivex.core.Vertx
import io.vertx.reactivex.ext.mail.MailClient
import io.vertx.reactivex.ext.web.Router
import io.vertx.reactivex.ext.web.handler.CorsHandler
import mu.KotlinLogging
import org.codejargon.feather.Provides
import org.redisson.Redisson
import org.redisson.api.RedissonClient
import java.net.URI
import javax.inject.Singleton
import kotlin.math.log

class OblxBaseModule() :
    OblxModule {

    private val logger = KotlinLogging.logger {}

    @Provides
    @Singleton
    fun vertx(): Vertx {
        val vertxOptions = VertxOptions().setAddressResolverOptions(AddressResolverOptions().setMaxQueries(7))
        vertxOptions.metricsOptions = MicrometerMetricsOptions()
            .setPrometheusOptions(
                VertxPrometheusOptions()
                    .setEnabled(true)
                    .setStartEmbeddedServer(true)
                    .setEmbeddedServerOptions(
                        HttpServerOptions().setPort(
                            System.getenv(OblxConfig.ENV_METRICS_PORT)?.toInt() ?: 8081
                        )
                    )
            )
            .setEnabled(true)
            .addDisabledMetricsCategory(MetricsDomain.EVENT_BUS)
            .addDisabledMetricsCategory(MetricsDomain.VERTICLES)
        val vertx = Vertx.vertx(vertxOptions);

        // Bind JVM instrumentation
        val registry = BackendRegistries.getDefaultNow()
        JvmMemoryMetrics().bindTo(registry)
        JvmGcMetrics().bindTo(registry)
        ProcessorMetrics().bindTo(registry)
        JvmThreadMetrics().bindTo(registry)

        return vertx
    }

    @Provides
    @Singleton
    fun router(vertx: Vertx, config: OblxConfig): Router {
        val router = Router.router(vertx)
        val corsHandler = CorsHandler.create("*")
            .allowedMethods(
                mutableSetOf(
                    HttpMethod.GET,
                    HttpMethod.PUT,
                    HttpMethod.POST,
                    HttpMethod.DELETE,
                    HttpMethod.OPTIONS,
                    HttpMethod.HEAD
                )
            )
            .allowedHeaders(
                mutableSetOf(
                    HttpHeaders.ACCEPT.toString(),
                    HttpHeaders.CONTENT_TYPE.toString(),
                    HttpHeaders.AUTHORIZATION.toString(),
                    HttpHeaders.CONTENT_DISPOSITION.toString()
                )
            )
            .exposedHeaders(
                mutableSetOf(
                    HttpHeaders.CONTENT_DISPOSITION.toString()
                )
            )
        router.route().handler(corsHandler)

        router.errorHandler(404) { ctx ->
            logger.warn { "No resource for ${ctx.request().method()} ${ctx.request().path()}" }
        }
        router.errorHandler(500) { ctx ->
            val failure = ctx.failure()
            logger.warn(failure) {
                "Reporting uncaught exception in HTTP call: ${ctx.request().method()} ${
                    ctx.request().path()
                }"
            }
        }

        vertx.createHttpServer(HttpServerOptions().setCompressionSupported(true)).requestHandler(router)
            .listen(config.httpPort)
        return router
    }

    @Provides
    @Singleton
    fun config(vertx: Vertx): OblxConfig {
        return ConfigRetriever.create(vertx).rxGetConfig()
            .map { it.mapTo(OblxConfig::class.java) }
            .blockingGet()
    }

    @Provides
    @Singleton
    fun redis(config: OblxConfig): RedissonClient {
        val redisConfig: org.redisson.config.Config = org.redisson.config.Config()
        redisConfig.useSingleServer().address = config.redisConnectionUri
        return Redisson.create(redisConfig)
    }

    @Provides
    @Singleton
    fun mailClient(vertx: Vertx, config: OblxConfig): MailService {
        val smtpConnectionUri = URI(config.smtpConnectionUri)
        val mailConfig = MailConfig().setHostname(smtpConnectionUri.host).setPort(smtpConnectionUri.port)
            .setStarttls(if (config.smtpUseTls) StartTLSOptions.REQUIRED else StartTLSOptions.DISABLED)
            .setAllowRcptErrors(true)
            .setTrustAll(true)

        val userAndPassword = smtpConnectionUri.userInfo?.split(":")
        userAndPassword?.getOrNull(0)?.let { mailConfig.setUsername(it) }
        userAndPassword?.getOrNull(1)?.let { mailConfig.setPassword(it) }
        val mailClient = MailClient.create(vertx, mailConfig)

        return SmtpMailService(mailClient, config)
    }

}