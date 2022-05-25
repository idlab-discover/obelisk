package idlab.obelisk.utils.service.utils

import java.util.Base64

object Base64 {

    fun encode(value: String): String {
        return Base64.getEncoder().encodeToString(value.toByteArray())
    }

    fun decode(value: String): String {
        return String(Base64.getDecoder().decode(value))
    }

    fun String.encodeAsBase64(): String {
        return encode(this)
    }

    fun String.decodeFromBase64(): String {
        return decode(this)
    }

}