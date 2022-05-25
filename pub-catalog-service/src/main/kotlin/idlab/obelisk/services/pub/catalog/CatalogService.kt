package idlab.obelisk.services.pub.catalog

import com.google.common.reflect.ClassPath
import graphql.GraphQL
import graphql.execution.AsyncExecutionStrategy
import graphql.execution.AsyncSerialExecutionStrategy
import graphql.scalars.ExtendedScalars
import graphql.schema.GraphQLSchema
import graphql.schema.idl.RuntimeWiring
import graphql.schema.idl.SchemaGenerator
import graphql.schema.idl.SchemaParser
import graphql.schema.idl.TypeDefinitionRegistry
import idlab.obelisk.definitions.catalog.AccessManager
import idlab.obelisk.definitions.framework.OblxConfig
import idlab.obelisk.definitions.framework.OblxService
import idlab.obelisk.plugins.accessmanager.basic.BasicAccessManagerModule
import idlab.obelisk.plugins.datastore.clickhouse.ClickhouseDataStoreModule
import idlab.obelisk.plugins.metastore.mongo.MongoDBMetaStoreModule
import idlab.obelisk.plugins.monitoring.prometheus.PrometheusMonitoringModule
import idlab.obelisk.plugins.ratelimiter.gubernator.GubernatorRateLimiterModule
import idlab.obelisk.pulsar.utils.PulsarModule
import idlab.obelisk.services.pub.catalog.impl.*
import idlab.obelisk.services.pub.catalog.types.util.GQLType
import idlab.obelisk.services.pub.catalog.types.util.wiring
import idlab.obelisk.utils.service.OblxBaseModule
import idlab.obelisk.utils.service.OblxLauncher
import idlab.obelisk.utils.service.http.writeHttpError
import io.reactivex.Completable
import io.reactivex.rxkotlin.subscribeBy
import io.vertx.reactivex.ext.web.Router
import io.vertx.reactivex.ext.web.handler.BodyHandler
import io.vertx.reactivex.ext.web.handler.graphql.GraphQLHandler
import java.nio.file.Files
import java.nio.file.Path
import javax.inject.Inject
import javax.inject.Singleton

const val CATALOG_ENDPOINT = "/catalog"
const val SDL_SCAN_RESOURCE_FOLDER = "schemas"
const val GQL_TYPES_PACKAGE = "idlab.obelisk.services.pub.catalog.types"

fun main(args: Array<String>) {
    OblxLauncher
        .with(
            OblxBaseModule(),
            ClickhouseDataStoreModule(),
            MongoDBMetaStoreModule(),
            BasicAccessManagerModule(),
            PulsarModule(),
            GubernatorRateLimiterModule(),
            PrometheusMonitoringModule()
        )
        .bootstrap(CatalogService::class.java)
}

@Singleton
class CatalogService @Inject constructor(
    private val oblxLauncher: OblxLauncher,
    private val router: Router,
    private val accessManager: AccessManager,
    private val config: OblxConfig,
    private val dataLoaders: DataLoaders,
    private val resourcesEndpoints: Resources
) : OblxService {

    override fun start(): Completable {
        val basePath = config.getString(OblxConfig.HTTP_BASE_PATH_PROP, CATALOG_ENDPOINT)
        val runtTimeWiring = buildRuntimeWiring()
        val graphQLSchema = buildSchema(runtTimeWiring)

        val graphQL = GraphQL.newGraphQL(graphQLSchema)
            .mutationExecutionStrategy(AsyncSerialExecutionStrategy(OblxDataFetcherExceptionHandler()))
            .queryExecutionStrategy(AsyncExecutionStrategy(OblxDataFetcherExceptionHandler()))
            .build()
        router.route("${basePath}/graphql")
            .handler(BodyHandler.create())
            .handler { ctx -> loadToken(accessManager, ctx) }
            .handler(GraphQLHandler.create(graphQL).dataLoaderRegistry(dataLoaders::buildRegistry))

        router.get("${basePath}/me")
            .handler { ctx ->
                accessManager.getToken(ctx.request())
                    .subscribeBy(
                        onSuccess = { ctx.json(it) },
                        onError = writeHttpError(ctx)
                    )
            }

        resourcesEndpoints.init(basePath)
        return Completable.complete()
    }

    fun buildSchema(runtimeWiring: RuntimeWiring): GraphQLSchema {
        val schemaParser = SchemaParser()
        val typeRegistry = TypeDefinitionRegistry()

        val parentPath =
            Path.of(Thread.currentThread().contextClassLoader?.getResource(SDL_SCAN_RESOURCE_FOLDER)?.toURI())

        listGraphqlFiles(parentPath).forEach {
            logger.info { "Loading graphql schema SDL: ${parentPath.relativize(it)}" }
            typeRegistry.merge(schemaParser.parse(it.toFile()))
        }
        return SchemaGenerator().makeExecutableSchema(typeRegistry, runtimeWiring)
    }

    private fun listGraphqlFiles(parentPath: Path): List<Path> {
        val stack = ArrayDeque<Path>()
        val files = mutableListOf<Path>()
        stack.addFirst(parentPath)
        while (!stack.isEmpty()) {
            Files.newDirectoryStream(stack.removeFirst()).use { stream ->
                stream.forEach {
                    if (Files.isDirectory(it)) {
                        stack.add(it)
                    } else if (it.toFile().name.endsWith(".graphql")) {
                        files.add(it)
                    }
                }
            }
        }
        return files
    }

    fun buildRuntimeWiring(): RuntimeWiring {
        val runtimeWiring = RuntimeWiring.newRuntimeWiring()
        // Get all the classes annotated with @GQLType from the types package.
        val typeClasses = ClassPath.from(CatalogService::class.java.classLoader)
            .getTopLevelClasses(GQL_TYPES_PACKAGE)
            .map { it.load() }
            .filter { it.annotations.any { it is GQLType } }

        // For each type class:
        typeClasses
            .map { oblxLauncher.getInstance(it) } // Instantiate using Feather // Obelisk Launcher context
            .forEach { runtimeWiring.type(it.wiring()) } // Register the Type and its datafetchers with the GraphQL runtime

        return runtimeWiring
            .scalar(ExtendedScalars.Json)
            .scalar(ExtendedScalars.GraphQLLong)
            .directive(PagedDirective.PAGED, PagedDirective())
            .directive("restricted", RestrictedDirective())
            .directive(FilterDirective.ENABLE_FILTER, FilterDirective())
            .type("OriginProducer") { it.typeResolver(::originProducerResolver) }
            .build()
    }
}