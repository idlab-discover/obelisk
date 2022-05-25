package idlab.obelisk.definitions.framework

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import kotlin.reflect.KClass

// Environment variable names that should be available globally
const val ENV_GOOGLE_IDP_CLIENT_ID = "GOOGLE_IDP_CLIENT_ID"
const val ENV_GOOGLE_IDP_CLIENT_SECRET = "GOOGLE_IDP_CLIENT_SECRET"
const val ENV_GOOGLE_IDP_ISSUER_SITE = "GOOGLE_IDP_ISSUER_SITE"

@JsonIgnoreProperties(ignoreUnknown = true)
data class OblxConfig(
    @JsonProperty("HTTP_PORT")
    val httpPort: Int = 8080,
    @JsonProperty(ENV_METRICS_PORT)
    val metricsPort: Int = 8081,
    @JsonProperty("AUTH_PUBLIC_URI")
    val authPublicUri: String = "http://localhost:8080",
    @JsonProperty("AUTH_ADMIN_USER")
    val authAdminUser: String = "admin",
    @JsonProperty("AUTH_ADMIN_PASSWORD")
    val authAdminPassword: String = "",
    @JsonProperty("ADMINS_GROUP_ID")
    val adminsGroupId: String = "admins",
    @JsonProperty("ALL_USERS_GROUP_ID")
    val allUsersGroupId: String = "allusers",
    @JsonProperty("WEB_CLIENTS_CLIENT_ID")
    val webClientsClientId: String = "Obelisk-web-clients",
    @JsonProperty("PULSAR_CONNECTION_URI")
    val pulsarServiceUrl: String = "pulsar://localhost:6650",
    @JsonProperty("PULSAR_ADMIN_API_URI")
    val pulsarAdminApiUri: String = "http://localhost:8086",
    @JsonProperty("PULSAR_METRIC_EVENTS_TOPIC")
    val pulsarMetricEventsTopic: String = "public/oblx-core/metric_events",
    @JsonProperty("PULSAR_DATASET_TOPICS_PREFIX")
    val pulsarDatasetTopicsPrefix: String = "public/oblx-ds/metric_events_ds_",
    @JsonProperty("PULSAR_STORAGE_SINK_SUBSCRIBER")
    val pulsarStorageSinkSubscriber: String = "storage_sink",
    @JsonProperty("PULSAR_DATASET_STREAMER_SUBSCRIBER")
    val pulsarDatasetStreamerSubscriber: String = "dataset_streamer",
    @JsonProperty(PULSAR_LISTENER_THREADS)
    val pulsarListenerThreads: Int = 2,
    @JsonProperty("MONGO_CONNECTION_URI")
    val mongoConnectionUri: String = "mongodb://localhost:27017",
    @JsonProperty("MONGO_DB_NAME")
    val mongoDbName: String = "obelisk",
    @JsonProperty("REDIS_CONNECTION_URI")
    val redisConnectionUri: String = "redis://localhost:6379",
    @JsonProperty("CLICKHOUSE_CONNECTION_URI")
    val chConnectionUri: String = "jdbc:clickhouse://localhost:8123/default",
    @JsonProperty("CLICKHOUSE_CLUSTER_NAME")
    val chClusterName: String = "", // Empty String means CH is not running in cluster mode!
    @JsonProperty("CLICKHOUSE_MIN_QUERY_TIMEOUT_SECONDS")
    val chMinTimeoutSeconds: Int = 10,
    @JsonProperty("CLICKHOUSE_MAX_QUERY_TIMEOUT_SECONDS")
    val chMaxTimeoutSeconds: Int = 20,
    @JsonProperty("GUBERNATOR_CONNECTION_URI")
    val gubernatorConnectionUri: String = "http://localhost:9080",
    @JsonProperty("SMTP_CONNECTION_URI")
    val smtpConnectionUri: String = "smtp://user:password@smtp.server.com:587",
    @JsonProperty("SMTP_USE_TLS")
    val smtpUseTls: Boolean = true,
    @JsonProperty("PROMETHEUS_URI")
    val prometheusUri: String = "http://localhost:9090" // Empty String means no Prometheus metric data is available.
) {
    companion object {

        @JsonIgnore
        const val ENV_METRICS_PORT = "METRICS_PORT"

        @JsonIgnore
        const val HTTP_BASE_PATH_PROP = "HTTP_BASE_PATH"

        @JsonIgnore
        const val PULSAR_LISTENER_THREADS = "PULSAR_LISTENER_THREADS"
    }

    @JsonIgnore
    fun getInteger(propertyName: String): Int? {
        return System.getenv(propertyName)?.toIntOrNull()
    }

    @JsonIgnore
    fun getInteger(propertyName: String, default: Int): Int {
        return getInteger(propertyName) ?: default
    }

    @JsonIgnore
    fun getLong(propertyName: String): Long? {
        return System.getenv(propertyName)?.toLongOrNull()
    }

    @JsonIgnore
    fun getLong(propertyName: String, default: Long): Long {
        return getLong(propertyName) ?: default
    }

    @JsonIgnore
    fun getDouble(propertyName: String): Double? {
        return System.getenv(propertyName)?.toDoubleOrNull()
    }

    @JsonIgnore
    fun getDouble(propertyName: String, default: Double): Double {
        return getDouble(propertyName) ?: default
    }

    @JsonIgnore
    fun getString(propertyName: String): String? {
        return System.getenv(propertyName)
    }

    @JsonIgnore
    fun getString(propertyName: String, default: String): String {
        return getString(propertyName) ?: default
    }

    @JsonIgnore
    fun getBoolean(propertyName: String): Boolean? {
        return System.getenv(propertyName)?.toBoolean()
    }

    @JsonIgnore
    fun getBoolean(propertyName: String, default: Boolean): Boolean {
        return getBoolean(propertyName) ?: default
    }

    fun <T : Enum<T>> getEnum(enumType: Class<T>, propertyName: String): T? {
        return System.getenv(propertyName)
            ?.let { propVal -> enumType.enumConstants.firstOrNull() { it.name == propVal } }
    }

    fun <T : Enum<T>> getEnum(enumType: Class<T>, propertyName: String, default: T): T {
        return getEnum(enumType, propertyName) ?: default
    }

    /**
     * Returns the value of the HOSTNAME env variable (or 'localhost' if it is not set)
     */
    @JsonIgnore
    fun hostname(): String {
        return getString("HOSTNAME") ?: "localhost"
    }

    @JsonIgnore
    fun pulsarDatasetTopic(datasetId: String): String {
        return "${pulsarDatasetTopicsPrefix}${datasetId}"
    }

    @JsonIgnore
    fun storeOnlyMetricEventsTopic(): String {
        return "${pulsarMetricEventsTopic}_store_only"
    }
}
