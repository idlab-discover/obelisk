package idlab.obelisk.services.pub.auth.util

data class Reader(var idToken: Map<String, Any>) {
    fun email(): String {
        return idToken["email"] as String
    }

    fun firstName(): String {
        return idToken["firstName"] as String
    }

    fun lastName(): String {
        return idToken["lastName"] as String
    }

    fun name(): String {
        return idToken["name"] as String
    }

    fun iss(): String {
        return idToken["iss"] as String
    }

    fun idp(): String {
        return idToken["idp"] as String
    }

    fun picture(): String {
        return idToken["picture"] as String
    }
}