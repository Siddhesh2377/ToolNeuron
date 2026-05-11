package com.dark.networking

data class WebResponse(
    val status: Int,
    val body: String,
    val error: String? = null,
) {
    val isSuccess: Boolean get() = error == null && status in 200..399

    fun retryAfterSeconds(headers: Map<String, String>?): Int? {
        if (status != 429) return null
        val raw = headers?.entries?.firstOrNull { it.key.equals("retry-after", true) }?.value
        return raw?.trim()?.toIntOrNull()
    }
}
