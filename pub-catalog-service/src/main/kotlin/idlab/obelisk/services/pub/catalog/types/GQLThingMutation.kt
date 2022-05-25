package idlab.obelisk.services.pub.catalog.types

import graphql.schema.DataFetchingEnvironment
import idlab.obelisk.definitions.catalog.AccessManager
import idlab.obelisk.definitions.catalog.MetaStore
import idlab.obelisk.definitions.catalog.Permission
import idlab.obelisk.definitions.catalog.Thing
import idlab.obelisk.definitions.catalog.codegen.ThingUpdate
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
class GQLThingMutation @Inject constructor(accessManager: AccessManager, private val metaStore: MetaStore) :
    Operations(accessManager) {

    @GQLFetcher
    fun update(env: DataFetchingEnvironment): CompletionStage<Response<Thing>> {
        val thing = env.getSource<Thing>()
        return withAccess(env) { token ->
            if (token.grants[thing.datasetId]!!.permissions.contains(Permission.WRITE)) {
                (thing.id?.let {
                    metaStore.updateThing(it, ThingUpdate(properties = env.getArgument("properties")))
                        .toSingleDefault(thing.id!!)
                }
                    ?: metaStore.createThing(thing.copy(properties = env.getArgument("properties"))))
                    .flatMap { thingId ->
                        metaStore.getThing(thingId).toSingle().map { Response(item = it) }
                    }
            } else {
                Single.error(AuthorizationException("You don't have the required write permissions on Dataset with id ${thing.datasetId} to update the Thing properties."))
            }
        }
    }

}