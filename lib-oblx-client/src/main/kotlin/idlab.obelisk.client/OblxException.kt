package idlab.obelisk.client

import java.lang.RuntimeException

data class OblxException(val status: Int, override val message: String? = null, val details: List<String>? = null) : RuntimeException(message)