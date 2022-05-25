package idlab.obelisk.services.pub.auth.oidc

import idlab.obelisk.services.pub.auth.oidc.provider.IdentityProvider

interface OpenIdConnector {
    /**
     * Return a list of all registered IdentityProviders
     * @return
     */
    val identityProviders: List<IdentityProvider>


    /**
     * Return IdentityProvider based on name.
     * @param id id of the IdentityProvider
     * @return Optional of IdentityProvider, which might be empty if not found.
     */
    fun getIdentityProvider(id: String): IdentityProvider?
}