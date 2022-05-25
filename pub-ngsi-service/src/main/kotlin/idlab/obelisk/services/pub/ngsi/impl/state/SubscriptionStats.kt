package idlab.obelisk.services.pub.ngsi.impl.state

import idlab.obelisk.services.pub.ngsi.impl.model.SubscriptionInfo
import io.reactivex.Completable
import io.reactivex.Single

interface SubscriptionStats {

    operator fun get(dataStreamId: String): Single<SubscriptionInfo>
    operator fun set(dataStreamId: String, subscriptionInfo: SubscriptionInfo): Completable
    fun remove(dataStreamId: String): Completable

}