package idlab.obelisk.definitions

/**
 * Use this Exception whenever you want to indicate one of the following:
 * - an insert or update results in a unique constraint failure
 * - ... (add whenever you encounter a fresh use for this exception)
 */
class AlreadyExistsException(message: String, throwable: Throwable? = null) :
    IllegalArgumentException(message, throwable)

/**
 * Use this Exception whenever you could not perform the desired operation due to a user's passed arguments, e.g.,
 * - delete a tuple that is not there
 */
class CouldNotPerformOperationException(message: String, throwable: Throwable? = null) :
    IllegalArgumentException(message, throwable)

/**
 * Use this to indicate that a certain [DataRange] is invalid.
 */
class InvalidDataRangeException(dataRange: DataRange, reason: String, throwable: Throwable? = null) :
    IllegalArgumentException("The DataRange $dataRange is invalid ($reason)", throwable)

class DataStoreQueryTimeoutException(throwable: Throwable? = null) :
    IllegalArgumentException(
        "The query took too long to process and has been aborted. Try limiting the data to process, e.g. by adding \"from\" or \"to\" timestamp boundaries.",
        throwable
    )