package idlab.obelisk.client

import com.fasterxml.jackson.databind.MapperFeature
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.github.davidmoten.rx2.RetryWhen
import com.github.davidmoten.rx2.flowable.Transformers
import idlab.obelisk.definitions.PagedResult
import idlab.obelisk.definitions.TimestampPrecision
import idlab.obelisk.definitions.data.EventsQuery
import idlab.obelisk.definitions.data.MetricEvent
import idlab.obelisk.definitions.data.MetricStat
import idlab.obelisk.definitions.data.StatsQuery
import io.reactivex.Completable
import io.reactivex.Flowable
import io.reactivex.FlowableTransformer
import io.reactivex.Single
import io.vertx.core.http.HttpClientOptions
import io.vertx.core.http.HttpHeaders
import io.vertx.core.http.HttpMethod
import io.vertx.core.http.RequestOptions
import io.vertx.core.json.Json
import io.vertx.core.json.JsonArray
import io.vertx.core.json.JsonObject
import io.vertx.core.json.jackson.DatabindCodec
import io.vertx.ext.web.client.WebClientOptions
import io.vertx.reactivex.core.Vertx
import io.vertx.reactivex.core.buffer.Buffer
import io.vertx.reactivex.core.http.HttpClient
import io.vertx.reactivex.ext.web.client.HttpRequest
import io.vertx.reactivex.ext.web.client.HttpResponse
import io.vertx.reactivex.ext.web.client.WebClient
import mu.KotlinLogging
import io.vertx.reactivex.core.parsetools.RecordParser
import org.reactivestreams.Publisher
import java.util.concurrent.TimeUnit

class OblxClient(
    private val httpClient: WebClient,
    private val rawClient: HttpClient,
    private val options: OblxClientOptions
) {

    private val logger = KotlinLogging.logger { }

    enum class IngestMode {
        default, stream_only, store_only
    }

    companion object {

        private const val SSE_SEPARATOR = "\n\n"
        private const val SSE_TEXT_ENCODING = "UTF-8"

        init {
            DatabindCodec.mapper().registerKotlinModule().enable(MapperFeature.ACCEPT_CASE_INSENSITIVE_ENUMS)
            DatabindCodec.prettyMapper().registerKotlinModule().enable(MapperFeature.ACCEPT_CASE_INSENSITIVE_ENUMS)
        }

        fun create(vertx: Vertx = Vertx.vertx(), options: OblxClientOptions): OblxClient {
            return OblxClient(
                WebClient.create(vertx, WebClientOptions().setKeepAlive(false).setTryUseCompression(true)),
                vertx.createHttpClient(
                    HttpClientOptions().setKeepAlive(false)
                ),
                options
            )
        }

    }

    private fun WebClient.obeliskRequest(path: String, method: HttpMethod = HttpMethod.POST): HttpRequest<Buffer> {
        val fullPath = "${options.safeApiUrl}$path"
        val request = when (method) {
            HttpMethod.HEAD -> this.headAbs(fullPath)
            HttpMethod.GET -> this.getAbs(fullPath)
            HttpMethod.PATCH -> this.patchAbs(fullPath)
            HttpMethod.PUT -> this.putAbs(fullPath)
            HttpMethod.DELETE -> this.deleteAbs(fullPath)
            HttpMethod.POST -> this.postAbs(fullPath)
            else -> throw IllegalArgumentException("OblxClient does not support $method")
        }
        if (options.virtualHost != null) {
            request.virtualHost(options.virtualHost)
        }
        return request.timeout(options.requestTimeoutMs)
    }

    private var token: String? = null
    private fun getToken(): Single<String> {
        return if (token != null) {
            Single.just(token)
        } else {
            httpClient.obeliskRequest("/auth/token")
                .rxSendJson(
                    JsonObject()
                        .put("grant_type", "client_credentials")
                        .put("client_id", options.clientId)
                        .put("client_secret", options.secret)
                )
                .json()
                .map { it.getString("token") }
                .doOnSuccess { token = it }
        }
    }

    private fun HttpRequest<Buffer>.rxSendJsonAuthed(body: Any, retry: Boolean = false): Single<HttpResponse<Buffer>> {
        return getToken()
            .flatMap { token ->
                this.putHeader(HttpHeaders.AUTHORIZATION.toString(), "Bearer $token").rxSendJson(body)
            }
            .flatMap { resp ->
                if (!retry && resp.statusCode() in setOf(401, 403)) {
                    // Invalidate stored token
                    token = null
                    // Retry
                    this.copy().rxSendJsonAuthed(body, true)
                } else {
                    Single.just(resp)
                }
            }
    }

    private fun HttpRequest<Buffer>.rxSendAuthed(retry: Boolean = false): Single<HttpResponse<Buffer>> {
        return getToken()
            .flatMap { token ->
                this.putHeader(HttpHeaders.AUTHORIZATION.toString(), "Bearer $token").rxSend()
            }
            .flatMap { resp ->
                if (!retry && resp.statusCode() in setOf(401, 403)) {
                    // Invalidate stored token
                    token = null
                    // Retry
                    this.copy().rxSendAuthed(true)
                } else {
                    Single.just(resp)
                }
            }
    }

    fun ingest(
        datasetId: String,
        event: MetricEvent,
        timestampPrecision: TimestampPrecision = TimestampPrecision.milliseconds
    ): Completable {
        return ingest(datasetId, listOf(event), timestampPrecision)
    }

    fun ingest(
        datasetId: String,
        events: List<MetricEvent>,
        timestampPrecision: TimestampPrecision = TimestampPrecision.milliseconds,
        mode: IngestMode = IngestMode.default
    ): Completable {
        return httpClient.obeliskRequest("/data/ingest/$datasetId?timestampPrecision=$timestampPrecision&mode=$mode")
            .rxSendJsonAuthed(JsonArray(events))
            .emptyResult()
    }

    fun queryCatalog(graphqlQuery: String): Single<JsonObject> {
        return httpClient.obeliskRequest("/catalog/graphql")
            .rxSendJsonAuthed(JsonObject().put("query", graphqlQuery))
            .json()
    }

    fun customHttpGet(
        apiSubPath: String,
        customizer: (HttpRequest<Buffer>) -> HttpRequest<Buffer> = { it }
    ): Single<HttpResponse<Buffer>> {
        return httpClient.obeliskRequest(apiSubPath, HttpMethod.GET)
            .apply { customizer.invoke(this) }
            .rxSendAuthed()
    }

    fun customHttpPost(
        apiSubPath: String, requestBody: Any,
        customizer: (HttpRequest<Buffer>) -> HttpRequest<Buffer> = { it }
    ): Single<HttpResponse<Buffer>> {
        return httpClient.obeliskRequest(apiSubPath)
            .apply { customizer.invoke(this) }
            .rxSendJsonAuthed(requestBody)
    }

    fun customHttpPut(
        apiSubPath: String, requestBody: Any,
        customizer: (HttpRequest<Buffer>) -> HttpRequest<Buffer> = { it }
    ): Single<HttpResponse<Buffer>> {
        return httpClient.obeliskRequest(apiSubPath, HttpMethod.PUT)
            .apply { customizer.invoke(this) }
            .rxSendJsonAuthed(requestBody)
    }

    fun customHttpPatch(
        apiSubPath: String, requestBody: Any,
        customizer: (HttpRequest<Buffer>) -> HttpRequest<Buffer> = { it }
    ): Single<HttpResponse<Buffer>> {
        return httpClient.obeliskRequest(apiSubPath, HttpMethod.PATCH)
            .apply { customizer.invoke(this) }
            .rxSendJsonAuthed(requestBody)
    }

    fun customHttpDelete(
        apiSubPath: String,
        customizer: (HttpRequest<Buffer>) -> HttpRequest<Buffer> = { it }
    ): Single<HttpResponse<Buffer>> {
        return httpClient.obeliskRequest(apiSubPath, HttpMethod.DELETE)
            .apply { customizer.invoke(this) }
            .rxSendAuthed()
    }


    fun queryEvents(q: EventsQuery): Single<PagedResult<MetricEvent>> {
        return httpClient.obeliskRequest("/data/query/events")
            .rxSendJsonAuthed(q)
            .pagedResultOf(MetricEvent::class.java)
    }

    fun queryStats(q: StatsQuery): Single<PagedResult<MetricStat>> {
        return httpClient.obeliskRequest("/data/query/stats")
            .rxSendJsonAuthed(q)
            .pagedResultOf(MetricStat::class.java)
    }

    fun closeStream(streamId: String): Completable {
        val closeMutation = """
            mutation {
              onStream(id: "$streamId") {
                endSession {
                  responseCode
                }
              }
            }
        """.trimIndent()
        val statusQuery = """
            {
              me {
                activeStream(id: "$streamId") {
                  clientConnected
                }
              }
            }
        """.trimIndent()
        return queryCatalog(closeMutation)
            .flatMapCompletable {
                queryCatalog(statusQuery)
                    .flatMapCompletable { result ->
                        val clientConnected =
                            result.getJsonObject("data")?.getJsonObject("me")?.getJsonObject("activeStream")
                                ?.getBoolean("clientConnected") ?: false
                        if (!clientConnected) Completable.complete() else Completable.error(IllegalStateException("End session operation was not completed!"))
                    }
                    .retryWhen(RetryWhen.delay(500, TimeUnit.MILLISECONDS).maxRetries(10).build())
            }
    }

    fun openStream(streamId: String, receiveBacklog: Boolean = false): Flowable<MetricEvent> {
        return getToken().flatMapPublisher { token ->
            val requestOptions =
                RequestOptions().setAbsoluteURI("${options.safeApiUrl}/data/streams/$streamId?receiveBacklog=$receiveBacklog")
            rawClient.rxRequest(requestOptions)
                .flatMap { request ->
                    request
                        .putHeader(HttpHeaders.ACCEPT, "text/event-stream")
                        .putHeader(HttpHeaders.AUTHORIZATION, "Bearer $token")

                    if (options.virtualHost != null) {
                        request.putHeader(HttpHeaders.HOST, options.virtualHost)
                    }
                    request.rxSend()
                }
                .flatMapPublisher { resp ->
                    if (resp.statusCode() == 200) {
                        RecordParser.newDelimited("\n\n", resp).toFlowable()
                            .map { it.toString(SSE_TEXT_ENCODING) }
                            .filter { msg -> !(msg.startsWith("comment:") || msg.startsWith(":")) }
                            .map { msg ->
                                //logger.warn { "Receiving SSE msg: $msg" }
                                msg.split("\n").find { it.startsWith("data:") }?.removePrefix("data:")
                                    ?.let { Json.decodeValue(it, MetricEvent::class.java) }!!
                            }
                            .doOnError { resp.netSocket().close { } }
                    } else {
                        resp.rxBody()
                            .flatMapPublisher {
                                Flowable.error(
                                    OblxException(
                                        resp.statusCode(),
                                        it.toString()
                                    )
                                )
                            }
                    }
                }
        }
    }

    private fun Single<HttpResponse<Buffer>>.emptyResult(): Completable {
        return this
            .map { escalateHttpError(it) }
            .ignoreElement()
    }

    private fun Single<HttpResponse<Buffer>>.json(): Single<JsonObject> {
        return this
            .map { escalateHttpError(it) }
            .map { it.bodyAsJsonObject() }
    }

    private fun <T> Single<HttpResponse<Buffer>>.pagedResultOf(type: Class<T>): Single<PagedResult<T>> {
        return this
            .map { escalateHttpError(it) }
            .map {
                DatabindCodec.mapper().readValue(
                    it.bodyAsString(),
                    DatabindCodec.mapper().typeFactory.constructParametricType(PagedResult::class.java, type)
                )
            }
    }

    private fun escalateHttpError(resp: HttpResponse<Buffer>): HttpResponse<Buffer> {
        if (resp.statusCode() < 200 || resp.statusCode() >= 400) {
            val error = try {
                resp.bodyAsJson(OblxException::class.java)
            } catch (t: Throwable) {
                OblxException(resp.statusCode(), resp.bodyAsString())
            }
            throw error
        } else {
            return resp
        }
    }
}
