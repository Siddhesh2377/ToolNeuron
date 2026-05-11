package com.dark.plugin_api.api

interface NetworkApi {
    suspend fun fetch(request: NetworkRequest): NetworkResponse
}

data class NetworkRequest(
    val url: String,
    val method: NetworkMethod = NetworkMethod.GET,
    val headers: Map<String, String> = emptyMap(),
    val body: ByteArray? = null,
    val timeoutMs: Long = 30_000,
)

data class NetworkResponse(
    val statusCode: Int,
    val headers: Map<String, String>,
    val body: ByteArray,
)

enum class NetworkMethod { GET, POST, PUT, DELETE, HEAD, PATCH }
