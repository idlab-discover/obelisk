package idlab.obelisk.services.pub.ngsi.impl.model

import idlab.obelisk.definitions.data.Location
import idlab.obelisk.definitions.data.Producer
import idlab.obelisk.services.pub.ngsi.Constants
import idlab.obelisk.services.pub.ngsi.impl.utils.ngsiDateTimeFrom
import io.vertx.core.json.JsonArray
import io.vertx.core.json.JsonObject
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit

val dateTimeFormatter = DateTimeFormatter.ISO_DATE_TIME

/**
 * Util class that acts as a bridge between NgsiAttribute and Obelisk metric event
 */
data class NgsiAttribute(val producer: Producer, val raw: JsonObject) {

    init {
        if (!raw.containsKey(Constants.FQ_OBSERVED_AT)) {
            setTimeFieldToNow(TimeField.observedAt)
        }

        if (!raw.containsKey(Constants.FQ_CREATED_AT)) {
            setTimeFieldToNow(TimeField.createdAt)
        }
    }

    enum class TimeField(val fqAttributeName: String) {
        observedAt(Constants.FQ_OBSERVED_AT), createdAt(Constants.FQ_CREATED_AT), modifiedAt(Constants.FQ_MODIFIED_AT)
    }

    fun getTimestampMus(timeField: TimeField = TimeField.observedAt): Long {
        //observedAt , createdAt, modifiedAt
        return TimeUnit.MICROSECONDS.convert(Instant.parse(
                getProp(timeField.fqAttributeName, Instant.now().atOffset(ZoneOffset.UTC).format(dateTimeFormatter))
        ).toEpochMilli(), TimeUnit.MILLISECONDS)
    }

    fun getTimestampMs(timeField: TimeField = TimeField.observedAt): Long {
        return TimeUnit.MILLISECONDS.convert(getTimestampMus(timeField), TimeUnit.MICROSECONDS)
    }

    fun getTimeField(timeField: TimeField = TimeField.observedAt): String? {
        return raw.getJsonArray(timeField.fqAttributeName)?.getJsonObject(0)?.getString(Constants.FQ_VALUE)
    }

    fun setTimeField(timeField: TimeField = TimeField.observedAt, dateTime: String) {
        raw.put(timeField.fqAttributeName, ngsiDateTimeFrom(dateTime))
    }

    fun setTimeFieldToNow(timeField: TimeField = TimeField.observedAt) {
        setTimeField(timeField, Instant.now().atOffset(ZoneOffset.UTC).format(dateTimeFormatter))
    }

    fun getLocation(): Location {
        // TODO
        return Location(0.0, 0.0)
    }

    fun getNgsiDatasetId(): String? {
        return raw.getJsonArray(Constants.FQ_DATASET_ID)
                ?.firstOrNull()
                ?.takeIf { it is JsonObject }
                ?.let { it as JsonObject }
                ?.getString(Constants.FQ_ENTITY_ID)
    }

    fun setNgsiDatasetId(datasetId: String) {
        raw.put(Constants.FQ_DATASET_ID, JsonArray().add(JsonObject().put(Constants.FQ_ENTITY_ID, datasetId)))
    }

    private fun <T> getProp(name: String, defaulVal: T): T {
        return raw.getJsonArray(name)
                ?.firstOrNull()
                ?.takeIf { it is JsonObject }
                ?.let { it as JsonObject }
                ?.getValue(Constants.FQ_VALUE)
                ?.let { it as T }
                ?: defaulVal
    }

}