package idlab.obelisk.services.pub.auth.token

import idlab.obelisk.services.pub.auth.model.Claims
import org.jose4j.jwt.JwtClaims
import org.jose4j.jwt.consumer.InvalidJwtException

interface TokenMinter {


    /**
     * Mint a token based on a Claims object.
     * @param claims: The claims to be included
     * @param sessionId: The sessionId to encode in this token
     * @return The encodedToken string.
     */
    fun mintToken(claims: Claims, sessionId: String): String

    /**
     * Just verify the encodedToken string.
     */
    fun isTokenValid(encodedToken: String): Boolean

    /**
     * First verify the encodedToken string, then return the contained claims as a map
     */
    @Throws(InvalidJwtException::class)
    fun verifyAndProcessToken(encodedToken: String): Map<String, Any>

}