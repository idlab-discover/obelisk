package idlab.obelisk.services.pub.catalog.types

import graphql.schema.DataFetchingEnvironment
import idlab.obelisk.definitions.DataRange
import idlab.obelisk.definitions.In
import idlab.obelisk.definitions.catalog.AccessManager
import idlab.obelisk.definitions.catalog.Dataset
import idlab.obelisk.definitions.catalog.MetaStore
import idlab.obelisk.definitions.catalog.codegen.DatasetField
import idlab.obelisk.services.pub.catalog.types.util.GQLFetcher
import idlab.obelisk.services.pub.catalog.types.util.GQLType
import idlab.obelisk.services.pub.catalog.types.util.Operations
import idlab.obelisk.utils.service.utils.unpage
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
@GQLType("DataRange")
class GQLDataRange @Inject constructor(accessManager: AccessManager, private val metaStore: MetaStore) :
    Operations(accessManager) {

    @GQLFetcher
    fun datasets(env: DataFetchingEnvironment): CompletionStage<List<Dataset>> {
        return withAccess(env) {
            val source = env.getSource<DataRange>()
            unpage { cursor -> metaStore.queryDatasets(In(DatasetField.ID, source.datasets.toSet())) }.toList()
        }
    }

    @GQLFetcher
    fun metrics(env: DataFetchingEnvironment): CompletionStage<List<String>> {
        val source = env.getSource<DataRange>()
        return CompletableFuture.completedStage(source.metrics.map { it.getFullyQualifiedId() })
    }

}