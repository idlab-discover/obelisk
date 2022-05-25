package idlab.obelisk.services.pub.query

import idlab.obelisk.definitions.DataRange
import idlab.obelisk.definitions.DataStoreQueryTimeoutException
import idlab.obelisk.definitions.PagedResult
import idlab.obelisk.definitions.catalog.AccessManager
import idlab.obelisk.definitions.catalog.MetaStore
import idlab.obelisk.definitions.catalog.UsageLimitId
import idlab.obelisk.definitions.data.DataQuery
import idlab.obelisk.definitions.data.DataStore
import idlab.obelisk.definitions.data.EventsQuery
import idlab.obelisk.definitions.data.StatsQuery
import idlab.obelisk.definitions.framework.OblxConfig
import idlab.obelisk.definitions.framework.OblxService
import idlab.obelisk.definitions.ratelimiting.RateLimiter
import idlab.obelisk.plugins.accessmanager.basic.BasicAccessManagerModule
import idlab.obelisk.plugins.datastore.clickhouse.ClickhouseDataStoreModule
import idlab.obelisk.plugins.metastore.mongo.MongoDBMetaStoreModule
import idlab.obelisk.plugins.ratelimiter.gubernator.GubernatorRateLimiterModule
import idlab.obelisk.utils.service.OblxBaseModule
import idlab.obelisk.utils.service.OblxLauncher
import idlab.obelisk.utils.service.http.writeHttpError
import idlab.obelisk.utils.service.http.writeHttpResponse
import idlab.obelisk.utils.service.instrumentation.IdToNameMap
import idlab.obelisk.utils.service.instrumentation.TagTemplate
import idlab.obelisk.utils.service.instrumentation.TargetType
import idlab.obelisk.utils.service.utils.applyToken
import io.reactivex.Completable
import io.reactivex.Single
import io.reactivex.rxkotlin.subscribeBy
import io.vertx.micrometer.backends.BackendRegistries
import io.vertx.reactivex.ext.web.Router
import io.vertx.reactivex.ext.web.RoutingContext
import io.vertx.reactivex.ext.web.handler.BodyHandler
import mu.KotlinLogging
import java.lang.Integer.min
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

fun main(args: Array<String>) {
    OblxLauncher.with(
        OblxBaseModule(),
        BasicAccessManagerModule(),
        MongoDBMetaStoreModule(),
        ClickhouseDataStoreModule(),
        GubernatorRateLimiterModule()
    )
        .bootstrap(QueryService::class.java)
}

private enum class QueryType {
    events, stats;

    companion object {
        fun from(q: DataQuery): QueryType {
            return when (q) {
                is EventsQuery -> events
                is StatsQuery -> stats
                else -> throw IllegalArgumentException("Invalid query type")
            }
        }
    }
}

const val BASE_PATH = "/data/query"
const val ENV_SLOW_QUERY_THRESHOLD_MS = "SLOW_QUERY_THRESHOLD_MS"
const val DEFAULT_SLOW_QUERY_THRESHOLD_MS = 1000L
const val ENV_MAX_EVENT_QUERY_LIMIT = "MAX_EVENT_QUERY_LIMIT"
const val DEFAULT_MAX_EVENT_QUERY_LIMIT = 100000
const val ENV_MAX_STATS_QUERY_LIMIT = "MAX_STATS_QUERY_LIMIT"
const val DEFAULT_MAX_STATS_QUERY_LIMIT = 2400

private const val GLOBAL_REQUESTS_METRIC = "oblx.query.global.requests"
private const val REQUESTS_METRIC = "oblx.query.requests"
private const val GLOBAL_RESPONSE_SIZE_METRIC = "oblx.query.global.response.size"
private const val RESPONSE_SIZE_METRIC = "oblx.query.response.size"
private const val GLOBAL_TARGETED_DATASETS_METRIC = "oblx.query.global.datarange.datasets"
private val specificQueryTags = TagTemplate("queryType", "datasetId", "datasetName")
private val globalQueryTags = TagTemplate("queryType")

@Singleton
class QueryService @Inject constructor(
    private val router: Router,
    private val accessManager: AccessManager,
    private val datastore: DataStore,
    metaStore: MetaStore,
    private val config: OblxConfig,
    private val rateLimiter: RateLimiter
) : OblxService {

    private val microMeterRegistry = BackendRegistries.getDefaultNow()

    private val logger = KotlinLogging.logger { }
    private val datasetIdToNameMap = IdToNameMap(metaStore, TargetType.DATASET)
    private val slowestQueriesTracker =
        SlowestQueriesTracker(
            slowQueryThreshold = config.getLong(
                ENV_SLOW_QUERY_THRESHOLD_MS,
                DEFAULT_SLOW_QUERY_THRESHOLD_MS
            )
        )

    private val maxEventQueryLimit = config.getInteger(ENV_MAX_EVENT_QUERY_LIMIT, DEFAULT_MAX_EVENT_QUERY_LIMIT)
    private val maxStatsQueryLimit = config.getInteger(ENV_MAX_STATS_QUERY_LIMIT, DEFAULT_MAX_STATS_QUERY_LIMIT)

    override fun start(): Completable {
        val basePath = config.getString(OblxConfig.HTTP_BASE_PATH_PROP, BASE_PATH)
        val eventsEndpoint = "${basePath}/events"
        val eventsLatestEndpoint = "${basePath}/events/latest"
        val statsEndpoint = "${basePath}/stats"

        router.route(eventsEndpoint).handler(BodyHandler.create())
        router.post(eventsEndpoint)
            .handler(query(EventsQuery::class.java, datastore::getEvents))

        router.route(eventsLatestEndpoint).handler(BodyHandler.create())
        router.post(eventsLatestEndpoint)
            .handler(query(EventsQuery::class.java, datastore::getLatestEvents))

        router.route(statsEndpoint).handler(BodyHandler.create())
        router.post(statsEndpoint)
            .handler(query(StatsQuery::class.java, datastore::getStats))

        router.get("$basePath/meta/slowest").handler { ctx -> ctx.json(slowestQueriesTracker.list()) }

        return datasetIdToNameMap.init()
    }

    private fun <Q : DataQuery, QR> query(
        queryClass: Class<Q>,
        executor: (Q) -> Single<PagedResult<QR>>,
    ): (RoutingContext) -> Unit {
        return { ctx: RoutingContext ->
            try {
                // Parse the request body as a DataQuery
                val query = ctx.bodyAsJson.mapTo(queryClass)
                // Get an Auth Token for the request
                accessManager.getToken(ctx.request())
                    .flatMap { rateLimiter.apply(ctx, it, determineRateLimitingProps(query)) }
                    .map {
                        // Apply the token to the query (check access control and add implicit filters if necessary)
                        // Apply restrictions to the user supplied limit values
                        fixLimits(query.applyToken(it))
                    }
                    .flatMap { restrictedQuery ->
                        val start = System.currentTimeMillis()
                        executor.invoke(restrictedQuery)
                            .doOnError { err ->
                                if (err is DataStoreQueryTimeoutException) {
                                    slowestQueriesTracker.notifyTimeout(restrictedQuery)
                                }
                            }
                            .doOnSuccess {
                                applyInstrumentation(
                                    restrictedQuery,
                                    it,
                                    System.currentTimeMillis() - start
                                )
                            }
                    } // Execute the query
                    .subscribeBy(
                        onSuccess = writeHttpResponse(ctx),
                        onError = writeHttpError(ctx)
                    ) // Write the HTTP response
            } catch (err: Exception) {
                ctx.response().setStatusCode(400)
                    .end("Could not process the request body, check the validity of your query! Details: ${err.message}")
            }
        }
    }

    private fun applyInstrumentation(query: DataQuery, response: PagedResult<*>, durationMs: Long) {
        val queryType = QueryType.from(query).toString()

        // Slow query tracking
        slowestQueriesTracker.notifyQueryExecuted(query, durationMs)

        // Per dataset instrumentation
        query.dataRange.datasets.forEach { datasetId ->
            val datasetName = datasetIdToNameMap.getName(datasetId) ?: ""
            val tags = specificQueryTags.instantiate(queryType, datasetId, datasetName)
            microMeterRegistry.timer(REQUESTS_METRIC, tags).record(durationMs, TimeUnit.MILLISECONDS)
            microMeterRegistry.summary(RESPONSE_SIZE_METRIC, tags).record(response.items.size.toDouble())
        }

        // Global instrumentation
        val globalTags = globalQueryTags.instantiate(queryType)
        microMeterRegistry.timer(GLOBAL_REQUESTS_METRIC, globalTags).record(durationMs, TimeUnit.MILLISECONDS)
        microMeterRegistry.summary(GLOBAL_RESPONSE_SIZE_METRIC, globalTags).record(response.items.size.toDouble())
        microMeterRegistry.summary(GLOBAL_TARGETED_DATASETS_METRIC, globalTags)
            .record(query.dataRange.datasets.size.toDouble())
    }

    private fun determineRateLimitingProps(query: DataQuery): Map<UsageLimitId, Int> {
        val targetsComplexTypes =
            query.dataRange.metrics.any { it.type.isComplex() || (it.isDerived() && it.getParent()?.type?.isComplex() == true) }
        return when (query) {
            is EventsQuery -> mapOf((if (targetsComplexTypes) UsageLimitId.maxHourlyComplexEventQueries else UsageLimitId.maxHourlyPrimitiveEventQueries) to 1)
            is StatsQuery -> mapOf((if (targetsComplexTypes) UsageLimitId.maxHourlyComplexStatsQueries else UsageLimitId.maxHourlyPrimitiveStatsQueries) to 1)
            else -> throw IllegalArgumentException("Query type ${query::class.simpleName} cannot be processed!")
        }
    }


    private val statsQueryTemplate = StatsQuery(DataRange.allData)
    private val eventsQueryTemplate = EventsQuery(DataRange.allData)

    // Restrict the range of the limit argument the users are allowed to query
    private fun <T : DataQuery> fixLimits(query: T): T {
        return when (query) {
            is EventsQuery -> (if (query.limit == 0) query.copy(limit = eventsQueryTemplate.limit) else query.copy(
                limit = min(
                    query.limit,
                    maxEventQueryLimit
                )
            )) as T
            is StatsQuery -> (if (query.limit == 0) query.copy(limit = statsQueryTemplate.limit) else query.copy(
                limit = min(
                    query.limit,
                    maxStatsQueryLimit
                )
            )) as T
            else -> throw IllegalArgumentException("Query type ${query::class.simpleName} cannot be processed!")
        }
    }
}