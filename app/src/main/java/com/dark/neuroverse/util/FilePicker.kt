package com.dark.neuroverse.util

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import java.io.File
import java.io.FileOutputStream

fun uriToFile(context: Context, uri: Uri): File {
    val fileName = queryDisplayName(context.contentResolver, uri) ?: "temp.zip"
    val tempFile = File(context.cacheDir, fileName)

    context.contentResolver.openInputStream(uri)?.use { input ->
        FileOutputStream(tempFile).use { output ->
            input.copyTo(output)
        }
    }
    return tempFile
}

// ---- Helpers ----
fun queryDisplayName(resolver: ContentResolver, uri: Uri): String? =
    resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
        ?.use { c -> if (c.moveToFirst()) c.getString(0) else null }

fun isZipByName(name: String?): Boolean = name?.lowercase()?.endsWith(".zip") == true

fun isZipByMime(mime: String?): Boolean =
    mime == "application/zip" || mime == "application/x-zip-compressed"

// Check magic number: PK\x03\x04
fun isZipByHeader(resolver: ContentResolver, uri: Uri): Boolean = try {
    resolver.openInputStream(uri)?.use { ins ->
        val sig = ByteArray(4)
        val n = ins.read(sig)
        n == 4 && sig[0] == 'P'.code.toByte() && sig[1] == 'K'.code.toByte() && sig[2] == 3.toByte() && sig[3] == 4.toByte()
    } ?: false
} catch (_: Exception) {
    false
}
