package idlab.obelisk.services.pub.catalog.types

import graphql.schema.DataFetchingEnvironment
import idlab.obelisk.definitions.catalog.AccessManager
import idlab.obelisk.definitions.catalog.ClientRestriction
import idlab.obelisk.definitions.catalog.Dataset
import idlab.obelisk.definitions.catalog.MetaStore
import idlab.obelisk.services.pub.catalog.types.util.GQLFetcher
import idlab.obelisk.services.pub.catalog.types.util.GQLType
import idlab.obelisk.services.pub.catalog.types.util.Operations
import java.util.concurrent.CompletionStage
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
@GQLType("ClientRestriction")
class GQLClientRestriction @Inject constructor(private val metaStore: MetaStore, accessManager: AccessManager) : Operations(accessManager) {

    @GQLFetcher
    fun dataset(env: DataFetchingEnvironment): CompletionStage<Dataset> {
        return withAccessMaybe(env) {
            val restriction = env.getSource<ClientRestriction>()
            metaStore.getDataset(restriction.datasetId)
        }
    }

}