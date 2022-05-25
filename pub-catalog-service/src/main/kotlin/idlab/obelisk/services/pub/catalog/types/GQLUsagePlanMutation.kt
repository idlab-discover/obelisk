package idlab.obelisk.services.pub.catalog.types

import graphql.schema.DataFetchingEnvironment
import idlab.obelisk.definitions.Eq
import idlab.obelisk.definitions.catalog.AccessManager
import idlab.obelisk.definitions.catalog.MetaStore
import idlab.obelisk.definitions.catalog.UsagePlan
import idlab.obelisk.definitions.catalog.codegen.UsagePlanField
import idlab.obelisk.definitions.catalog.codegen.UsagePlanNullableField
import idlab.obelisk.definitions.catalog.codegen.UsagePlanUpdate
import idlab.obelisk.services.pub.catalog.impl.Response
import idlab.obelisk.services.pub.catalog.impl.UsagePlanInput
import idlab.obelisk.services.pub.catalog.impl.errorResponse
import idlab.obelisk.services.pub.catalog.impl.parseInput
import idlab.obelisk.services.pub.catalog.types.util.GQLFetcher
import idlab.obelisk.services.pub.catalog.types.util.GQLType
import idlab.obelisk.services.pub.catalog.types.util.Operations
import idlab.obelisk.utils.service.reactive.flatMapSingle
import io.reactivex.Completable
import io.reactivex.Single
import java.util.concurrent.CompletionStage
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
@GQLType("UsagePlanMutation")
class GQLUsagePlanMutation @Inject constructor(accessManager: AccessManager, private val metaStore: MetaStore) :
    Operations(accessManager) {
    @GQLFetcher
    fun update(env: DataFetchingEnvironment): CompletionStage<Response<UsagePlan>> {
        return withAccess(env) {
            val source = env.getSource<UsagePlan>()
            val (update, nullFields) = calcUpdate<UsagePlanUpdate, UsagePlanNullableField>(env)

            metaStore.updateUsagePlan(source.id!!, update, nullFields)
                .flatMapSingle { metaStore.getUsagePlan(source.id!!).toSingle() }
                .map { Response(item = it) }
                .onErrorReturn { errorResponse(env, it) }
        }
    }

    @GQLFetcher
    fun makeDefault(env: DataFetchingEnvironment): CompletionStage<Response<UsagePlan>> {
        return withAccess(env) {
            val source = env.getSource<UsagePlan>()
            metaStore.queryUsagePlans(Eq(UsagePlanField.DEFAULT_PLAN, true), 1)
                .flatMapCompletable { result ->
                    if (result.items.isNotEmpty() && result.items.first().id != source.id) {
                        metaStore.updateUsagePlan(result.items.first().id!!, UsagePlanUpdate(defaultPlan = false))
                    } else {
                        Completable.complete()
                    }
                }
                .flatMapSingle {
                    metaStore.updateUsagePlan(source.id!!, UsagePlanUpdate(defaultPlan = true))
                        .flatMapSingle { metaStore.getUsagePlan(source.id!!).toSingle() }
                }
                .map { Response(item = it) }
                .onErrorReturn { errorResponse(env, it) }
        }
    }

    @GQLFetcher
    fun remove(env: DataFetchingEnvironment): CompletionStage<Response<UsagePlan>> {
        return withAccess(env) {
            val plan = env.getSource<UsagePlan>()
            (if (!plan.defaultPlan) {
                metaStore.removeUsagePlan(plan.id!!).toSingleDefault(plan)
            } else {
                Single.error { IllegalStateException("Cannot remove UsagePlan that is marked as default!") }
            })
                .map { Response(item = it) }
                .onErrorReturn { errorResponse(env, it) }
        }
    }

}