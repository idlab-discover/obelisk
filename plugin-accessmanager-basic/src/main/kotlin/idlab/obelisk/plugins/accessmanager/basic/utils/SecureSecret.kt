package idlab.obelisk.plugins.accessmanager.basic.utils

import org.mindrot.jbcrypt.BCrypt

object SecureSecret {

    fun hash(plainSecret: String): String {
        return BCrypt.hashpw(plainSecret, BCrypt.gensalt())
    }

    fun isValid(secretCandidate: String, hashedSecret: String?): Boolean {
        return hashedSecret != null && BCrypt.checkpw(secretCandidate, hashedSecret)
    }

}