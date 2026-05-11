package com.dark.networking

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object WebNative {

    init {
        System.loadLibrary("networking")
    }

    @JvmStatic private external fun nativeSearch(
        query: String,
        userAgent: String,
        maxResults: Int,
        locale: String,
    ): Array<String>?

    @JvmStatic private external fun nativeHasBackend(): Boolean

    @JvmStatic private external fun nativeBackendName(): String

    @JvmStatic private external fun nativeSetCaBundle(path: String)

    @JvmStatic private external fun nativeSetProfile(profile: String)

    @JvmStatic private external fun nativeFetch(
        url: String,
        userAgent: String,
        timeoutMs: Int,
        headerKeys: Array<String>,
        headerVals: Array<String>,
    ): Array<String>?

    @JvmStatic private external fun nativeFetchBytes(
        url: String,
        userAgent: String,
        timeoutMs: Int,
        headerKeys: Array<String>,
        headerVals: Array<String>,
    ): Array<Any>?

    val isReady: Boolean by lazy { nativeHasBackend() }

    val backend: String by lazy { nativeBackendName() }

    fun setCaBundle(path: String) = nativeSetCaBundle(path)

    fun setImpersonateProfile(profile: String) = nativeSetProfile(profile)

    @Volatile private var caInstalled = false

    fun ensureReady(context: Context, profile: String = "chrome116") {
        if (caInstalled) return
        synchronized(this) {
            if (caInstalled) return
            val target = File(context.filesDir, "net_cacert.pem")
            if (!target.exists() || target.length() < 100_000L) {
                context.assets.open("cacert.pem").use { input ->
                    target.outputStream().use { output -> input.copyTo(output) }
                }
            }
            setCaBundle(target.absolutePath)
            setImpersonateProfile(profile)
            caInstalled = true
        }
    }

    suspend fun fetch(
        url: String,
        userAgent: String = DefaultUserAgent,
        timeoutMs: Int = 15000,
        headers: Map<String, String> = emptyMap(),
    ): Result<WebResponse> = withContext(Dispatchers.IO) {
        runCatching {
            val keys = if (headers.isEmpty()) EmptyStrArr else headers.keys.toTypedArray()
            val vals = if (headers.isEmpty()) EmptyStrArr else headers.values.toTypedArray()
            val arr = nativeFetch(url, userAgent, timeoutMs, keys, vals)
                ?: throw IllegalStateException("native fetch returned null: $url")
            require(arr.size == 3) { "malformed native response size=${arr.size}" }
            val status = arr[0].toIntOrNull() ?: 0
            val body = arr[1]
            val err = arr[2].takeIf { it.isNotEmpty() }
            WebResponse(status = status, body = body, error = err)
        }
    }

    suspend fun fetchBytes(
        url: String,
        userAgent: String = DefaultUserAgent,
        timeoutMs: Int = 30000,
        headers: Map<String, String> = emptyMap(),
    ): Result<WebBytesResponse> = withContext(Dispatchers.IO) {
        runCatching {
            val keys = if (headers.isEmpty()) EmptyStrArr else headers.keys.toTypedArray()
            val vals = if (headers.isEmpty()) EmptyStrArr else headers.values.toTypedArray()
            val arr = nativeFetchBytes(url, userAgent, timeoutMs, keys, vals)
                ?: throw IllegalStateException("native fetchBytes returned null: $url")
            require(arr.size == 4) { "malformed native bytes response size=${arr.size}" }
            val status = (arr[0] as String).toIntOrNull() ?: 0
            val body = arr[1] as ByteArray
            val err = (arr[2] as String).takeIf { it.isNotEmpty() }
            val ct = (arr[3] as String).takeIf { it.isNotEmpty() }
            WebBytesResponse(status = status, body = body, error = err, contentType = ct)
        }
    }

    suspend fun search(
        query: String,
        userAgent: String = DefaultUserAgent,
        maxResults: Int = 10,
        locale: String = "",
    ): Result<List<WebSearchResult>> = withContext(Dispatchers.IO) {
        runCatching {
            val raw = nativeSearch(query, userAgent, maxResults, locale)
                ?: return@runCatching emptyList()
            require(raw.size % 3 == 0) { "malformed native array size=${raw.size}" }
            List(raw.size / 3) { idx ->
                val base = idx * 3
                WebSearchResult(
                    title = raw[base],
                    url = raw[base + 1],
                    snippet = raw[base + 2],
                )
            }
        }
    }

    private val EmptyStrArr = emptyArray<String>()

    const val DefaultUserAgent: String =
        "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"
}
