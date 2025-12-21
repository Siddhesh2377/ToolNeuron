package com.mp.ai_engine.util

import android.util.Log
import java.io.File
import java.util.zip.ZipInputStream

fun unzipFile(zipFile: File, outputDir: File) {
    Log.d("File Util", "Unzipping ${zipFile.name} to ${outputDir.absolutePath}")

    if (!zipFile.exists()) {
        throw IllegalArgumentException("Zip file does not exist: ${zipFile.absolutePath}")
    }

    // Ensure output directory exists
    if (!outputDir.exists()) {
        outputDir.mkdirs()
    }

    zipFile.inputStream().use { fileInputStream ->
        ZipInputStream(fileInputStream).use { zipInputStream ->
            var entry = zipInputStream.nextEntry

            while (entry != null) {
                val entryFile = File(outputDir, entry.name)

                // Security check: prevent zip slip attack
                val canonicalDestPath = outputDir.canonicalPath
                val canonicalEntryPath = entryFile.canonicalPath

                if (!canonicalEntryPath.startsWith(canonicalDestPath + File.separator)) {
                    throw SecurityException("Entry is outside of the target directory: ${entry.name}")
                }

                if (entry.isDirectory) {
                    // Create directory
                    entryFile.mkdirs()
                    Log.v("File Util", "Created directory: ${entryFile.name}")
                } else {
                    // Ensure parent directory exists
                    entryFile.parentFile?.mkdirs()

                    // Extract file
                    entryFile.outputStream().use { outputStream ->
                        zipInputStream.copyTo(outputStream)
                    }
                    Log.v("File Util", "Extracted file: ${entryFile.name} (${entryFile.length()} bytes)")
                }

                zipInputStream.closeEntry()
                entry = zipInputStream.nextEntry
            }
        }
    }

    Log.i("File Util", "Successfully unzipped ${zipFile.name}")
}