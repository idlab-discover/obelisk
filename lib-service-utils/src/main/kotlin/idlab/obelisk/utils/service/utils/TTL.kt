package idlab.obelisk.utils.service.utils

import java.time.Duration
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern



class TTL {
    companion object {
        const val TOKEN_TTL_PROP = "TOKEN_TTL"
        const val TOKEN_TTL = "1d"
        const val TOKEN_IDLE_TTL_PROP = "TOKEN_IDLE_TTL"
        const val TOKEN_IDLE_TTL = "1h"
        const val LOGIN_TTL_PROP = "LOGIN_TTL"
        const val LOGIN_TTL = "2m"
        const val SESSION_TTL_PROP = "SESSION_TTL"
        const val SESSION_TTL = "30d"
        const val SESSION_IDLE_TTL_PROP = "SESSION_IDLE_TTL"
        const val SESSION_IDLE_TTL = "7d"
    }

    var value: Long
    var unit: TimeUnit
    val pattern: Pattern = Pattern.compile("^([0-9]+)([sSmMhHdD])$")

    @Throws(IllegalTTLExpression::class)
    constructor(ttl: String?) {
        val matcher = pattern.matcher(ttl)
        if (matcher.matches()) {
            value = matcher.group(1).toLong()
            unit = convertToUnit(matcher.group(2))!!
        } else {
            throw IllegalTTLExpression(ttl!!)
        }
    }

    private fun convertToUnit(input: String): TimeUnit? {
        when (input.toLowerCase()) {
            "s" -> return TimeUnit.SECONDS
            "m" -> return TimeUnit.MINUTES
            "h" -> return TimeUnit.HOURS
            "d" -> return TimeUnit.DAYS
        }
        return null
    }


    fun inMinutes(): Long {
        return Duration.of(value, unit.toChronoUnit()).toMinutes()
    }

    fun inSeconds(): Long {
        return Duration.of(value, unit.toChronoUnit()).toSeconds()
    }

}

class IllegalTTLExpression(ttl: String): RuntimeException("$ttl is not a legal TTL expression. (must be a number followed by s, S, m, M, h or H")