package idlab.obelisk.services.pub.catalog.impl

import idlab.obelisk.definitions.catalog.AccessManager
import idlab.obelisk.definitions.catalog.MetaStore
import idlab.obelisk.definitions.catalog.Permission
import idlab.obelisk.definitions.framework.OblxConfig
import idlab.obelisk.utils.service.http.*
import io.reactivex.Completable
import io.reactivex.Flowable
import io.reactivex.rxkotlin.subscribeBy
import io.vertx.core.file.CopyOptions
import io.vertx.reactivex.core.Vertx
import io.vertx.reactivex.core.file.FileProps
import io.vertx.reactivex.ext.web.FileUpload
import io.vertx.reactivex.ext.web.Router
import io.vertx.reactivex.ext.web.RoutingContext
import io.vertx.reactivex.ext.web.handler.BodyHandler
import org.apache.tika.Tika
import java.io.File
import java.io.FileNotFoundException
import java.nio.file.NoSuchFileException
import javax.inject.Inject
import javax.inject.Singleton

const val RESOURCES_PATH = "RESOURCES_PATH"
const val RESOURCES_PATH_DEFAULT = "/oblx/resources"
const val RESOURCES_SIZE_LIMIT_MB = "RESOURCES_SIZE_LIMIT_MB"
const val RESOURCES_SIZE_LIMIT_MB_DEFAULT = 5
private val tika = Tika()

@Singleton
class Resources @Inject constructor(private val vertx: Vertx, private val router: Router, config: OblxConfig, private val accessManager: AccessManager, private val metaStore: MetaStore) {
    private val resourcePath = config.getString(RESOURCES_PATH, RESOURCES_PATH_DEFAULT)
    private val resourceLimitBytes = config.getInteger(RESOURCES_SIZE_LIMIT_MB, RESOURCES_SIZE_LIMIT_MB_DEFAULT).toLong() * 1024 * 1024

    fun init(basePath: String) {
        // Users with MANAGE permission can use this endpoint to upload additional resources for the dataset
        router.route("${basePath}/resources/:datasetId").handler(BodyHandler.create().setBodyLimit(resourceLimitBytes).setHandleFileUploads(true).setUploadsDirectory(resourcePath))
        router.post("${basePath}/resources/:datasetId").handler { ctx ->
            checkManagePermissions(ctx)
                    .andThen(Completable.defer { Completable.concat(Flowable.fromIterable(ctx.fileUploads()).map { handleUpload(ctx, it) }) })
                    .subscribeBy(
                            onComplete = { ctx.response().setStatusCode(201).end() },
                            onError = writeHttpError(ctx)
                    )
        }

        // List resources for the dataset
        router.get("${basePath}/resources/:datasetId").handler { ctx ->
            checkAccessible(ctx)
                    .andThen(Flowable.defer {
                        vertx.fileSystem().rxReadDir("$resourcePath/${ctx.pathParam("datasetId")}")
                                .flattenAsFlowable { it }
                                .flatMapSingle { path -> vertx.fileSystem().rxProps(path).map { ResourceDesc(path, it) } }
                    })
                    .toList()
                    .subscribeBy(
                            onSuccess = writeHttpResponse(ctx),
                            onError = writeHttpError(ctx)
                    )
        }
        // Get a specific resource
        router.get("${basePath}/resources/:datasetId/:resourceId").handler { ctx ->
            val datasetId = ctx.pathParam("datasetId")
            val resourceId = ctx.pathParam("resourceId")
            checkAccessible(ctx)
                    .andThen(Completable.defer { ctx.response().rxSendFile("$resourcePath/$datasetId/$resourceId") })
                    .onErrorResumeNext { err -> Completable.error(if (err is FileNotFoundException) NotFoundException("Resource $resourceId does not exists!") else err) }
                    .subscribeBy(onError = writeHttpError(ctx))
        }
        // Users with MANAGE permissions can delete a resource
        router.delete("${basePath}/resources/:datasetId/:resourceId").handler { ctx ->
            val datasetId = ctx.pathParam("datasetId")
            val resourceId = ctx.pathParam("resourceId")
            checkManagePermissions(ctx)
                    .andThen(Completable.defer { vertx.fileSystem().rxDelete("$resourcePath/$datasetId/$resourceId") })
                    .onErrorResumeNext { err -> Completable.error(if (err.cause is NoSuchFileException) NotFoundException("Resource $resourceId does not exists!") else err) }
                    .subscribeBy(
                            onComplete = { ctx.response().setStatusCode(204).end() },
                            onError = writeHttpError(ctx)
                    )
        }
    }


    private fun checkManagePermissions(ctx: RoutingContext): Completable {
        return accessManager.getToken(ctx.request())
                .flatMapCompletable { token ->
                    if (token.user.platformManager || token.grants[ctx.pathParam("datasetId")]?.permissions?.contains(Permission.MANAGE) == true) {
                        Completable.complete()
                    } else {
                        Completable.error(AuthorizationException("This operation requires MANAGE permission!"))
                    }
                }
    }

    private fun checkAccessible(ctx: RoutingContext): Completable {
        val resourceId: String? = ctx.pathParam("resourceId")
        return if (resourceId != null && "thumbnail.png" == resourceId) {
            // Everyone (even not logged in users) should be able to see thumbnails
            Completable.complete()
        } else {
            accessManager.getToken(ctx.request())
                    .flatMapCompletable { token ->
                        val datasetId = ctx.pathParam("datasetId")
                        if (token.user.platformManager || token.grants.containsKey(datasetId)) {
                            Completable.complete()
                        } else {
                            metaStore.getDataset(datasetId).flatMapCompletable { if (it.published) Completable.complete() else Completable.error(AuthorizationException("Inaccessible resource: the dataset is not public and you are not a member!")) }
                        }
                    }
        }
    }

    private fun handleUpload(ctx: RoutingContext, upload: FileUpload): Completable {
        val datasetId = ctx.pathParam("datasetId")
        val resourceType = ctx.request().params()["resourceType"]!!.let { ResourceType.valueOf(it.toUpperCase()) }
        val overwrite = ctx.request().params()["overwrite"]?.let { it.toBoolean() } ?: false
        val targetFileName = when (resourceType) {
            ResourceType.BANNER -> "banner.png"
            ResourceType.README -> "README.MD"
            ResourceType.THUMBNAIL -> "thumbnail.png"
        }
        val path = "$resourcePath/$datasetId/$targetFileName"

        return vertx.fileSystem().rxExists(path)
                .flatMapCompletable { alreadyExists ->
                    if (!alreadyExists || overwrite) {
                        if (tika.detect(upload.fileName()) == tika.detect(targetFileName)) {
                            vertx.fileSystem().rxMkdirs("$resourcePath/$datasetId")
                                    .andThen(Completable.defer { vertx.fileSystem().rxMove(upload.uploadedFileName(), path, CopyOptions().setReplaceExisting(true)) })
                        } else {
                            Completable.error(BadRequestException("Incompatible MIME-type, uploaded ${upload.fileName()} cannot be used as $targetFileName!"))
                        }
                    } else {
                        Completable.error(BadRequestException("A resource with the name $targetFileName already exists! Use the overwrite query parameter if you are sure you want to overwrite this resource!"))
                    }
                }
    }
}

private enum class ResourceType {
    THUMBNAIL, BANNER, README
}

private data class ResourceDesc(val timestamp: Long, val fileName: String, val mimeType: String, val sizeBytes: Long) {
    constructor(path: String, properties: FileProps) : this(
            timestamp = properties.lastModifiedTime(),
            fileName = path.substringAfterLast(File.separator),
            mimeType = tika.detect(path),
            sizeBytes = properties.size()
    )
}