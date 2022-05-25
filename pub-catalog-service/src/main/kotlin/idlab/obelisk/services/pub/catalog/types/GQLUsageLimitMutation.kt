package idlab.obelisk.services.pub.catalog.types

import graphql.schema.DataFetchingEnvironment
import idlab.obelisk.definitions.Eq
import idlab.obelisk.definitions.catalog.AccessManager
import idlab.obelisk.definitions.catalog.MetaStore
import idlab.obelisk.definitions.catalog.UsageLimit
import idlab.obelisk.definitions.catalog.codegen.UsageLimitField
import idlab.obelisk.definitions.catalog.codegen.UsageLimitNullableField
import idlab.obelisk.definitions.catalog.codegen.UsageLimitUpdate
import idlab.obelisk.services.pub.catalog.impl.*
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
@GQLType("UsageLimitMutation")
class GQLUsageLimitMutation @Inject constructor(
    private val metaStore: MetaStore,
    accessManager: AccessManager
) : Operations(accessManager) {

    @GQLFetcher
    fun update(env: DataFetchingEnvironment): CompletionStage<Response<UsageLimit>> {
        return withAccess(env) {
            val source = env.getSource<UsageLimit>()
            val (update, nullFields) = calcUpdate<UsageLimitUpdate, UsageLimitNullableField>(env)

            metaStore.updateUsageLimit(source.id!!, update, nullFields)
                .flatMapSingle { metaStore.getUsageLimit(source.id!!).toSingle() }
                .map { Response(item = it) }
                .onErrorReturn { errorResponse(env, it) }
        }
    }

    @GQLFetcher
    fun makeDefault(env: DataFetchingEnvironment): CompletionStage<Response<UsageLimit>> {
        return withAccess(env) {
            val source = env.getSource<UsageLimit>()
            metaStore.queryUsageLimits(Eq(UsageLimitField.DEFAULT_LIMIT, true), 1)
                .flatMapCompletable { result ->
                    if (result.items.isNotEmpty() && result.items.first().id != source.id) {
                        metaStore.updateUsageLimit(
                            result.items.first().id!!,
                            UsageLimitUpdate(defaultLimit = false)
                        )
                    } else {
                        Completable.complete()
                    }
                }
                .flatMapSingle {
                    val updatedLimit = source.copy(defaultLimit = true)
                    metaStore.updateUsageLimit(source.id!!, UsageLimitUpdate(defaultLimit = true))
                        .flatMapSingle { metaStore.getUsageLimit(source.id!!).toSingle() }
                }
                .map { Response(item = it) }
                .onErrorReturn { errorResponse(env, it) }
        }
    }

    @GQLFetcher
    fun remove(env: DataFetchingEnvironment): CompletionStage<Response<UsageLimit>> {
        return withAccess(env) {
            val limits = env.getSource<UsageLimit>()
            (if (!limits.defaultLimit) {
                metaStore.removeUsageLimit(limits.id!!).toSingleDefault(limits)
            } else {
                Single.error { IllegalStateException("Cannot remove UsageLimits that are marked as default!") }
            })
                .map { Response(item = it) }
                .onErrorReturn { errorResponse(env, it) }
        }
    }
}