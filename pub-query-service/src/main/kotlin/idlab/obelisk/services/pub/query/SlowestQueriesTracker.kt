package idlab.obelisk.services.pub.query

import idlab.obelisk.definitions.data.DataQuery
import io.vertx.core.json.Json
import mu.KotlinLogging

class SlowestQueriesTracker(private val maxSize: Int = 25, private val slowQueryThreshold: Long) {

    private val logger = KotlinLogging.logger { }
    private var trackedQueries = emptyList<TimedQuery>()

    fun notifyQueryExecuted(query: DataQuery, executionTimeMs: Long) {
        if (executionTimeMs >= slowQueryThreshold) {
            logger.warn { "Slow query detected: ${Json.encode(query)} took $executionTimeMs ms." }
        }

        trackedQueries =
            trackedQueries.plus(TimedQuery(query, executionTimeMs)).sortedByDescending { it.executionTimeMs }
                .take(maxSize)
    }

    fun notifyTimeout(query: DataQuery) {
        logger.warn { "A query has timed out: ${Json.encode(query)}" }
    }

    fun list(): List<TimedQuery> {
        return trackedQueries
    }

}

data class TimedQuery(val query: DataQuery, val executionTimeMs: Long)