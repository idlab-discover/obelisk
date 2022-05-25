package idlab.obelisk.services.pub.streaming

import idlab.obelisk.utils.service.instrumentation.IdToNameMap
import idlab.obelisk.utils.service.instrumentation.TagTemplate
import io.micrometer.core.instrument.MeterRegistry
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

class ActiveStreamTracker(private val microMeterRegistry: MeterRegistry) {

    // Map of counters per dataset
    private val counters = ConcurrentHashMap<String, AtomicInteger>()
    private val datasetIdAndNameTag = TagTemplate("datasetId", "datasetName")

    fun streamStarted(datasetId: String, datasetIdToNameMap: IdToNameMap) {
        counters.getOrPut(datasetId) {
            val counter = AtomicInteger(0)
            StreamingService.microMeterRegistry.gauge(
                "oblx.sse.active.streams",
                datasetIdAndNameTag.instantiate(datasetId, datasetIdToNameMap.getName(datasetId) ?: ""),
                counter
            )!!
            counter
        }.incrementAndGet()
    }

    fun streamStopped(datasetId: String) {
        counters[datasetId]?.decrementAndGet()
    }

}