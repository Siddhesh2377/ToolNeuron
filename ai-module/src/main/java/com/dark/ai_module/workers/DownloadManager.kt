package com.dark.ai_module.workers

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

suspend fun downloadFile(
    fileUrl: String,
    outputFile: File,
    onProgress: (Float) -> Unit,
    onComplete: () -> Unit,
    onError: (Exception) -> Unit
) {
    withContext(Dispatchers.IO) {
        try {
            if (outputFile.exists()) {
                outputFile.delete()
            }
            // First, try to get the size with HEAD
            val headConn = (URL(fileUrl).openConnection() as HttpURLConnection).apply {
                requestMethod = "HEAD"
                connect()
            }
            val fileLength = headConn.getHeaderFieldLong("Content-Length", -1)
            headConn.disconnect()

            val conn = (URL(fileUrl).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connect()
            }
            if (conn.responseCode != HttpURLConnection.HTTP_OK) {
                throw Exception("Server returned HTTP ${conn.responseCode}")
            }

            val input = conn.inputStream
            val output = FileOutputStream(outputFile)
            val data = ByteArray(4096)
            var total: Long = 0
            var count: Int
            while (input.read(data).also { count = it } != -1) {
                // Cancellation check
                ensureActive()

                total += count
                output.write(data, 0, count)
                if (fileLength > 0) {
                    onProgress(total.toFloat() / fileLength.toFloat())
                } else {
                    onProgress(-1f)
                }
            }
            output.flush()
            output.close()
            input.close()
            onComplete()
        } catch (e: Exception) {
            onError(e)
        }
    }
}