package idlab.obelisk.services.pub.auth.token

import idlab.obelisk.definitions.framework.OblxConfig
import idlab.obelisk.plugins.accessmanager.basic.store.model.AuthType
import idlab.obelisk.services.pub.auth.HTTP_BASE_PATH
import idlab.obelisk.services.pub.auth.keys.KeyStore
import idlab.obelisk.services.pub.auth.model.Claims
import idlab.obelisk.utils.service.utils.TTL
import mu.KotlinLogging
import org.jose4j.jwa.AlgorithmConstraints
import org.jose4j.jwk.PublicJsonWebKey
import org.jose4j.jws.JsonWebSignature
import org.jose4j.jwt.consumer.InvalidJwtException
import org.jose4j.jwt.consumer.JwtConsumer
import org.jose4j.jwt.consumer.JwtConsumerBuilder
import javax.inject.Inject

class Jose4JTokenMinter : TokenMinter {
    private val keyStore: KeyStore
    private val config: OblxConfig

    private val sessionTTL: TTL
    private val keyInUse: PublicJsonWebKey
    private val jwtConsumer: JwtConsumer

    private val issuer: String

    private val logger = KotlinLogging.logger { }

    @Inject
    constructor(keyStore: KeyStore, config: OblxConfig) {
        this.keyStore = keyStore;
        this.config = config;

        sessionTTL = TTL(config.getString(TTL.SESSION_TTL_PROP, TTL.SESSION_TTL))
        val basePath = config.getString(OblxConfig.HTTP_BASE_PATH_PROP, HTTP_BASE_PATH)
        issuer = config.authPublicUri + basePath

        keyInUse = keyStore.getYoungestKey()
        jwtConsumer = setUpJwtConsumer()
    }

    override fun mintToken(claims: Claims, sessionId: String): String {
        val userEmail = claims.email
        var audience: String
        if (claims.type == AuthType.USER.subject) {
            audience = userEmail
        } else if (claims.type == AuthType.CLIENT.subject) {
            audience = claims.name
        } else {
            audience = "*"
        }
        // Set the required and optional claims
        val jwtClaims = claims.toJwtClaims()
        // Set the mandatory claims
        jwtClaims.issuer = issuer
        jwtClaims.audience = listOf(audience)
        jwtClaims.setExpirationTimeMinutesInTheFuture(sessionTTL.inMinutes().toFloat())
        jwtClaims.setGeneratedJwtId()
        jwtClaims.setIssuedAtToNow()
        jwtClaims.setNotBeforeMinutesInThePast(2f)
        jwtClaims.subject = claims.sub

        // Session id
        jwtClaims.setStringClaim("sid", sessionId)

        // Setup signing
        val key = keyStore.getYoungestKey()
        val jws = JsonWebSignature()
        jws.payload = jwtClaims.toJson()
        jws.key = key.privateKey
        jws.keyIdHeaderValue = key.keyId
        jws.setHeader("typ", "JWT")
        jws.algorithmHeaderValue = key.algorithm
        return jws.compactSerialization
    }

    override fun isTokenValid(encodedToken: String): Boolean {
        try {
            jwtConsumer.process(encodedToken)
            return true
        } catch (ex: InvalidJwtException) {
            logger.error { ex }
            return false
        }
    }

    @Throws(InvalidJwtException::class)
    override fun verifyAndProcessToken(encodedToken: String): Map<String, Any> {
        return jwtConsumer.processToClaims(encodedToken).claimsMap
    }

    private fun setUpJwtConsumer(): JwtConsumer {
        return JwtConsumerBuilder()
            .setRequireExpirationTime()
            .setAllowedClockSkewInSeconds(30)
            .setRequireSubject()
            .setExpectedIssuer(issuer)
            .setSkipDefaultAudienceValidation()
            .setVerificationKey(keyInUse.key)
            .setJwsAlgorithmConstraints(AlgorithmConstraints.ConstraintType.PERMIT, keyInUse.algorithm)
            .build()
    }
}