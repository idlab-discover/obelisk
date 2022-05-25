package idlab.obelisk.utils.service.utils

import idlab.obelisk.definitions.*
import idlab.obelisk.definitions.catalog.Permission
import idlab.obelisk.definitions.catalog.Token
import idlab.obelisk.utils.service.http.AuthorizationException

fun <Q : DataRequest> Q.applyToken(token: Token): Q {
    val extraFilters = mutableListOf<FilterExpression>()

    if (this.dataRange.datasets.isEmpty()) {
        throw IllegalArgumentException("Datasets in DataRange should not be empty!")
    }

    if (token.client != null && !token.client!!.scope.contains(Permission.READ)) {
        throw AuthorizationException("Client is not authorized to perform reads (check client scope!)")
    }

    if (!token.user.platformManager) {
        for (dataset in this.dataRange.datasets) {
            val grant = token.grants.get(dataset)
            if (grant != null && grant.permissions.contains(Permission.READ)) {
                if (grant.readFilter != SELECT_ALL) {
                    extraFilters.add(Or(Neq(EventField.dataset.toString(), dataset), grant.readFilter))
                }
            } else {
                throw AuthorizationException("Read access denied on dataset $dataset")
            }
        }
    }

    if (extraFilters.isNotEmpty()) {
        this.filter = And(this.filter, *extraFilters.toTypedArray())
    }
    return this
}