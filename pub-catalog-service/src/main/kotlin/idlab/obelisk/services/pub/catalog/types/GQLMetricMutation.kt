package idlab.obelisk.services.pub.catalog.types

import graphql.schema.DataFetchingEnvironment
import idlab.obelisk.definitions.catalog.AccessManager
import idlab.obelisk.definitions.catalog.MetaStore
import idlab.obelisk.definitions.catalog.Metric
import idlab.obelisk.definitions.catalog.Permission
import idlab.obelisk.definitions.catalog.codegen.MetricUpdate
import idlab.obelisk.services.pub.catalog.impl.Response
import idlab.obelisk.services.pub.catalog.types.util.GQLFetcher
import idlab.obelisk.services.pub.catalog.types.util.GQLType
import idlab.obelisk.services.pub.catalog.types.util.Operations
import idlab.obelisk.utils.service.http.AuthorizationException
import io.reactivex.Single
import java.util.concurrent.CompletionStage
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
@GQLType("ThingMutation")
class GQLMetricMutation @Inject constructor(accessManager: AccessManager, private val metaStore: MetaStore) :
    Operations(accessManager) {

    @GQLFetcher
    fun update(env: DataFetchingEnvironment): CompletionStage<Response<Metric>> {
        val metric = env.getSource<Metric>()
        return withAccess(env) { token ->
            if (token.grants[metric.datasetId]!!.permissions.contains(Permission.WRITE)) {
                (metric.id?.let {
                    metaStore.updateMetric(it, MetricUpdate(properties = env.getArgument("properties")))
                        .toSingleDefault(metric.id!!)
                }
                    ?: metaStore.createMetric(metric.copy(properties = env.getArgument("properties"))))
                    .flatMap { metricId ->
                        metaStore.getMetric(metricId).toSingle().map { Response(item = it) }
                    }
            } else {
                Single.error(AuthorizationException("You don't have the required write permissions on Dataset with id ${metric.datasetId} to update the Metric properties."))
            }
        }
    }

}