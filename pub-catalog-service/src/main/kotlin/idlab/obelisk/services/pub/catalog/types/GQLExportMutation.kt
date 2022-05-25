package idlab.obelisk.services.pub.catalog.types

import graphql.schema.DataFetchingEnvironment
import idlab.obelisk.definitions.catalog.AccessManager
import idlab.obelisk.definitions.catalog.DataExport
import idlab.obelisk.definitions.catalog.MetaStore
import idlab.obelisk.services.pub.catalog.impl.Response
import idlab.obelisk.services.pub.catalog.impl.errorResponse
import idlab.obelisk.services.pub.catalog.types.util.GQLFetcher
import idlab.obelisk.services.pub.catalog.types.util.GQLType
import idlab.obelisk.services.pub.catalog.types.util.Operations
import java.util.concurrent.CompletionStage
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
@GQLType("ExportMutation")
class GQLExportMutation @Inject constructor(private val metaStore: MetaStore, accessManager: AccessManager) :
    Operations(accessManager) {

    @GQLFetcher
    fun remove(env: DataFetchingEnvironment): CompletionStage<Response<DataExport>> {
        return withAccess(env) { token ->
            val export = env.getSource<DataExport>()
            metaStore.removeDataExport(export.id!!).toSingleDefault(Response(item = export))
                .onErrorReturn { errorResponse(env, it) }
        }
    }

}