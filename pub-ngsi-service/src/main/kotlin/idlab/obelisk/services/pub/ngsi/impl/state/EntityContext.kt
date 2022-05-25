package idlab.obelisk.services.pub.ngsi.impl.state

import idlab.obelisk.definitions.data.EventsQuery
import idlab.obelisk.services.pub.ngsi.impl.model.NgsiEntity
import io.reactivex.Completable
import io.reactivex.Flowable
import io.reactivex.Maybe

// Bounded cache of the latest state of the NGSI Entities
interface EntityContext {

    fun getOrLoad(datasetId: String, entityId: String): Maybe<NgsiEntity>

    fun findInCache(query: EventsQuery, limit: Int): Flowable<NgsiEntity>

    fun cache(entity: NgsiEntity): Completable

    fun invalidate(datasetId: String, vararg entityIds: String): Completable


    // TODO: eventuele type / attribute cache toevoegen (voor metadata endpoints)
}
