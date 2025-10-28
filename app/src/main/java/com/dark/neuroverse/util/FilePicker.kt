package com.dark.neuroverse.util

import android.content.Context
import java.io.File
import java.io.FileOutputStream

fun copyAssetToFile(
    context: Context, assetName: String, destDir: File, destName: String = assetName
): File? {
    return try {
        // Ensure destination directory exists
        if (!destDir.exists()) destDir.mkdirs()

        val destFile = File(destDir, destName)

        context.assets.open(assetName).use { input ->
            FileOutputStream(destFile).use { output ->
                val buffer = ByteArray(8192)
                var bytesRead: Int
                while (input.read(buffer).also { bytesRead = it } != -1) {
                    output.write(buffer, 0, bytesRead)
                }
                output.flush()
            }
        }
        destFile
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}