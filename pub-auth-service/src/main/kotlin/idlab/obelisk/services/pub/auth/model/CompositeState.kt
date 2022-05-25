package idlab.obelisk.services.pub.auth.model

import java.net.URLDecoder
import java.net.URLEncoder

/**
 * Stores multiple objects as one state string. Call encode() and (static) decode() functions to use.
 */
class CompositeState  {
    val state: String?
    val rememberMe: Boolean

    constructor(state: String?, rememberMe: Boolean) {
        this.state = state
        this.rememberMe = rememberMe
    }

    constructor(state: String?, remember_me: String): this(state, remember_me.toBoolean())

    companion object {
        /**
         * Decode into a CompositeState object form a url-safe string
         */
        fun decode(encodedString: String): CompositeState {
            val remember_me = encodedString[0].toString().equals("1")
            val encState = encodedString.drop(1);
            val state = if (encState.isEmpty()) null else URLDecoder.decode(encodedString.drop(1), Charsets.UTF_8)
            return CompositeState(state, remember_me)
        }
    }

    /**
     * Encode to a url-safe string
     */
    fun encode(): String {
        var enc = if (rememberMe) "1" else "0";
        if (state != null) {
            enc += URLEncoder.encode(state, Charsets.UTF_8)
        }
        return enc;
    }

    override fun toString(): String {
        return this.encode();
    }

}