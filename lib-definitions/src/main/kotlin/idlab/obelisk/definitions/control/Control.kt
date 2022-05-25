package idlab.obelisk.definitions.control

import idlab.obelisk.definitions.MetricName

/**
 * This file contains a number of Utils and Message formats as data classes
 * used in the coordination and control channels for Obelisk.
 *
 * Microservices can communicate if necessary using Pulsar as a pub/sub mechanism or via Redis (shared memory).
 */

object ControlChannels {
    const val DATA_STREAM_EVENTS_TOPIC = "data_stream_events"
    const val EXPORT_EVENT_TOPIC = "export_events"
}

object ControlKeys {
    const val REDIS_LOGINS = "logins";
    const val REDIS_SESSIONS = "sessions";
    const val REDIS_TOKENS = "tokens";
    const val REDIS_SID_SUB = "sid-sub"
}

enum class DataStreamEventType {
    INIT, STOP
}

data class DataStreamEvent(val type: DataStreamEventType, val streamId: String) {
    constructor() : this(DataStreamEventType.STOP, "") // Hack to enable Pulsar Jackson shaded instance to work...
}

data class ExportEvent(val exportId: String) {
    constructor() : this("") // Hack to enable Pulsar Jackson shaded instance to work...
}