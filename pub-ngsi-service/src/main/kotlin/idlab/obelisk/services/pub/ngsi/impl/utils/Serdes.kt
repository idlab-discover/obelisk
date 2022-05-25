package idlab.obelisk.services.pub.ngsi.impl.utils

import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.SerializerProvider
import com.fasterxml.jackson.databind.deser.std.StdDeserializer
import com.fasterxml.jackson.databind.ser.std.StdSerializer
import idlab.obelisk.services.pub.ngsi.impl.model.GeoRel
import idlab.obelisk.services.pub.ngsi.impl.model.Relationship

class GeoRelSerializer : StdSerializer<GeoRel>(GeoRel::class.java) {
    override fun serialize(georel: GeoRel?, jsonGenerator: JsonGenerator?, provider: SerializerProvider?) {
        if (jsonGenerator != null && georel != null) {
            jsonGenerator.writeString("${georel.relationship}${georel.minDistance?.let { ";$it" } ?: ""}${georel.maxDistance?.let { ";$it" } ?: ""}")
        }
    }
}

class GeoRelDeserializer : StdDeserializer<GeoRel>(GeoRel::class.java) {
    override fun deserialize(parser: JsonParser?, context: DeserializationContext?): GeoRel {
        val geoRelStr = parser!!.text
        return geoRelStr.split(";").let {parts ->
            val minMax = parts.drop(1).map { it.split("==").let { Pair(it[0], it[1].toDouble()) } }.toMap()
            GeoRel(
                    relationship = Relationship.valueOf(parts.getOrNull(0)!!),
                    minDistance = minMax["minDistance"],
                    maxDistance = minMax["maxDistance"]
            )
        }
    }

}