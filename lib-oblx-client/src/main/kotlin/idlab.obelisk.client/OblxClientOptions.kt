package idlab.obelisk.client

data class OblxClientOptions(
    val apiUrl: String,
    val clientId: String,
    val secret: String,
    val virtualHost: String? = null,
    val requestTimeoutMs: Long = 5000
) {
    val safeApiUrl: String = apiUrl.trimEnd('/')
}