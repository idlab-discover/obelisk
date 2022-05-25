package idlab.obelisk.services.pub.auth.oidc

import idlab.obelisk.services.pub.auth.oidc.provider.IdentityProvider
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

@Singleton
class OpenIdConnectorImpl : OpenIdConnector {
    private var providerMap: MutableMap<String, IdentityProvider> = HashMap()

    @Inject
    constructor(@Named("google") idpGoogle: IdentityProvider, @Named("local") idpLocal: IdentityProvider) {
        providerMap[idpGoogle.id] = idpGoogle
        providerMap[idpLocal.id] = idpLocal
    }

    override val identityProviders: List<IdentityProvider>
        get() = providerMap.values.toList()

    override fun getIdentityProvider(id: String): IdentityProvider? {
        return providerMap[id]
    }
}