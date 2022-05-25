package idlab.obelisk.utils.test.rg

import io.vertx.core.json.JsonArray

import java.util.stream.IntStream

object Values {

    private val ALPHABET = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ-_/:#@$; "

    fun someFloat(): Double {
        return floatBetween(java.lang.Double.MIN_VALUE, java.lang.Double.MAX_VALUE)
    }

    fun someInt(): Int {
        return DeterministicRG.instance.nextInt()
    }

    fun someLong(): Long {
        return longBetween(java.lang.Long.MIN_VALUE, java.lang.Long.MAX_VALUE)
    }

    fun floatBetween(min: Double, max: Double): Double {
        return min + DeterministicRG.instance.nextDouble() * (max - min)
    }

    fun intBetween(min: Int, max: Int): Int {
        return min + DeterministicRG.instance.nextInt(max - min)
    }

    fun longBetween(min: Long, max: Long): Long {
        return min + (DeterministicRG.instance.nextDouble() * (max - min)).toLong()
    }

    fun someString(maxSize: Int): String {
        return stringBetween(0, maxSize)
    }

    fun stringBetween(minChars: Int, maxChars: Int): String {
        val count = minChars + DeterministicRG.instance.nextInt(maxChars - minChars)
        val sb = StringBuilder()
        for (i in 0 until count) {
            sb.append(ALPHABET[DeterministicRG.instance.nextInt(ALPHABET.length)])
        }
        return sb.toString()
    }

    fun floatArray(size: Int): JsonArray {
        val result = JsonArray()
        IntStream.range(0, size).mapToDouble { i -> someFloat() }.forEach { result.add(it) }
        return result
    }

    fun fixedArray(length: Int): JsonArray {
        return IntStream.range(0, length).mapToDouble { i -> floatBetween(-250.0, 250.0) }.collect({ JsonArray() }, { objects, value -> objects.add(value) }, { objects, objects2 -> objects.addAll(objects2) })
    }
}
