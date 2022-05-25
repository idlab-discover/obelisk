package idlab.obelisk.utils.test.rg

import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.ArrayList

object Time {

    data class Range(val fromMs: Long, val toMs: Long) {
        enum class DurationUnit {
            DAYS, WEEKS, MONTHS, YEARS;

            fun convertToDays(duration: Int): Int {
                when (this) {
                    WEEKS -> return 7 * duration
                    MONTHS -> return (30.417 * duration).toInt()
                    YEARS -> return 365 * duration
                    else //DAYS
                    -> return duration
                }
            }
        }

        fun randomSubRange(): Range {
            val t1 = timestampIn(this)
            val t2 = timestampIn(this)
            return if (t1 == t2) {
                randomSubRange()
            } else {
                if (t1 < t2) Range(t1, t2) else Range(t2, t1)
            }
        }

        fun duration(): Long {
            return toMs - fromMs
        }

        operator fun contains(timestampMs: Long): Boolean {
            return timestampMs in fromMs until toMs
        }

    }

    fun range(durationLength: Int, durationUnit: Range.DurationUnit): Range {
        val durationInDays = durationUnit.convertToDays(durationLength)

        // Go back at most ~2 years for end timestamp (rough calculation)
        val end = Instant.now().minus(DeterministicRG.instance.nextInt(365 * 2).toLong(), ChronoUnit.DAYS)
        return Range(end.minus(durationInDays.toLong(), ChronoUnit.DAYS).toEpochMilli(), end.toEpochMilli())
    }

    fun timestampIn(range: Range): Long {
        return Values.longBetween(range.fromMs, range.toMs)
    }

    fun timestampSequenceIn(range: Range, sequenceLength: Int): List<Long> {
        val startPoint = Values.longBetween(range.fromMs, range.toMs)
        var period = 0.0
        var tmp = 0.0
        if (range.toMs - startPoint > sequenceLength) {
            period = 1.0
            tmp = startPoint.toDouble()
        } else if (range.toMs - sequenceLength > range.fromMs) {
            period = 1.0
            tmp = range.toMs.toDouble()
        } else {
            period = java.lang.Long.valueOf(range.duration()).toDouble() / sequenceLength
            tmp = range.toMs.toDouble()
        }
        val result = ArrayList<Long>(sequenceLength)
        for (i in 0 until sequenceLength) {
            result.add(java.lang.Double.valueOf(tmp).toLong())
            tmp += period
        }

        return result
    }

    fun timestampOutOf(range: Range): Long {
        return if (DeterministicRG.instance.nextBoolean()) Values.longBetween(java.lang.Long.MIN_VALUE, range.fromMs - 1) else Values.longBetween(range.toMs + 1, System.currentTimeMillis())
    }


}
