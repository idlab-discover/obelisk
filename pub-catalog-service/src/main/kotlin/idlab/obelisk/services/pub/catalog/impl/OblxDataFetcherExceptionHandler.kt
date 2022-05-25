package idlab.obelisk.services.pub.catalog.impl

import graphql.ExceptionWhileDataFetching
import graphql.execution.*
import graphql.language.SourceLocation
import idlab.obelisk.utils.service.http.AuthorizationException
import idlab.obelisk.utils.service.http.BadRequestException
import idlab.obelisk.utils.service.http.NotFoundException
import idlab.obelisk.utils.service.http.UnauthorizedException
import mu.KotlinLogging
import java.util.concurrent.CompletionException
import kotlin.math.log


class OblxDataFetcherExceptionHandler : DataFetcherExceptionHandler {

    private val logger = KotlinLogging.logger { }


    override fun onException(handlerParameters: DataFetcherExceptionHandlerParameters): DataFetcherExceptionHandlerResult {
        val exception: Throwable = handlerParameters.exception
        val sourceLocation: SourceLocation = handlerParameters.sourceLocation
        val path: ResultPath = handlerParameters.path

        val error = ExceptionWhileDataFetching(path, exception, sourceLocation)
        logException(exception, path)

        return DataFetcherExceptionHandlerResult.newResult().error(error).build()
    }

    private fun logException(exception: Throwable, path: ResultPath) {
        if (exception is CompletionException && exception.cause != null) {
            logException(exception.cause!!, path)
        } else {
            when (exception) {
                is IllegalArgumentException, is BadRequestException -> logger.info(exception) { "Bad GraphQL request at path $path!" }
                is UnauthorizedException, is AuthorizationException -> logger.info(exception) { "Unauthorized GraphQL request (path: $path)!" }
                is NoSuchElementException, is NotFoundException -> logger.info(exception) { "GraphQL request subject not found (path: $path)!" }
                else -> logger.warn(exception) { "Unexpected error while executing a GraphQL request (path: $path)!" }
            }
        }
    }
}