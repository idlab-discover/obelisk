package idlab.obelisk.plugins.accessmanager.basic.store.model

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonTypeInfo

@JsonTypeInfo(use = JsonTypeInfo.Id.CLASS, include = JsonTypeInfo.As.PROPERTY)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
data class Session(
    /**
     * The sub claims for whom this session is created
     */
    val sub: String,

    /**
     * idToken in readable form (map of all claims)
     */
    val idToken: Map<String, Any>? = null,
    /**
     * id_token in compact string form (as delivered to user/client)
     */
    val id_token: String? = null,
    /**
     * refresh_token in string form
     */
    val refresh_token: String? = null,
    /**
     * key for retrieving the LoginState for the AuthStore
     */
    val loginStateKey: String? = null,
    /**
     * The access_token as sent to the user. Might be outdated.
     * **NEVER USE THIS ONE TO SEND TO THE USER! Always check if it exists in redis first!**
     */
    val access_token: String? = null,

    )
