package idlab.obelisk.services.pub.catalog.types

import graphql.schema.DataFetchingEnvironment
import idlab.obelisk.definitions.catalog.AccessManager
import idlab.obelisk.definitions.catalog.Announcement
import idlab.obelisk.definitions.catalog.MetaStore
import idlab.obelisk.definitions.catalog.codegen.AnnouncementNullableField
import idlab.obelisk.definitions.catalog.codegen.AnnouncementUpdate
import idlab.obelisk.services.pub.catalog.impl.Response
import idlab.obelisk.services.pub.catalog.impl.errorResponse
import idlab.obelisk.services.pub.catalog.types.util.GQLFetcher
import idlab.obelisk.services.pub.catalog.types.util.GQLType
import idlab.obelisk.services.pub.catalog.types.util.Operations
import idlab.obelisk.utils.service.reactive.flatMapSingle
import java.util.concurrent.CompletionStage
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
@GQLType("AnnouncementMutation")
class GQLAnnouncementMutation @Inject constructor(private val metaStore: MetaStore, accessManager: AccessManager) :
    Operations(accessManager) {

    @GQLFetcher
    fun update(env: DataFetchingEnvironment): CompletionStage<Response<Announcement>> {
        return withAccess(env) {
            val source = env.getSource<Announcement>()
            val (update, nullFields) = calcUpdate<AnnouncementUpdate, AnnouncementNullableField>(env)
            metaStore.updateAnnouncement(source.id!!, update, nullFields)
                .flatMapSingle { metaStore.getAnnouncement(source.id!!).toSingle() }
                .map { Response(item = it) }
                .onErrorReturn { errorResponse(env, it) }
        }
    }

    @GQLFetcher
    fun remove(env: DataFetchingEnvironment): CompletionStage<Response<Announcement>> {
        return withAccess(env) {
            val source = env.getSource<Announcement>()
            metaStore.removeAnnouncement(source.id!!)
                .toSingleDefault(Response(item = source))
                .onErrorReturn { errorResponse(env, it) }
        }
    }

}