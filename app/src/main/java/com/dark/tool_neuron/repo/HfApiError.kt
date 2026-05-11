package com.dark.tool_neuron.repo

import com.dark.networking.WebResponse

sealed class HfApiError(message: String) : Throwable(message) {
    class RateLimited(val retryAfterSeconds: Int?) :
        HfApiError("Rate limit reached" + (retryAfterSeconds?.let { ", try again in ${it}s" } ?: ""))

    class NotFound(val url: String) : HfApiError("Repository not found")

    class Forbidden(val url: String) : HfApiError("Access denied")

    class Network(reason: String) : HfApiError("Network error: $reason")

    class Parse(reason: String) : HfApiError("Couldn't parse response: $reason")

    class Http(val status: Int) : HfApiError("HTTP $status")

    companion object {
        fun fromResponse(resp: WebResponse, url: String): HfApiError {
            val transport = resp.error
            if (transport != null && resp.status == 0) return Network(transport)
            return when (resp.status) {
                404 -> NotFound(url)
                401, 403 -> Forbidden(url)
                429 -> RateLimited(parseRetryAfter(resp.body))
                in 500..599 -> Http(resp.status)
                else -> Http(resp.status)
            }
        }

        private fun parseRetryAfter(body: String): Int? {
            val idx = body.indexOf("retry", ignoreCase = true)
            if (idx < 0) return null
            val tail = body.substring(idx).take(64)
            val digits = tail.filter { it.isDigit() }.take(6)
            return digits.toIntOrNull()
        }
    }
}
