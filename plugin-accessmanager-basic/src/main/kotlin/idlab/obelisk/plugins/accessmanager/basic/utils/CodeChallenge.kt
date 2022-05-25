package idlab.obelisk.plugins.accessmanager.basic.utils

import jodd.util.DigestEngine
import java.security.NoSuchAlgorithmException
import java.util.*

class CodeChallenge {
    enum class Algorithm(val text: String) {
        S256("S256"), PLAIN("plain")
    }

    val hash: String

    companion object {
        /**
         * Calculate appropriate code_challenge hash with the given method
         *
         * @param codeVerifier CodeVerifier string to use
         * @param algorithm Method to use
         */
        fun calcCodeChallenge(codeVerifier: String, algorithm: Algorithm): String {
            return when (algorithm) {
                Algorithm.PLAIN -> codeVerifier
                Algorithm.S256 -> base64urlEncode(S256(codeVerifier))
                else -> throw NoSuchAlgorithmException("No such algorithm supported for code_challenge hashing")
            }
        }

        /**
         * SHA-256 encoding
         */
        fun S256(codeVerifier: String): ByteArray {
            return DigestEngine.sha256().digest(codeVerifier)
        }

        /**
         * Base64url encoder without padding
         */
        fun base64urlEncode(arg: ByteArray): String {
            return Base64.getUrlEncoder().withoutPadding().encodeToString(arg);
        }

    }

    /**
     * Creates a code_challenge from this codeVerifier with the given algorithm
     */
    constructor(codeVerifier: String, algorithm: Algorithm) {
        this.hash = calcCodeChallenge(codeVerifier, algorithm)
    }

    /**
     * Creates a code_challenge from this codeVerifier with the given algorithm as string
     *
     * @throws IllegalArgumentException If algorithm does not exist
     */
    constructor(codeVerifier: String, algorithmString: String) : this(
        codeVerifier,
        Algorithm.valueOf(algorithmString)
    ) {
    }

    fun matches(otherCodeChallenge: String): Boolean {
        return hash == otherCodeChallenge;
    }

    override fun toString(): String {
        return hash
    }
}
