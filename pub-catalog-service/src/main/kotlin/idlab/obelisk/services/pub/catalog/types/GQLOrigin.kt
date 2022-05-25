package idlab.obelisk.services.pub.catalog.types

import graphql.schema.DataFetchingEnvironment
import idlab.obelisk.definitions.*
import idlab.obelisk.definitions.catalog.AccessManager
import idlab.obelisk.definitions.catalog.Client
import idlab.obelisk.definitions.catalog.User
import idlab.obelisk.definitions.data.*
import idlab.obelisk.services.pub.catalog.impl.Origin
import idlab.obelisk.services.pub.catalog.types.util.GQLFetcher
import idlab.obelisk.services.pub.catalog.types.util.GQLType
import idlab.obelisk.services.pub.catalog.types.util.Operations
import io.reactivex.Maybe
import java.util.concurrent.CompletionStage
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
@GQLType("Origin")
class GQLOrigin @Inject constructor(private val dataStore: DataStore, accessManager: AccessManager) :
    Operations(accessManager) {

    @GQLFetcher
    fun started(env: DataFetchingEnvironment): CompletionStage<Long> {
        return withAccessMaybe(env) {
            val origin = env.getSource<Origin>()
            val query = MetaQuery(
                dataRange = DataRange(datasets = listOf(origin.datasetId), metrics = MetricName.wildcard()),
                fields = listOf(MetaField.producer, MetaField.started),
                filter = when (origin.producer) {
                    is User -> Eq(Field(MetaField.producer.toString(), "userId"), origin.producer.id!!)
                    is Client -> Eq(Field(MetaField.producer.toString(), "clientId"), origin.producer.id!!)
                    else -> throw IllegalArgumentException("Invalid producer type!")
                },
                limit = 1
            )

            dataStore.getMetadata(query).flatMapMaybe { result ->
                result.items.firstOrNull()?.started?.let { Maybe.just(it) } ?: Maybe.empty()
            }
        }
    }

    @GQLFetcher
    fun lastUpdate(env: DataFetchingEnvironment): CompletionStage<Long> {
        return withAccessMaybe(env) {
            val origin = env.getSource<Origin>()
            val query = MetaQuery(
                dataRange = DataRange(datasets = listOf(origin.datasetId), metrics = MetricName.wildcard()),
                fields = listOf(MetaField.producer, MetaField.lastUpdate),
                filter = when (origin.producer) {
                    is User -> Eq(Field(MetaField.producer.toString(), "userId"), origin.producer.id!!)
                    is Client -> Eq(Field(MetaField.producer.toString(), "clientId"), origin.producer.id!!)
                    else -> throw IllegalArgumentException("Invalid producer type!")
                },
                limit = 1
            )
            dataStore.getMetadata(query).flatMapMaybe { result ->
                result.items.firstOrNull()?.lastUpdate?.let { Maybe.just(it) } ?: Maybe.empty()
            }
        }
    }

}