package idlab.obelisk.services.pub.ngsi.impl.state

import idlab.obelisk.definitions.data.EventsQuery
import idlab.obelisk.definitions.data.MetricEvent
import idlab.obelisk.services.pub.ngsi.impl.model.NgsiEntity
import io.reactivex.Completable
import io.reactivex.Flowable

interface HistoricalBuffer {

    fun put(datasetId: String, temporalEntity: NgsiEntity): Completable

    fun get(query: EventsQuery, limit: Int): Flowable<List<MetricEvent>>

    fun remove(query: EventsQuery): Completable
}