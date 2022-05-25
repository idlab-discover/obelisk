package idlab.obelisk.utils.mongo

import com.mongodb.MongoCommandException
import com.mongodb.MongoWriteException
import idlab.obelisk.definitions.AlreadyExistsException
import idlab.obelisk.definitions.PagedResult
import idlab.obelisk.utils.mongo.query.withId
import idlab.obelisk.utils.service.utils.Base64
import io.reactivex.Completable
import io.reactivex.Flowable
import io.reactivex.Maybe
import io.reactivex.Single
import io.vertx.core.json.JsonObject
import io.vertx.ext.mongo.FindOptions
import io.vertx.reactivex.ext.mongo.MongoClient

fun MongoClient.rxSave(collection: String, instance: Any): Maybe<String> {
    return this.rxSave(collection, Conversions.escapeDocument(JsonObject.mapFrom(instance)))
}

/**
 * Returns a Maybe resulting in the generated id if the instance was created.
 * If the instance already existed and was updated, the Maybe will be empty!
 */
fun MongoClient.rxCreate(collection: String, instance: Any): Maybe<String> {
    return this.rxInsert(collection, Conversions.escapeDocument(JsonObject.mapFrom(instance)))
        .onErrorResumeNext { err: Throwable ->
            Maybe.error {
                if (err is MongoWriteException) {
                    AlreadyExistsException(
                        "Cannot create ${collection.capitalize().dropLast(1)}: ${err.message?.removePrefix("E11000 ")}",
                        err
                    )
                } else {
                    err
                }
            }
        }
}

fun MongoClient.rxUpdate(
    collection: String,
    objectId: String,
    instance: Any,
    nullFields: Set<String> = emptySet()
): Completable {
    val updateDoc = JsonObject()
    val instanceJson = Conversions.escapeDocument(JsonObject.mapFrom(instance))
    instanceJson.filter { it.value != null || nullFields.contains(it.key) }.forEach { updateDoc.put(it.key, it.value) }
    return if (updateDoc.isEmpty) {
        Completable.complete()
    } else {
        this.rxFindOneAndUpdate(collection, withId(objectId), JsonObject().put("\$set", updateDoc)).ignoreElement()
            .onErrorResumeNext { err: Throwable ->
                Completable.error {
                    if (err is MongoCommandException) {
                        AlreadyExistsException(
                            "Cannot update ${
                                collection.capitalize().dropLast(1)
                            }: ${err.errorMessage.removePrefix("E11000 ")}",
                            err
                        )
                    } else {
                        err
                    }
                }
            }
    }
}

fun <T> MongoClient.rxFindById(collection: String, objectId: String, type: Class<T>): Maybe<T> {
    return this.rxFindOne(collection, withId(objectId), JsonObject())
        .map { Conversions.unescapeDocument(it).mapTo(type) }
}

fun MongoClient.rxDeleteById(collection: String, objectId: String): Completable {
    return this.rxFindOneAndDelete(collection, withId(objectId))
        .toSingle()
        .ignoreElement()
}

fun <T> MongoClient.rxFind(collection: String, query: JsonObject, type: Class<T>): Flowable<T> {
    return this.rxFind(collection, query)
        .flattenAsFlowable { it }
        .map { Conversions.unescapeDocument(it).mapTo(type) }
}

fun <T> MongoClient.rxFindPaged(
    collection: String,
    query: JsonObject,
    type: Class<T>,
    limit: Int,
    cursor: String? = null,
    sortKey: JsonObject? = null
): Single<PagedResult<T>> {
    val offset = cursor?.let { Base64.decode(it).toInt() } ?: 0
    var findOptions = FindOptions().setSkip(offset).setLimit(limit + 1)
    if (sortKey != null) {
        findOptions = findOptions.setSort(sortKey)
    }
    return this.rxFindWithOptions(collection, query, findOptions)
        .map { results ->
            PagedResult(
                items = results.take(limit).map { Conversions.unescapeDocument(it).mapTo(type) },
                cursor = if (results.size == limit + 1) Base64.encode("${offset + limit}") else null
            )
        }
}