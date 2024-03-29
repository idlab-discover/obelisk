package idlab.obelisk.services.pub.ingest

import idlab.obelisk.definitions.MetricType
import idlab.obelisk.definitions.catalog.*
import idlab.obelisk.definitions.data.MetricEvent
import idlab.obelisk.definitions.data.Producer
import idlab.obelisk.definitions.framework.OblxConfig
import idlab.obelisk.definitions.framework.OblxService
import idlab.obelisk.definitions.ratelimiting.RateLimiter
import idlab.obelisk.plugins.accessmanager.basic.BasicAccessManagerModule
import idlab.obelisk.plugins.metastore.mongo.MongoDBMetaStoreModule
import idlab.obelisk.plugins.ratelimiter.gubernator.GubernatorRateLimiterModule
import idlab.obelisk.pulsar.utils.*
import idlab.obelisk.utils.service.OblxBaseModule
import idlab.obelisk.utils.service.OblxLauncher
import idlab.obelisk.utils.service.http.AuthorizationException
import idlab.obelisk.utils.service.http.BadRequestException
import idlab.obelisk.utils.service.http.writeHttpError
import idlab.obelisk.utils.service.instrumentation.IdToNameMap
import idlab.obelisk.utils.service.instrumentation.TagTemplate
import idlab.obelisk.utils.service.instrumentation.TargetType
import idlab.obelisk.utils.service.reactive.flatMap
import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.DistributionSummary
import io.reactivex.Completable
import io.reactivex.Flowable
import io.reactivex.Single
import io.reactivex.rxkotlin.subscribeBy
import io.vertx.core.json.jackson.DatabindCodec
import io.vertx.micrometer.backends.BackendRegistries
import io.vertx.reactivex.ext.web.Router
import io.vertx.reactivex.ext.web.RoutingContext
import io.vertx.reactivex.ext.web.handler.BodyHandler
import org.apache.pulsar.client.api.PulsarClient
import org.apache.pulsar.client.api.PulsarClientException.TimeoutException
import org.apache.pulsar.client.api.Schema
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.system.exitProcess

const val HTTP_BASE_PATH = "/data/ingest"
private val mapper = DatabindCodec.mapper()
private val eventListTypeFactory =
    mapper.typeFactory.constructCollectionType(List::class.java, IngestMetricEvent::class.java)
private val schema = Schema.JSON(MetricEvent::class.java)

private const val RECEIVED_EVENTS_METRIC = "oblx.ingest.received.events"
private const val REQUEST_SIZE_BYTES_METRIC = "oblx.ingest.request.size.bytes"
private const val REQUEST_SIZE_EVENTS_METRIC = "oblx.ingest.request.size.events"
private const val SEND_FAILURES_METRIC = "oblx.ingest.send.failures"

fun main(args: Array<String>) {
    OblxLauncher.with(
        OblxBaseModule(),
        BasicAccessManagerModule(),
        PulsarModule(),
        MongoDBMetaStoreModule(),
        GubernatorRateLimiterModule()
    ).bootstrap(IngestService::class.java)
}

@Singleton
class IngestService @Inject constructor(
    private val config: OblxConfig,
    private val router: Router,
    private val accessManager: AccessManager,
    private val pulsarClient: PulsarClient,
    private val rateLimiter: RateLimiter,
    private val metaStore: MetaStore
) : OblxService {

    private val microMeterRegistry = BackendRegistries.getDefaultNow()
    private val datasetIdAndNameTags = TagTemplate("datasetId", "datasetName")
    private val ingestSendFailures = Counter
        .builder(SEND_FAILURES_METRIC)
        .description("Counts number of times sending an event to Pulsar resulted in a failure.")
        .register(microMeterRegistry)
    private val receivedEventsBuilder = Counter
        .builder(RECEIVED_EVENTS_METRIC)
        .description("Counts events received by Oblx Ingest Service.")
    private val requestSizeBytesBuilder = DistributionSummary
        .builder(REQUEST_SIZE_BYTES_METRIC)
        .description("Summary tracking the byte size of requests to the Oblx Ingest Service.")
    private val requestSizeEventsBuilder = DistributionSummary
        .builder(REQUEST_SIZE_EVENTS_METRIC)
        .description("Summary tracking number of events per request to the Oblx Ingest Service")

    private val datasetIdToNameMap = IdToNameMap(metaStore, TargetType.DATASET)

    override fun start(): Completable {
        val basePath = config.getString(OblxConfig.HTTP_BASE_PATH_PROP, HTTP_BASE_PATH)
        val producerCache =
            RxPulsarProducerCache<MetricEvent>(
                pulsarClient
            ) { it.configureForHighThroughput() }

        router.route("$basePath/:datasetId").handler(BodyHandler.create())
        router.post("$basePath/:datasetId").handler { ctx ->
            val datasetId = ctx.pathParam("datasetId")
            val precision = getPrecision(ctx)
            val mode = getIngestMode(ctx)
            val targetTopic = when (mode) {
                IngestMode.STREAM_ONLY -> config.pulsarDatasetTopic(datasetId)
                IngestMode.STORE_ONLY -> config.storeOnlyMetricEventsTopic()
                IngestMode.DEFAULT -> config.pulsarMetricEventsTopic
            }

            getValidatedToken(datasetId, ctx)
                .flatMapCompletable { token ->
                    try {
                        val events = mapper.readValue<List<IngestMetricEvent>>(ctx.body().asString(), eventListTypeFactory)
                        if (events.isEmpty()) {
                            Completable.error(BadRequestException("The event array cannot be empty! "))
                        } else {
                            // Apply rate limiting
                            rateLimiter.apply(ctx, token, determineRateLimitCost(mode, events))
                                // Continue processing events
                                .flatMapPublisher { Flowable.range(0, events.size) }
                                .map { index ->
                                    events[index].convert(
                                        datasetId,
                                        Producer(token.user.id!!, token.client?.id),
                                        precision,
                                        index
                                    )
                                }
                                .flatMapCompletable {
                                    producerCache.rxGet(schema, targetTopic).flatMapCompletable { producer ->
                                        producer.rxSend(it).doOnError { ingestSendFailures.increment() }.ignoreElement()
                                    }
                                }
                        }
                            .doOnComplete {
                                val datasetName = datasetIdToNameMap.getName(datasetId) ?: ""
                                val tags = datasetIdAndNameTags.instantiate(datasetId, datasetName)
                                receivedEventsBuilder.tags(tags).register(microMeterRegistry)
                                    .increment(events.size.toDouble())
                                requestSizeEventsBuilder.tags(tags).register(microMeterRegistry)
                                    .record(events.size.toDouble())
                                requestSizeBytesBuilder.tags(tags).register(microMeterRegistry)
                                    .record(ctx.body.bytes.size.toDouble())
                            }
                    } catch (e: Exception) {
                        Completable.error(BadRequestException("Could not process the request body: ${e.message}"))
                    }
                }
                .subscribeBy(
                    onComplete = { ctx.response().setStatusCode(204).end() },
                    onError = { err ->
                        writeHttpError(ctx).invoke(err)
                        if (err is TimeoutException) {
                            logger.error(err) { "Detected PulsarClient TimeoutException, shutting down." }
                            exitProcess(1)
                        }
                    }
                )
        }
        return datasetIdToNameMap.init()
            .flatMap { producerCache.rxGet(schema, config.pulsarMetricEventsTopic).map { it.topic }.ignoreElement() }
    }

    private fun getValidatedToken(datasetId: String, ctx: RoutingContext): Single<Token> {
        return accessManager.getToken(ctx.request()).map {
            val grant = it.grants[datasetId]
            if (it.client != null && !it.client!!.scope.contains(Permission.WRITE)) {
                throw AuthorizationException("Client is not authorized to ingest events (requires WRITE in scope!)")
            }
            if (it.user.platformManager || (grant != null && grant.permissions.contains(Permission.WRITE))) {
                return@map it
            }
            // Write access denied, let's figure out if they used the name instead of the id
            if (datasetId.matches(Regex("^[0-9a-f]{24}$"))) {
                throw AuthorizationException(message = "Write access denied on dataset $datasetId")
            } else {
                throw AuthorizationException(message = "Write access denied on dataset $datasetId. (Did you use the dataset name instead of the id to ingest?)")
            }
        }
    }

    private fun determineRateLimitCost(mode: IngestMode, events: List<IngestMetricEvent>): Map<UsageLimitId, Int> {
        var nrOfPrimitiveEvents = 0
        var nrOfComplexEvents = 0
        events.forEach {
            when (it.metric.type) {
                MetricType.NUMBER_ARRAY, MetricType.BOOL, MetricType.NUMBER -> nrOfPrimitiveEvents++
                MetricType.JSON, MetricType.STRING -> nrOfComplexEvents++
            }
        }
        val result = mutableMapOf<UsageLimitId, Int>()

        if (mode == IngestMode.STORE_ONLY || mode == IngestMode.DEFAULT) {
            result[UsageLimitId.maxHourlyPrimitiveEventsStored] = nrOfPrimitiveEvents
            result[UsageLimitId.maxHourlyComplexEventsStored] = nrOfComplexEvents
        }

        if (mode == IngestMode.STREAM_ONLY || mode == IngestMode.DEFAULT) {
            result[UsageLimitId.maxHourlyPrimitiveEventsStreamed] = nrOfPrimitiveEvents
            result[UsageLimitId.maxHourlyComplexEventsStreamed] = nrOfComplexEvents
        }

        return result.filterNot { it.value == 0 }
    }
}

private fun getPrecision(ctx: RoutingContext): TimeUnit {
    return ctx.queryParams().get("timestampPrecision")?.let { TimeUnit.valueOf(it.uppercase()) }
        ?: TimeUnit.MILLISECONDS
}

private fun getIngestMode(ctx: RoutingContext): IngestMode {
    return ctx.queryParams().get("mode")?.let { IngestMode.valueOf(it.uppercase()) } ?: IngestMode.DEFAULT
}

private enum class IngestMode {
    DEFAULT, STREAM_ONLY, STORE_ONLY
}
