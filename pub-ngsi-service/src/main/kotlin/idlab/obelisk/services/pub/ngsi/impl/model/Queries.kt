package idlab.obelisk.services.pub.ngsi.impl.model

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonRawValue
import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import com.fasterxml.jackson.databind.annotation.JsonSerialize
import idlab.obelisk.definitions.data.Location
import idlab.obelisk.services.pub.ngsi.impl.utils.GeoRelDeserializer
import idlab.obelisk.services.pub.ngsi.impl.utils.GeoRelSerializer
import io.vertx.core.json.JsonArray

@JsonInclude(JsonInclude.Include.NON_EMPTY)
data class GeoQuery(val geometry: Geometry, @JsonRawValue val coordinates: String, val georel: GeoRel, val geoproperty: String = "location") {

    fun parseCoordinates(): List<Location> {
        val envelope = JsonArray(coordinates)
        try {
            return expandCoordinatesEnvelope(envelope)
        } catch (t: Throwable) {
            throw IllegalArgumentException("Cannot parse the coordinates String for the GeoQuery: ${t.message}")
        }
    }

    private fun expandCoordinatesEnvelope(env: JsonArray): List<Location> {
        return if (env.size() > 0) {
            if (env.getValue(0) is JsonArray) {
                env.flatMap { expandCoordinatesEnvelope(it as JsonArray) }
            } else {
                listOf(Location(env.getDouble(1), env.getDouble(0)))
            }
        } else {
            emptyList()
        }
    }

}

@JsonInclude(JsonInclude.Include.NON_EMPTY)
data class TemporalQuery(val timerel: TimeRel, val time: String, val endTime: String? = null, val timeProperty: String = "observedAt")

@JsonSerialize(using = GeoRelSerializer::class)
@JsonDeserialize(using = GeoRelDeserializer::class)
data class GeoRel(
        val relationship: Relationship,
        val minDistance: Double? = null,
        val maxDistance: Double? = null
)

enum class Relationship {
    near, within, contains, intersects, equals, disjoint, overlaps
}

enum class Geometry {
    Point, MultiPoint, LineString, MultiLineString, Polygon, MultiPolygon
}