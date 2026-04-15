package com.memoryvault

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

class BackupManager(private val vaultFile: File) {

    suspend fun backup(destination: File, compress: Boolean = true): BackupResult = withContext(Dispatchers.IO) {
        if (!vaultFile.exists()) {
            return@withContext BackupResult(false, 0, "Vault file does not exist")
        }

        destination.parentFile?.mkdirs()

        val startTime = System.currentTimeMillis()
        val originalSize = vaultFile.length()

        if (compress) {
            FileInputStream(vaultFile).use { input ->
                FileOutputStream(destination).use { output ->
                    GZIPOutputStream(output).use { gzip ->
                        input.copyTo(gzip)
                    }
                }
            }
        } else {
            vaultFile.copyTo(destination, overwrite = true)
        }

        val backupSize = destination.length()
        val duration = System.currentTimeMillis() - startTime

        BackupResult(
            success = true,
            durationMs = duration,
            message = "Backup completed successfully",
            originalSize = originalSize,
            backupSize = backupSize
        )
    }

    suspend fun restore(backup: File, decompress: Boolean = true): BackupResult = withContext(Dispatchers.IO) {
        if (!backup.exists()) {
            return@withContext BackupResult(false, 0, "Backup file does not exist")
        }

        val startTime = System.currentTimeMillis()
        val backupSize = backup.length()

        if (decompress) {
            FileInputStream(backup).use { input ->
                GZIPInputStream(input).use { gzip ->
                    FileOutputStream(vaultFile).use { output ->
                        gzip.copyTo(output)
                    }
                }
            }
        } else {
            backup.copyTo(vaultFile, overwrite = true)
        }

        val restoredSize = vaultFile.length()
        val duration = System.currentTimeMillis() - startTime

        BackupResult(
            success = true,
            durationMs = duration,
            message = "Restore completed successfully",
            originalSize = backupSize,
            backupSize = restoredSize
        )
    }
}

data class BackupResult(
    val success: Boolean,
    val durationMs: Long,
    val message: String,
    val originalSize: Long = 0,
    val backupSize: Long = 0
)
