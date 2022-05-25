package idlab.obelisk.services.pub.ngsi.impl.model

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonInclude
import idlab.obelisk.definitions.*
import idlab.obelisk.definitions.catalog.DataStream
import idlab.obelisk.definitions.catalog.Token
import idlab.obelisk.services.pub.ngsi.impl.utils.QueryParser
import idlab.obelisk.services.pub.ngsi.impl.utils.ngsiTypeFilter
import io.vertx.core.json.JsonObject

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
data class Subscription(
    val id: String,
    val name: String? = null,
    val type: String? = null, // Should be 'Subscription'
    val description: String? = null,
    val entities: List<EntityInfo>? = null,
    val watchedAttributes: List<String>? = null,
    val timeInterval: Int? = null,
    val q: String? = null,
    val geoQ: GeoQuery? = null,
    val csf: String? = null,
    val isActive: Boolean = true,
    val notification: NotificationParams,
    val expires: String? = null, //Datetime
    val throttling: Int? = null,
    val temporalQ: TemporalQuery? = null
) {

    companion object {

        /**
         * Convert an Obelisk dataStream to an NGSI-LD subscription
         */
        fun fromDataStream(dataStream: DataStream): Subscription {
            return JsonObject(dataStream.properties).mapTo(Subscription::class.java)
        }

    }

    /**
     * Create an Obelisk FilterExpression from this Subscription
     */
    private fun getFilter(ldContext: JsonObject): FilterExpression {
        val filters = listOfNotNull(
            entityFilter(ldContext),
            q?.let { QueryParser.parse(it, ldContext) },
            temporalQ?.let { QueryParser.parse(it) },
            temporalQ?.let { QueryParser.parse(it) }
        )
        return if (filters.size == 1) filters.first() else And(filters)
    }

    private fun entityFilter(ldContext: JsonObject): FilterExpression? {
        return entities?.map {
            val typeFilter = ngsiTypeFilter(listOf(it.type), ldContext)
            when {
                it.id != null -> {
                    And(Eq(EventField.source.toString(), it.id), typeFilter)
                }
                it.idPattern != null -> {
                    And(RegexMatches(EventField.source.toString(), it.idPattern), typeFilter)
                }
                else -> {
                    typeFilter
                }
            }
        }?.let { if (it.size == 1) it.first() else Or(it) }
    }

    /**
     * Derive an Obelisk DataStream from this Subscription
     */
    fun getDataStream(datasetId: String, token: Token, ldContext: JsonObject): DataStream {
        return DataStream(
            userId = token.user.id!!,
            name = name ?: "NGSI_SUBSCRIPTION_${System.currentTimeMillis()}",
            dataRange = DataRange(
                datasets = listOf(datasetId),
                metrics = if (q != null) MetricName.wildcard(MetricType.JSON) else MetricName.wildcard()
            ),
            timestampPrecision = TimestampPrecision.milliseconds,
            fields = listOf(
                EventField.dataset,
                EventField.metric,
                EventField.value,
                EventField.source,
                EventField.tags,
                EventField.tsReceived,
                EventField.location,
                EventField.producer
            ),
            filter = getFilter(ldContext),
            properties = JsonObject.mapFrom(this).map
        )
    }
}

data class SubscriptionInfo(var status: SubscriptionState, val notificationInfo: NotificationInfo)

@JsonInclude(JsonInclude.Include.NON_EMPTY)
data class NotificationInfo(
    var status: NotificationState? = null,
    var timesSent: Long? = null,
    var lastNotification: String? = null,
    var lastFailure: String? = null,
    var lastSuccess: String? = null
)

@JsonInclude(JsonInclude.Include.NON_EMPTY)
data class EntityInfo(val id: String? = null, val idPattern: String? = null, val type: String)

@JsonInclude(JsonInclude.Include.NON_EMPTY)
data class NotificationParams(
    val attributes: List<String>? = null,
    val format: Format = Format.normalized,
    val endpoint: Endpoint
)

@JsonInclude(JsonInclude.Include.NON_EMPTY)
data class Endpoint(val uri: String, val accept: String? = null) {
    // Hack to enable Pulsar Jackson shaded instance to work...
    internal constructor() : this("")
}

enum class Format {
    keyValues, normalized
}

enum class TimeRel {
    before, after, between
}

enum class SubscriptionState {
    active, paused, expired
}

enum class NotificationState {
    ok, failed
}

data class Notification(
    val subscriptionId: String,
    val datasetId: String,
    val entityIds: List<String>,
    val attributes: List<String>? = emptyList(),
    val endpoint: Endpoint
) {
    // Hack to enable Pulsar Jackson shaded instance to work...
    private constructor() : this(
        subscriptionId = "",
        datasetId = "",
        entityIds = emptyList<String>(),
        endpoint = Endpoint()
    )

}