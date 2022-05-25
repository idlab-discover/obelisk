import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import idlab.obelisk.definitions.DataRange
import idlab.obelisk.definitions.TimestampPrecision
import io.vertx.core.json.JsonObject
import io.vertx.core.json.jackson.DatabindCodec
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test

class CommonsTest {

    companion object {

        @BeforeAll
        @JvmStatic
        fun init() {
            DatabindCodec.mapper().registerKotlinModule()
        }

    }

    @Test
    fun dataRangeTest() {

        DataRange(datasets = listOf())
            .also {
                tortureSon(it)
            }

        DataRange(
            listOf("dataset1"),
            listOf()
        )
            .also {
                tortureSon(it)
            }
    }

    @Test
    fun timestampPrecisionTest() {
        val original = TimestampPrecision.milliseconds
        val str = original.toString()
        val decoded = TimestampPrecision.valueOf(str)
        assertEquals(original, decoded)
        assertEquals(original.toString(), decoded.toString())
    }

    /**
     * Do some conversions of the given object, back and forth to Json.
     */
    private fun <T> tortureSon(obj: T, expectedJsonObject: JsonObject? = null) {

        val object1 = DataRange(datasets = listOf())
        val json1 = JsonObject.mapFrom(object1)
        val object2 = json1.mapTo(DataRange::class.java)
        val json2 = JsonObject.mapFrom(object2)

        assertEquals(object1, object2)
        assertEquals(json1, json2)

        if (expectedJsonObject != null) {
            assertEquals(json1, expectedJsonObject)
            assertEquals(json2, expectedJsonObject)
        }

    }
}
