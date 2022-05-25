package idlab.obelisk.client

import idlab.obelisk.definitions.data.MetricEvent
import io.reactivex.Completable
import io.reactivex.Flowable

interface OblxStreamSession {

    fun toFlowable(): Flowable<MetricEvent>

    fun stop(): Completable

}