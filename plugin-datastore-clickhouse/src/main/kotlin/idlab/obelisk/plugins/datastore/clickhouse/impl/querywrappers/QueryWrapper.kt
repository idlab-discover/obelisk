package idlab.obelisk.plugins.datastore.clickhouse.impl.querywrappers

import idlab.obelisk.definitions.PagedResult
import io.reactivex.Completable
import io.reactivex.Single
import io.vertx.reactivex.jdbcclient.JDBCPool
import io.vertx.reactivex.sqlclient.Row

internal interface QueryWrapper<T> {

    fun selectStatement(): String

    fun processResult(record: Row): T

    fun toPagedResult(results: List<T>): PagedResult<T>

    fun execute(jdbcClient: JDBCPool): Single<PagedResult<T>> {
        return jdbcClient
            .query(selectStatement())
            .rxExecute()
            .map { res -> toPagedResult(res.map(this::processResult)) }
    }

    fun executeNoResult(jdbcClient: JDBCPool): Completable {
        return jdbcClient
            .query(selectStatement())
            .rxExecute()
            .ignoreElement()
    }
}
