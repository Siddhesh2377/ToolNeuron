package com.dark.plugin_exc.catalog

import kotlinx.serialization.json.Json
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

class PluginCatalogClient(
    private val baseUrl: String = DEFAULT_BASE_URL,
    private val supportedSchemaVersion: Int = 1,
) {

    private val json = Json { ignoreUnknownKeys = true }

    fun fetchCatalog(): PluginCatalog {
        val url = "$baseUrl/plugins.json"
        val body = openConnection(url).use { conn ->
            val code = conn.responseCode
            if (code !in 200..299) throw IOException("HTTP $code fetching catalog")
            conn.inputStream.bufferedReader().use { it.readText() }
        }
        val parsed = json.decodeFromString(PluginCatalog.serializer(), body)
        if (parsed.schemaVersion > supportedSchemaVersion) {
            throw IOException(
                "Catalog schemaVersion ${parsed.schemaVersion} > supported $supportedSchemaVersion. Update the app."
            )
        }
        return parsed
    }

    fun downloadAndVerify(
        entry: CatalogEntry,
        dest: File,
        onProgress: (downloaded: Long, total: Long) -> Unit = { _, _ -> },
    ) {
        dest.parentFile?.mkdirs()
        if (dest.exists()) dest.delete()
        val url = "$baseUrl/${entry.download}"
        val digest = MessageDigest.getInstance("SHA-256")
        var downloaded = 0L
        val total = entry.size
        openConnection(url).use { conn ->
            val code = conn.responseCode
            if (code !in 200..299) throw IOException("HTTP $code downloading ${entry.id}")
            conn.inputStream.use { input ->
                dest.outputStream().use { output ->
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        output.write(buf, 0, n)
                        digest.update(buf, 0, n)
                        downloaded += n
                        onProgress(downloaded, total)
                    }
                }
            }
        }
        val actual = digest.digest().joinToString("") { "%02x".format(it) }
        if (!actual.equals(entry.sha256, ignoreCase = true)) {
            dest.delete()
            throw IOException("sha256 mismatch: expected ${entry.sha256}, got $actual")
        }
    }

    private fun openConnection(url: String): HttpURLConnection {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = CONNECT_TIMEOUT_MS
        conn.readTimeout = READ_TIMEOUT_MS
        conn.instanceFollowRedirects = true
        conn.useCaches = false
        conn.defaultUseCaches = false
        conn.setRequestProperty("Cache-Control", "no-cache, no-store, max-age=0")
        conn.setRequestProperty("Pragma", "no-cache")
        return conn
    }

    private inline fun <T> HttpURLConnection.use(block: (HttpURLConnection) -> T): T {
        try {
            return block(this)
        } finally {
            runCatching { disconnect() }
        }
    }

    companion object {
        const val DEFAULT_BASE_URL =
            "https://huggingface.co/Void2377/tool-neuron-plugins/resolve/main"
        private const val CONNECT_TIMEOUT_MS = 15_000
        private const val READ_TIMEOUT_MS = 60_000
    }
}
