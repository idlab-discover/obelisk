package idlab.obelisk.definitions.framework

import io.reactivex.Completable

interface OblxService {

    fun start(): Completable

    fun checkHealth(): Completable {
        return Completable.complete()
    }
}