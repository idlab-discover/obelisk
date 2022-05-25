package idlab.obelisk.services.pub.auth.model

import idlab.obelisk.plugins.accessmanager.basic.store.model.AuthType
import io.vertx.core.json.Json
import org.jose4j.jwt.JwtClaims

class Claims {
    val entries: MutableMap<String, Any>;

    /**
     * Unique identifier for this User or Client
     */
    val sub: String
        get() = entries.get("sub") as String
    val name: String
        get() = entries.get("name") as String
    val email: String
        get() = entries.get("email") as String
    val type: String
        get() = entries.get("type") as String
    val idp: String
        get() = entries.get("idp") as String

    companion object {
        fun forUser(
            userId: String,
            firstName: String,
            lastName: String,
            email: String,
            idp: String,
            name: String? = null,
            otherClaims: MutableMap<String, Any> = mutableMapOf()
        ): Claims {
            otherClaims["firstName"] = firstName
            otherClaims["lastName"] = lastName
            if (name != null) {
                otherClaims["name"] = name
            }
            return Claims(
                sub = generateSub(AuthType.USER, userId),
                name = name ?: ("$firstName $lastName"),
                email = email,
                idp = idp,
                type = AuthType.USER,
                optionalClaims = otherClaims
            )
        }

        fun forClient(
            clientId: String,
            name: String,
            ownerEmail: String,
            idp: String,
            otherClaims: MutableMap<String, Any> = mutableMapOf()
        ): Claims {
            return Claims(
                sub= generateSub(AuthType.CLIENT, clientId),
                name = name,
                email = ownerEmail,
                idp = idp,
                type = AuthType.CLIENT,
                optionalClaims = otherClaims
            )
        }

        private fun generateSub(type: AuthType, id: String): String {
            return type.subject.get(0) + id
        }

    }

    private constructor(
        sub: String,
        name: String,
        email: String,
        idp: String,
        type: AuthType,
        optionalClaims: MutableMap<String, Any>? = null
    ) {
        entries = optionalClaims ?: mutableMapOf()
        entries["sub"] = sub
        entries["name"] = name
        entries["email"] = email
        entries["idp"] = idp
        entries["type"] = type.subject
    }

    fun toJwtClaims(): JwtClaims {
        return JwtClaims.parse(Json.encode(entries))
    }

    fun toMap(): Map<String, Any> {
        return toJwtClaims().claimsMap
    }
}