package idlab.obelisk.services.pub.catalog.impl

import hu.akarnokd.rxjava2.interop.SingleInterop
import idlab.obelisk.definitions.In
import idlab.obelisk.definitions.catalog.*
import idlab.obelisk.definitions.catalog.codegen.DatasetField
import idlab.obelisk.utils.service.utils.unpage
import io.vertx.reactivex.ext.web.RoutingContext
import org.dataloader.DataLoader
import org.dataloader.DataLoaderRegistry
import javax.inject.Inject
import javax.inject.Singleton

const val LOAD_PAGE_SIZE = 100

@Singleton
class DataLoaders @Inject constructor(private val metaStore: MetaStore) {

    val DL_DATASETS = "datasets"
    val DL_USERS = "users"

    private val datasetLoader: DataLoader<String, Dataset> = DataLoader.newDataLoader { keys, _ ->
        unpage { cursor ->
            metaStore.queryDatasets(
                filter = In(DatasetField.ID, keys.toSet()),
                cursor = cursor,
                limit = LOAD_PAGE_SIZE
            )
        }.toList().to(SingleInterop.get())
    }

    private val userLoader: DataLoader<String, User> = DataLoader.newDataLoader { keys, _ ->
        unpage { cursor ->
            metaStore.queryUsers(
                filter = In("id", keys.toSet()),
                cursor = cursor,
                limit = LOAD_PAGE_SIZE
            )
        }.toList().to(SingleInterop.get())
    }

    fun buildRegistry(ctx: RoutingContext): DataLoaderRegistry {

        return DataLoaderRegistry()
            .register(DL_DATASETS, datasetLoader)
            .register(DL_USERS, userLoader)
    }

}