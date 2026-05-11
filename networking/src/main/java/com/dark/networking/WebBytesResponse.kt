package com.dark.networking

data class WebBytesResponse(
    val status: Int,
    val body: ByteArray,
    val error: String? = null,
    val contentType: String? = null,
) {
    val isSuccess: Boolean get() = error == null && status in 200..399

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is WebBytesResponse) return false
        return status == other.status &&
            body.contentEquals(other.body) &&
            error == other.error &&
            contentType == other.contentType
    }

    override fun hashCode(): Int {
        var r = status
        r = 31 * r + body.contentHashCode()
        r = 31 * r + (error?.hashCode() ?: 0)
        r = 31 * r + (contentType?.hashCode() ?: 0)
        return r
    }
}
