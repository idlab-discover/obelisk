package idlab.obelisk.utils.service.utils

import java.util.concurrent.TimeUnit

// Added these extensions because these operations occur often and this makes them much more concise and readable.

/**
 * Quick conversion to a millisecond long timestamp
 */
fun Long.toMs(sourceUnit: TimeUnit = TimeUnit.MICROSECONDS): Long {
    return TimeUnit.MILLISECONDS.convert(this, sourceUnit)
}

/**
 * Quick conversion to a microsecond long timestamp
 */
fun Long.toMus(sourceUnit: TimeUnit = TimeUnit.MILLISECONDS): Long {
    return TimeUnit.MICROSECONDS.convert(this, sourceUnit)
}

fun Long.fromMs(targetUnit: TimeUnit): Long {
    return targetUnit.convert(this, TimeUnit.MILLISECONDS)
}

fun Long.fromMus(targetUnit: TimeUnit): Long {
    return targetUnit.convert(this, TimeUnit.MICROSECONDS)
}