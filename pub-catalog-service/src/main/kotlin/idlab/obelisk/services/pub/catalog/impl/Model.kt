package idlab.obelisk.services.pub.catalog.impl

import com.fasterxml.jackson.annotation.JsonInclude
import graphql.schema.DataFetchingEnvironment
import idlab.obelisk.definitions.*
import idlab.obelisk.definitions.catalog.*
import idlab.obelisk.definitions.data.EventsQuery
import idlab.obelisk.definitions.data.MetaData
import idlab.obelisk.definitions.framework.MonitoringDuration
import idlab.obelisk.definitions.framework.MonitoringDurationUnit
import idlab.obelisk.definitions.framework.TimedFloat
import io.reactivex.Single
import io.vertx.core.json.JsonObject
import mu.KotlinLogging
import java.util.*
import java.util.concurrent.TimeUnit

val logger = KotlinLogging.logger { }

const val DEFAULT_PAGE_SIZE = 25
const val ADMIN_GROUP_ID = "2"
const val ALL_USERS_ID = "3"
val allMetricsWildcard = MetricType.values().map { MetricName("*", it) }

data class MetaStats(val datasetId: String, val metricInfo: List<MetaData>)
enum class RateSeriesMode(val offsetMs: Long, val monitoringDuration: MonitoringDuration) {
    //Return a window of an hour with a step of 1 minute
    LAST_HOUR(1 * 60 * 60 * 1000L, MonitoringDuration(1, MonitoringDurationUnit.MINUTES)),

    // Return a window of a day with a step of 20 minutes
    LAST_DAY(24 * 60 * 60 * 1000L, MonitoringDuration(20, MonitoringDurationUnit.MINUTES))
}

data class Origin(val datasetId: String, val producer: ProducerType)

enum class ResponseCode {
    SUCCESS, BAD_REQUEST, NOT_FOUND, NOT_AUTHORIZED, SERVER_ERROR
}

data class Response<T>(
    val responseCode: ResponseCode = ResponseCode.SUCCESS,
    val message: String? = null,
    val item: T? = null
)

fun <T> errorResponse(env: DataFetchingEnvironment, err: Throwable): Response<T> {
    return Response(responseCode = when (err) {
        is NoSuchElementException -> ResponseCode.NOT_FOUND
        is IllegalArgumentException -> ResponseCode.BAD_REQUEST
        else -> {
            logger.warn(err) { "Error while fetching GraphQL field ${env.executionStepInfo.path}/${env.field.name}." }
            ResponseCode.SERVER_ERROR
        }
    }, message = err.message)
}

internal fun <T> DataFetchingEnvironment.parseInput(type: Class<T>): T {
    return JsonObject(this.getArgument<Map<String, Any>>("input")).mapTo(type)
}

internal fun <T> DataFetchingEnvironment.getSourceIfTypeMatches(type: Class<T>): T? {
    val source = this.getSource<Any>()
    return source?.takeIf { it::class.java == type }?.let { it as T }
}

data class TeamUser(val teamId: String, val userId: String)

data class Membership(
    val userId: String? = null,
    val teamId: String? = null,
    val datasetId: String,
    val roleIds: Set<String> = setOf()
)

data class CreateDatasetInput(
    val name: String,
    val description: String? = null,
    val datasetOwnerId: String? = null
)

data class CreateRoleInput(
    val name: String,
    val description: String? = null,
    val permissions: List<Permission>,
    val readFilter: FilterExpression = SELECT_ALL
)

data class CreateClientInput(
    val name: String,
    val confidential: Boolean = false,
    val onBehalfOfUser: Boolean = true,
    val properties: Map<String, Object> = emptyMap(),
    val restrictions: List<ClientRestrictionInput> = emptyList(),
    val scope: List<Permission> = Permission.readOnly().toList(),
    val redirectURIs: List<String> = emptyList()
)

class ClientRestrictionInput(datasetId: String, permissions: Set<Permission>) :
    ClientRestriction(datasetId, permissions)

data class CreateStreamInput(
    val name: String,
    val dataRange: DataRangeInput,
    val timestampPrecision: TimeUnit = TimeUnit.MILLISECONDS,
    val fields: List<EventField> = listOf(EventField.metric, EventField.source, EventField.value),
    val filter: FilterExpression = SELECT_ALL
)

data class CreateExportInput(
    val name: String? = null,
    val dataRange: DataRangeInput,
    val timestampPrecision: TimeUnit = TimeUnit.MILLISECONDS,
    val fields: List<EventField> = listOf(EventField.metric, EventField.source, EventField.value),
    val filter: FilterExpression = SELECT_ALL,
    val from: Long? = null,
    val to: Long? = null,
    val limit: Int? = null
)

data class CreateTeamInput(
    val name: String,
    val description: String? = null,
    val teamOwnerId: String? = null
)

class DataRangeInput(val datasets: List<String>, val metrics: List<MetricName> = MetricName.wildcard()) {

    fun parse(): DataRange {
        return DataRange(datasets, if (metrics.isEmpty()) MetricName.wildcard() else metrics)
    }

}

data class RequestAccessInput(
    val type: List<Permission> = emptyList(),
    val message: String? = null
)

data class GrantAccessInput(
    val subjectId: String,
    val permissions: List<Permission>,
    val readFilter: FilterExpression = SELECT_ALL
)

data class UsagePlanInput(
    val name: String,
    val description: String? = null,
    val maxUsers: Int,
    val userUsageLimitId: String? = null,
    val maxClients: Int,
    val clientUsageLimitId: String? = null
)

data class UsageLimitInput(
    val name: String,
    val description: String? = null,
    val values: Map<UsageLimitId, Int>
)

data class InviteInput(
    val roleIds: List<String> = listOf(),
    val disallowTeams: Boolean = false
)

@JsonInclude(JsonInclude.Include.NON_EMPTY)
data class Invite(
    val id: String = UUID.randomUUID().toString(),
    val datasetId: String,
    val roleIds: Set<String> = setOf(),
    val disallowTeams: Boolean = false
)

data class InviteCursor(val offset: Int) {
    constructor(cursor: String) : this(String(Base64.getDecoder().decode(cursor)).toInt())

    fun asCursor(): String {
        return Base64.getEncoder().encodeToString("$offset".toByteArray())
    }
}

@JsonInclude(JsonInclude.Include.NON_EMPTY)
data class TeamInvite(
    val id: String = UUID.randomUUID().toString(),
    val teamId: String,
)

data class AssignRolesInput(val userId: String, val roleIds: List<String>? = null)
data class AssignTeamRolesInput(val teamId: String, val roleIds: List<String>? = null)

data class GraphQLPage<T>(val items: List<T>, val cursor: String? = null, val countSupplier: () -> Single<Long>)

data class DataRemovalRequest(
    val dataRange: DataRangeInput,
    val filter: FilterExpression = SELECT_ALL,
    val from: Long?,
    val to: Long?
) {

    fun toQuery(): EventsQuery {
        return EventsQuery(
            dataRange = dataRange.parse(),
            from = from,
            to = to,
            filter = filter
        )
    }

}

typealias TimeSeries = List<TimedFloat>
