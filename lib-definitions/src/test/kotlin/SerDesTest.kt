import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import idlab.obelisk.definitions.*
import idlab.obelisk.definitions.catalog.*
import idlab.obelisk.definitions.data.EventsQuery
import idlab.obelisk.definitions.data.Location
import io.vertx.core.json.Json
import io.vertx.core.json.jackson.DatabindCodec
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import java.util.Map
import java.util.Set


class SerDesTest {

    companion object {

        @BeforeAll
        @JvmStatic
        fun init() {
            DatabindCodec.mapper().registerKotlinModule()
            DatabindCodec.prettyMapper().registerKotlinModule()
        }

    }

    @Test
    fun testSerDesToken() {
        val usageLimits = UsageLimit(
            name = "test"
        )

        val user = User("0", "me@test.be", "Me", "Myself")
        val ai = Map.of(
            "1",
            Grant(Set.of(Permission.READ, Permission.WRITE), SELECT_ALL),
            "2",
            Grant(Set.of(Permission.READ), SELECT_ALL)
        )
        val opaqueToken = "asdfomwefAWEFESFASEFefae"
        val token = Token(user, null, ai, usageLimits, opaqueToken)

        val json = Json.encode(token)
        val result = Json.decodeValue(json, Token::class.java)
        Assertions.assertEquals(token, result)
    }

    @Test
    fun testDesSerFilter() {
        val filterJson =
            "{\"_and\":[{\"_withAnyTag\":[\"_type=https://uri.etsi.org/ngsi-ld/default-context/Vehicle\"]},{\"value->https://uri.etsi.org/ngsi-ld/hasValue->[1]->@value\":{\"_gt\":50.0}}]}"
        val filter = Json.decodeValue(filterJson, FilterExpression::class.java)
        Assertions.assertEquals(filterJson, Json.encode(filter))
    }

    @Test
    fun testSerDesFilter() {
        val filter = And(
            HasOneOfTags("test1", "test2"),
            Or(
                Eq(Field("value", "attr1"), "someVal"),
                LocationInPolygon(listOf(Coordinate2D(0.0, 3.1), Coordinate2D(0.4, 7.0), Coordinate2D(7.0, 3.0)))
            )
        )
        val filterJson = Json.encode(filter)
        Assertions.assertEquals(filter, Json.decodeValue(filterJson, FilterExpression::class.java))
    }

    @Test
    fun testSerDesFilter2() {
        val filter = Not(And(LocationInCircle(Location(5.1516, 3.48418), 200), Gt("value", 50)))
        val filterJson = Json.encode(filter)
        Assertions.assertEquals(filter, Json.decodeValue(filterJson, FilterExpression::class.java))
    }

    @Test
    fun testNestedOperator() {
        val filter = Eq(Field("producer", "userId"), "wkerckho@intec.ugent.be")
        val filterJson = Json.encode(filter)
        Assertions.assertEquals(filter, Json.decodeValue(filterJson, FilterExpression::class.java))
    }

    @Test
    fun testRegexWithOptions() {
        val filter = And(Eq("author", "wkerckho"), RegexMatches("name", "test"))
        val filterJson = Json.encode(filter)
        Assertions.assertEquals(filter, Json.decodeValue(filterJson, FilterExpression::class.java))
    }

    @Test
    fun testEventsQueryWithFilters() {
        val cursor = "eyJuIjpbIjIwMjEtMTItMDIgMDM6Mjc6MzMuNzE1Il0sIm8iOjB9"
        // This test case was added because SerDes implementation ignored content after filter attribute...
        val queryJson = """
            {
              "dataRange": {
                "datasets": ["612f66e1cbceda0ea9753d89"]
              },
              "from": "1638403200000",
              "to": "1638493200000",
              "filter": {
                    "_and": [
                        {
                            "source": {
                                "_eq": "velbus.90.EnergyMeter1"
                            }
                        },
                        {
                            "metric": {
                                "_eq": "energy.consumption::number"
                            }
                        }
                    ]
              },
              "orderBy": {
                  "ordering": "asc"
              },
              "cursor": "$cursor"
            }
        """.trimIndent()
        val query = Json.decodeValue(queryJson, EventsQuery::class.java)
        Assertions.assertTrue(query.cursor != null)
        Assertions.assertEquals(cursor, query.cursor)
    }

}
