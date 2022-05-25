package idlab.obelisk.services.pub.ngsi

import idlab.obelisk.plugins.accessmanager.basic.BasicAccessManagerModule
import idlab.obelisk.plugins.datastore.clickhouse.ClickhouseDataStoreModule
import idlab.obelisk.plugins.metastore.mongo.MongoDBMetaStoreModule
import idlab.obelisk.plugins.ratelimiter.gubernator.GubernatorRateLimiterModule
import idlab.obelisk.pulsar.utils.PulsarModule
import idlab.obelisk.utils.service.OblxBaseModule
import idlab.obelisk.utils.service.OblxLauncher

fun main(args: Array<String>) {
    OblxLauncher.with(
        OblxBaseModule(),
        PulsarModule(),
        MongoDBMetaStoreModule(),
        BasicAccessManagerModule(),
        ClickhouseDataStoreModule(),
        GubernatorRateLimiterModule(),
        NgsiModule()
    )
        .bootstrap(NgsiLDService::class.java, NgsiLDSubscriptionMatcher::class.java, NgsiLDNotifier::class.java)
}
