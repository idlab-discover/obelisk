package idlab.obelisk.plugins.accessmanager.basic.store.model

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonTypeInfo

@JsonTypeInfo(use = JsonTypeInfo.Id.CLASS, include = JsonTypeInfo.As.PROPERTY)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
data class LoginState(
    val sub: String? = null,
    val remember_me: Boolean = false,
    val clientId: String? = null,
    val codeChallenge: String? = null,
    val codeChallengeMethod: String? = null,
    val responseType: String? = null,
    val redirectUri: String? = null,
    val state: String? = null,
    val onBehalfOfUser: Boolean = true
)
