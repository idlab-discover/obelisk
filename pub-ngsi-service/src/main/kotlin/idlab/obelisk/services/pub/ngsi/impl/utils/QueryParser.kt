package idlab.obelisk.services.pub.ngsi.impl.utils

import idlab.obelisk.definitions.*
import idlab.obelisk.plugins.datastore.clickhouse.impl.utils.toCHDateTimeMicroseconds
import idlab.obelisk.services.pub.ngsi.Constants
import idlab.obelisk.services.pub.ngsi.impl.model.*
import io.vertx.core.json.JsonObject
import org.locationtech.spatial4j.context.SpatialContext
import org.locationtech.spatial4j.io.GeohashUtils
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

object QueryParser {
    val EXTRACTOR_PATH_PROPERTY = "${Constants.FQ_HAS_VALUE}, ${Field.encodeArrayIndex(1)}, @value"
    val EXTRACTOR_PATH_RELATION = "${Constants.FQ_HAS_OBJECT}, ${Field.encodeArrayIndex(1)}, @id"

    private val OPERATOR_PATTERN = Pattern.compile("(.*)(==|!=|>|>=|<|<=|~=|!~=)(.*)")
    private val URI_PATTERN = Pattern.compile("^(http|https|urn):.*")

    fun parse(q: String, ldContext: JsonObject = JsonObject()): FilterExpression {
        return if (q.startsWith("(")) {
            var balance = 1
            val current = q.drop(1).takeWhile { c ->
                balance += when (c) {
                    '(' -> 1
                    ')' -> -1
                    else -> 0
                }
                balance != 0
            }
            if (current.length == q.length - 1) {
                throw IllegalArgumentException("Invalid Query: could not match parentheses!")
            }
            val currentFilter = parse(current, ldContext)
            val next = q.substringAfter(current).drop(1)
            if (next.isEmpty()) {
                currentFilter
            } else if (next.startsWith('|')) {
                Or(currentFilter, parse(next.drop(1), ldContext))
            } else if (next.startsWith(';')) {
                And(currentFilter, parse(next.drop(1), ldContext))
            } else {
                throw IllegalArgumentException("Invalid Query: ${if (next.startsWith(")")) "could not match parentheses!" else "you must combine statements with | (OR) or ; (AND)!"}")
            }
        } else if (q.contains('|')) {
            Or(q.split('|').map { parse(it, ldContext) })
        } else if (q.contains(';')) {
            And(q.split(';').map { parse(it, ldContext) })
        } else {
            // Process leafs
            val matcher = OPERATOR_PATTERN.matcher(q)
            if (matcher.matches()) {
                parseStatement(matcher.group(1), matcher.group(2), matcher.group(3), ldContext)
            } else {
                return Eq(EventField.metric.toString(), "${JsonLdUtils.getFQAttribute(q, ldContext)}::json")
            }
        }
    }

    fun parse(geoQuery: GeoQuery): FilterExpression {
        val coords = geoQuery.parseCoordinates()
        return when (geoQuery.geometry) {
            Geometry.Point -> when (geoQuery.georel.relationship) {
                Relationship.equals -> {
                    Eq(EventField.geohash.toString(), GeohashUtils.encodeLatLon(coords.first().lat, coords.first().lng))
                }
                Relationship.near -> {
                    if (geoQuery.georel.maxDistance != null) {
                        LocationInCircle(coords.first(), geoQuery.georel.maxDistance.toInt())
                    } else if (geoQuery.georel.minDistance != null) {
                        Not(LocationInCircle(coords.first(), geoQuery.georel.minDistance.toInt()))
                    } else {
                        throw java.lang.IllegalArgumentException("Geo-relationship near needs a maxDistance / minDistance modifier!")
                    }
                }
                else -> throw IllegalArgumentException("Geo-relationship ${geoQuery.georel.relationship} is not supported for Point Geometry (we support 'equals' and 'near')!")
            }
            Geometry.Polygon -> when (geoQuery.georel.relationship) {
                Relationship.within -> LocationInPolygon(coords.map { Coordinate2D(it.lat, it.lng) })
                Relationship.disjoint -> Not(LocationInPolygon(coords.map { Coordinate2D(it.lat, it.lng) }))
                else -> throw IllegalArgumentException("Geo-relationship ${geoQuery.georel.relationship} is not supported for Point Geometry (we support 'within' and 'disjoint')!")
            }
            else -> throw java.lang.IllegalArgumentException("Geometry ${geoQuery.geometry} is not supported (we support Point and Polygon)!")
        }
    }

    fun parse(temporalQuery: TemporalQuery): FilterExpression {
        if (temporalQuery.timeProperty != "observedAt") {
            throw IllegalArgumentException("Currently only 'observedAt' is supported as timeProperty!")
        }
        val parsedTime = toCHDateTimeMicroseconds(dateTimeToMus(temporalQuery.time))
        val parsedEndTime = temporalQuery.endTime?.let { toCHDateTimeMicroseconds(dateTimeToMus(it)) }
        return when (temporalQuery.timerel) {
            TimeRel.before -> Lte(EventField.timestamp.toString(), parsedTime)
            TimeRel.after -> Gte(EventField.timestamp.toString(), parsedTime)
            TimeRel.between -> And(
                Gte(EventField.timestamp.toString(), parsedTime),
                Lte(EventField.timestamp.toString(),
                    parsedEndTime
                        ?: throw IllegalArgumentException("When 'timerel=between', 'endTime' must be specified!")
                )
            )
        }
    }

    private fun dateTimeToMus(dateTime: String): Long {
        return TimeUnit.MICROSECONDS.convert(Instant.parse(dateTime).toEpochMilli(), TimeUnit.MILLISECONDS)
    }

    private fun parseStatement(
        attribute: String,
        operator: String,
        argument: String,
        ldContext: JsonObject
    ): FilterExpression {
        val metric = attribute.substringBeforeLast('.')
        val subProp = attribute.substringAfterLast('.')
        val isRelationQ = URI_PATTERN.matcher(argument).matches()
        val extractorFinalPath = if (isRelationQ) EXTRACTOR_PATH_RELATION else EXTRACTOR_PATH_PROPERTY
        val extractor = "value, ${
            if (isRelationQ || subProp.isEmpty() || metric == subProp) extractorFinalPath else "${
                JsonLdUtils.getFQAttribute(
                    subProp,
                    ldContext
                )
            }, ${Field.encodeArrayIndex(1)}, $extractorFinalPath"
        }"
        val argNumber = argument.toDoubleOrNull() ?: argument
        val field = Field(extractor.split(",").map { it.trim() })

        return when (operator) {
            "==" -> Eq(field, argNumber)
            "!=" -> Neq(field, argNumber)
            ">" -> Gt(field, argNumber)
            ">=" -> Gte(field, argNumber)
            "<" -> Lt(field, argNumber)
            "<=" -> Lte(field, argNumber)
            "~=" -> RegexMatches(field, argument)
            "!~=" -> Not(RegexMatches(field, argument))
            else -> throw IllegalArgumentException("Unsupported operator ('$operator') in query!")
        }
    }

}
