package idlab.obelisk.utils.service.utils

import idlab.obelisk.definitions.PagedResult
import io.reactivex.Completable
import io.reactivex.Flowable
import io.reactivex.Single

fun <R> unpage(cursor: String? = null, executor: (String?) -> Single<PagedResult<R>>): Flowable<R> {
    return executor.invoke(cursor).flatMapPublisher {
        if (it.cursor != null) {
            Flowable.fromIterable(it.items).concatWith(unpage(it.cursor, executor))
        } else {
            Flowable.fromIterable(it.items)
        }
    }
}

fun <R> pageAndProcess(
    cursor: String? = null,
    limit: Int? = null,
    executor: (String?) -> Single<PagedResult<R>>,
    processor: (PagedResult<R>) -> Completable
): Completable {
    return executor.invoke(cursor).flatMapCompletable { page ->
        if (page.cursor != null && (limit != null && (limit - page.items.size) > 0)) {
            processor.invoke(page)
                .concatWith(pageAndProcess(page.cursor, limit.minus(page.items.size), executor, processor))
        } else {
            val limitedPage = if (limit != null) page.copy(items = page.items.take(limit)) else page
            processor.invoke(limitedPage)
        }
    }
}