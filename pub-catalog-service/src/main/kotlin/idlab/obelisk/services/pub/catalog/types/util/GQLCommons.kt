package idlab.obelisk.services.pub.catalog.types.util

import graphql.schema.DataFetchingEnvironment
import graphql.schema.idl.TypeRuntimeWiring
import hu.akarnokd.rxjava2.interop.MaybeInterop
import hu.akarnokd.rxjava2.interop.SingleInterop
import idlab.obelisk.definitions.*
import idlab.obelisk.definitions.catalog.*
import idlab.obelisk.definitions.catalog.codegen.*
import idlab.obelisk.definitions.control.ExportEvent
import idlab.obelisk.definitions.data.*
import idlab.obelisk.pulsar.utils.rxSend
import idlab.obelisk.services.pub.catalog.impl.*
import idlab.obelisk.utils.service.http.AuthorizationException
import idlab.obelisk.utils.service.http.UnauthorizedException
import idlab.obelisk.utils.service.utils.applyToken
import idlab.obelisk.utils.service.utils.unpage
import io.reactivex.Completable
import io.reactivex.Maybe
import io.reactivex.Single
import io.reactivex.exceptions.CompositeException
import io.vertx.core.json.JsonObject
import io.vertx.ext.web.impl.RoutingContextImpl
import io.vertx.reactivex.ext.web.RoutingContext
import org.redisson.api.RMapCacheRx
import org.redisson.api.RedissonClient
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage
import kotlin.math.min
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.full.memberFunctions
import kotlin.reflect.jvm.jvmErasure

@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
/**
 * Marks a class as a GraphQL Type resolver (for non-pojo properties)
 *
 * @param name The name of the GraphQL type (if not specified, the class name is used!)
 */
annotation class GQLType(val name: String = "")

@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
/**
 * Marks a member function as a GraphQL field fetcher. The member function MUST take a single argument (DataFetchingEnvironment) and return a CompletionStage).
 *
 * @param field The name of the field (according to the schema). If not specified, the name of the method is used!
 */
annotation class GQLFetcher(val field: String = "")

internal fun Any.wiring(): TypeRuntimeWiring {
    val gqlClass = this::class
    return gqlClass.findAnnotation<GQLType>()?.let { gqlType ->
        val wiring =
            TypeRuntimeWiring.newTypeWiring(if (gqlType.name.isNotEmpty()) gqlType.name else gqlClass.simpleName)
        gqlClass.memberFunctions
            .filter { it.annotations.any { it is GQLFetcher } }
            .forEach { mappingFunction ->
                val fieldDef = mappingFunction.findAnnotation<GQLFetcher>()!!
                val fieldName = if (fieldDef.field.isNotEmpty()) fieldDef.field else mappingFunction.name
                if (mappingFunction.parameters.size != 2 || mappingFunction.parameters[1].type.jvmErasure != DataFetchingEnvironment::class) {
                    throw IllegalArgumentException("Resolving function '$fieldName' for GraphQL Type ${gqlClass.simpleName} is not valid: it must take an instance of DataFetchingEnvironment as its single argument!")
                }
                wiring.dataFetcher(fieldName) { env -> mappingFunction.call(this, env) }
            }
        wiring.build()
    } ?: throw IllegalArgumentException("Class ${gqlClass.simpleName} is not a compatible GraphQL Type class!")
}

internal fun RedissonClient.getInvites(): RMapCacheRx<String, String> {
    return this.rxJava().getMapCache<String, String>(REDIS_INVITES_KEY)
}

internal fun RedissonClient.getTeamInvites(): RMapCacheRx<String, String> {
    return this.rxJava().getMapCache<String, String>(REDIS_TEAM_INVITES_KEY)
}

internal fun redisInviteKeyOrPattern(datasetId: String, inviteId: String = "*"): String {
    return "${datasetId}_${inviteId}"
}

internal const val REDIS_ITERATOR_DEFAULT_BATCH_SIZE = 10
internal const val REDIS_INVITES_KEY = "INVITES"
internal const val REDIS_TEAM_INVITES_KEY = "TEAM_INVITES"

// Default is 48 hours
internal const val DEFAULT_INVITE_TTL_MINUTES = 48 * 60

abstract class Operations(protected val accessManager: AccessManager) {

    /**
     * If the authenticated user has permission to access the current context, the callback handler is executed and its result returned as a CompletionStage.
     */
    fun <T> withAccess(env: DataFetchingEnvironment, handler: (Token) -> Single<T>): CompletionStage<T> {
        return try {
            Single.just(checkAccess(env))
                .flatMap(handler)
                .onErrorResumeNext { convertCompositeError(it) }
                .to(SingleInterop.get())
        } catch (t: Throwable) {
            CompletableFuture.failedStage<T>(t)
        }
    }

    /**
     * If the authenticated user has permission to access the current context, the callback handler is executed and its result returned as a CompletionStage.
     */
    fun <T> withAccessMaybe(env: DataFetchingEnvironment, handler: (Token) -> Maybe<T>): CompletionStage<T> {
        return try {
            Single.just(checkAccess(env))
                .flatMapMaybe(handler)
                .onErrorResumeNext { err: Throwable -> convertCompositeErrorMaybe(err) }
                .to(MaybeInterop.get())
        } catch (t: Throwable) {
            CompletableFuture.failedStage<T>(t)
        }
    }

    /**
     * Utility method resulting in a DataLoader call, takes care of deadlock issues that would occur when using the normal withAccess
     */
    fun <T> withAccessLoad(env: DataFetchingEnvironment, handler: (Token) -> CompletableFuture<T>): CompletionStage<T> {
        return try {
            handler.invoke(checkAccess(env))
        } catch (t: Throwable) {
            CompletableFuture.failedStage<T>(t)
        }
    }

    data class OptionalToken(val token: Token? = null)

    fun <T> withOptionalAccess(
        env: DataFetchingEnvironment,
        handler: (OptionalToken) -> Single<T>
    ): CompletionStage<T> {
        val ctx =
            RoutingContext(env.getContext<RoutingContextImpl>()) // Workaround for Vertx not providing a Reactive RoutingContext here...
        return accessManager.getToken(ctx.request())
            .map { OptionalToken(it) }
            .onErrorResumeNext { err ->
                if (err is UnauthorizedException) {
                    Single.just(OptionalToken())
                } else {
                    Single.error { err }
                }
            }
            .flatMap(handler)
            .onErrorResumeNext { convertCompositeError(it) }
            .to(SingleInterop.get())
    }

    fun <T> withOptionalAccessMaybe(
        env: DataFetchingEnvironment,
        handler: (OptionalToken) -> Maybe<T>
    ): CompletionStage<T> {
        val ctx =
            RoutingContext(env.getContext<RoutingContextImpl>()) // Workaround for Vertx not providing a Reactive RoutingContext here...
        return accessManager.getToken(ctx.request())
            .map { OptionalToken(it) }
            .onErrorResumeNext { err ->
                if (err is UnauthorizedException) {
                    Single.just(OptionalToken())
                } else {
                    Single.error { err }
                }
            }
            .flatMapMaybe(handler)
            .onErrorResumeNext { err: Throwable -> convertCompositeErrorMaybe(err) }
            .to(MaybeInterop.get())
    }

    private fun <T> convertCompositeError(err: Throwable): Single<T> {
        return if (err is CompositeException) {
            Single.error { CompositeExceptionWrapper(err) }
        } else {
            Single.error { err }
        }
    }

    private fun <T> convertCompositeErrorMaybe(err: Throwable): Maybe<T> {
        return if (err is CompositeException) {
            Maybe.error { CompositeExceptionWrapper(err) }
        } else {
            Maybe.error { err }
        }
    }

    protected fun limitFrom(env: DataFetchingEnvironment): Int {
        return env.getArgument("limit", DEFAULT_PAGE_SIZE)
    }

    protected fun cursorFrom(env: DataFetchingEnvironment): String? {
        return env.getArgumentOrNull("cursor")
    }

    private fun <T> DataFetchingEnvironment.getArgument(name: String, defaultValue: T): T {
        return if (this.containsArgument(name)) {
            this.getArgument(name)
        } else {
            defaultValue
        }
    }

    private fun <T> DataFetchingEnvironment.getArgumentOrNull(name: String): T? {
        return if (this.containsArgument(name)) this.getArgument(name) else null
    }

    protected fun combineThingsInfo(
        datasetId: String,
        fromRawData: List<MetaData>,
        fromMeta: List<Thing>
    ): List<Thing> {
        val metaMap = fromMeta.associateBy { it.sourceId }
        return fromRawData.mapNotNull { it.source }
            .map { metaMap[it] ?: Thing(sourceId = it, datasetId = datasetId) }
    }

    protected fun combineMetricsInfo(
        datasetId: String,
        fromRawData: List<MetaData>,
        fromMeta: List<Metric>
    ): List<Metric> {
        val metaMap = fromMeta.associateBy { it.name }
        return fromRawData.mapNotNull { it.metricName() }
            .map { metaMap[it] ?: Metric(name = it, datasetId = datasetId) }
    }

    protected fun getResultingTSForQuery(dataStore: DataStore, query: EventsQuery): Maybe<Long> {
        return dataStore.getEvents(query)
            .flatMapMaybe { results ->
                if (results.items.isNotEmpty()) {
                    Maybe.just(results.items.first().timestamp)
                } else {
                    Maybe.empty<Long>()
                }
            }
    }

    /**
     * Calculates an update instance based on the GraphQL input env.
     * Returns a pair of the update instance + a set of fields set explicitly to null.
     */
    protected inline fun <reified U, reified F : Enum<F>> calcUpdate(
        env: DataFetchingEnvironment,
    ): Pair<U, Set<F>> {
        val inputAsMap = env.getArgument<Map<String, Any?>>("input")
        // Access control
        val unAuthorizedUpdate = inputAsMap.keys.find { !checkUpdateFieldAllowed(env, it) }
        if (unAuthorizedUpdate != null) {
            throw AuthorizationException("You are not allowed to update the field '$unAuthorizedUpdate'")
        } else {
            val input = JsonObject(inputAsMap).mapTo(U::class.java)
            return Pair(
                input,
                inputAsMap.filter { it.value == null }.keys.mapNotNull { key -> enumValues<F>().find { it.toString() == key } }
                    .toSet()
            )
        }
    }

    /**
     * Invalidate all Users of a Team (is transitive, so the clients of the individual users are also invalidated)
     */
    protected fun invalidateTeam(teamId: String, metaStore: MetaStore): Completable {
        return unpage { cursor ->
            metaStore.queryUsers(
                filter = Eq(
                    Field(
                        UserField.TEAM_MEMBERSHIPS,
                        TeamMembershipField.TEAM_ID
                    ), teamId
                ), cursor = cursor
            )
        }.map { it.id!! }.toList()
            .flatMapCompletable { userIds ->
                val userIdsSet = userIds.toSet()
                unpage { cursor ->
                    metaStore.queryClients(
                        filter = Or(
                            Eq(ClientField.TEAM_ID, teamId),
                            In(ClientField.USER_ID, userIdsSet)
                        ), cursor = cursor
                    )
                }.map { it.id!! }.toList()
                    .flatMapCompletable { clientIds ->
                        accessManager.invalidateSessions(userIds = userIdsSet, clientIds = clientIds.toSet())
                    }
            }
    }

    /**
     * Invalidate a User and all his/her clients
     */
    protected fun invalidateUser(userId: String, metaStore: MetaStore): Completable {
        return unpage { cursor ->
            metaStore.queryClients(
                filter = Eq(ClientField.USER_ID, userId),
                cursor = cursor
            )
        }.map { it.id!! }
            .toList()
            .flatMapCompletable { clientIds ->
                accessManager.invalidateSessions(
                    userIds = setOf(userId),
                    clientIds = clientIds.toSet()
                )
            }
    }

    protected fun invalidateRole(roleId: String, metaStore: MetaStore): Completable {
        return unpage { cursor ->
            metaStore.queryTeams(
                Eq(
                    Field(
                        TeamField.DATASET_MEMBERSHIPS,
                        DatasetMembershipField.ASSIGNED_ROLE_IDS
                    ), roleId
                ), cursor = cursor
            )
        }.map { it.id!! }
            .toList()
            .flatMapCompletable { teamIds ->
                unpage { cursor ->
                    metaStore.queryUsers(
                        filter = Or(
                            In(Field(UserField.TEAM_MEMBERSHIPS, TeamMembershipField.TEAM_ID), teamIds.toSet()),
                            Eq(Field(UserField.DATASET_MEMBERSHIPS, DatasetMembershipField.ASSIGNED_ROLE_IDS), roleId)
                        ), cursor = cursor
                    )
                }.map { it.id!! }.toList()
                    .flatMapCompletable { userIds ->
                        val userIdsSet = userIds.toSet()
                        unpage { cursor ->
                            metaStore.queryClients(
                                filter = Or(
                                    In(ClientField.TEAM_ID, teamIds.toSet()),
                                    In(ClientField.USER_ID, userIdsSet)
                                ), cursor = cursor
                            )
                        }.map { it.id!! }.toList()
                            .flatMapCompletable { clientIds ->
                                accessManager.invalidateSessions(userIds = userIdsSet, clientIds = clientIds.toSet())
                            }
                    }
            }
    }

    protected fun createDataExport(
        dataStore: DataStore,
        metaStore: MetaStore,
        pulsarConnections: PulsarConnections,
        token: Token,
        input: CreateExportInput,
        teamId: String? = null
    ): Single<DataExport> {
        val maxDataExports = token.usageLimit.values[UsageLimitId.maxDataExports] ?: 0
        val maxDataExportRecords = token.usageLimit.values[UsageLimitId.maxDataExportRecords] ?: 0
        // Check if the user can create additional exports
        return metaStore.countDataExports(filter = Eq(DataExportField.USER_ID, token.user.id!!))
            .flatMap { count ->
                if ((count + 1) <= maxDataExports) {
                    val export = DataExport(
                        userId = token.user.id!!,
                        teamId = teamId,
                        name = input.name,
                        timestampPrecision = TimestampPrecision.from(input.timestampPrecision),
                        dataRange = input.dataRange.parse(),
                        fields = if (input.fields.isEmpty()) listOf(
                            EventField.metric,
                            EventField.source,
                            EventField.value
                        ) else input.fields,
                        filter = input.filter,
                        from = input.from,
                        to = input.to,
                        limit = min(maxDataExportRecords.toInt(), input.limit ?: Int.MAX_VALUE)
                    ).applyToken(token)

                    // Count the number of events that this export would produce (at this time) for the record count estimate.
                    dataStore
                        .getStats(
                            StatsQuery(
                                dataRange = export.dataRange,
                                fields = listOf(StatsField.count),
                                filter = export.filter,
                                from = export.from,
                                to = export.to
                            )
                        )
                        .flatMap { stats ->
                            // Set result as recordsEstimate
                            export.status.recordsEstimate =
                                min(stats.items.firstOrNull()?.count ?: 0L, export.limit?.toLong() ?: 0L)
                            metaStore.createDataExport(export)
                        }
                        .flatMap { exportId ->
                            // Send event to trigger the export process
                            pulsarConnections.exportTriggerProducer.rxSend(ExportEvent(exportId)).map { exportId }
                        }
                        .flatMapMaybe(metaStore::getDataExport)
                        .toSingle()
                } else {
                    Single.error { IllegalStateException("Cannot exceed the maximum amount of DataExports for this User ($maxDataExports)!") }
                }
            }
    }

    protected fun createDataStream(
        metaStore: MetaStore,
        pulsarConnections: PulsarConnections,
        token: Token,
        input: CreateStreamInput,
        teamId: String? = null
    ): Single<DataStream> {
        val maxDataStreams = token.usageLimit.values[UsageLimitId.maxDataStreams] ?: 0

        // Check if the user can create another stream
        return metaStore.countDataStreams(filter = Eq(DataStreamField.USER_ID, token.user.id!!))
            .flatMap { count ->
                if ((count + 1) <= maxDataStreams) {
                    // Create the stream
                    val dataStream = DataStream(
                        userId = token.user.id!!,
                        teamId = teamId,
                        name = input.name,
                        timestampPrecision = TimestampPrecision.from(input.timestampPrecision),
                        dataRange = input.dataRange.parse(),
                        fields = input.fields.ifEmpty {
                            listOf(
                                EventField.metric,
                                EventField.source,
                                EventField.value
                            )
                        },
                        filter = input.filter
                    ).applyToken(token)
                    metaStore
                        .createDataStream(dataStream)
                        .flatMapMaybe(metaStore::getDataStream)
                        .toSingle()
                } else {
                    Single.error { IllegalStateException("Cannot exceed the maximum amount of DataStreams for this User ($maxDataStreams)!") }
                }
            }
    }
}

class CompositeExceptionWrapper(compositeException: CompositeException) : RuntimeException(
    "${compositeException.size()} exceptions occurred! ${
        compositeException.exceptions.mapIndexed { i, t -> "${i + 1}) ${t.message}" }.joinToString()
    }"
)

fun Token.readFilterForDataset(datasetId: String): FilterExpression {
    return this.grants[datasetId]?.readFilter ?: SELECT_ALL
}

internal fun EventsQuery.restrictDeleteToContext(userId: String, clientId: String? = null): EventsQuery {
    /**
     * Make sure only events produced by the User or his / her client are deleted
     */
    return this.copy(
        filter = And(listOfNotNull(
            this.filter,
            Eq(Field(EventField.producer.toString(), "userId"), userId),
            clientId?.let { Eq(Field(EventField.producer.toString(), "clientId"), it) }
        ))
    )
}
