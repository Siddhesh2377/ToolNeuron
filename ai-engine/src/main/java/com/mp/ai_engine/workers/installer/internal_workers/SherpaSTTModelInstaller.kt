package com.mp.ai_engine.workers.installer.internal_workers

import android.util.Log
import com.mp.ai_engine.managers.SherpaSTTModelManager
import com.mp.ai_engine.models.llm_models.CloudModel
import com.mp.ai_engine.models.llm_models.ModelType
import com.mp.ai_engine.models.llm_models.toSherpaSTTModel
import com.mp.ai_engine.util.downloadFile
import com.mp.ai_engine.workers.installer.DownloadEvents
import com.mp.ai_engine.workers.installer.SuperInstaller
import java.io.File
import java.util.zip.ZipInputStream

/**
 * Installer for Sherpa STT models (typically directories with multiple files)
 */
class SherpaSTTModelInstaller : SuperInstaller() {

    override fun canHandle(cloudModel: CloudModel): Boolean {
        return cloudModel.modelType == ModelType.STT && cloudModel.providerName.contains("SHERPA", ignoreCase = true)
    }

    override fun determineOutputLocation(cloudModel: CloudModel, baseDir: File): File {
        // Sherpa STT models are typically directories
        return File(baseDir, cloudModel.modelName).also { it.mkdirs() }
    }

    override suspend fun downloadModel(
        cloudModel: CloudModel,
        downloadUrl: String,
        outputLocation: File,
        downloadEvents: DownloadEvents
    ) {
        try {
            // For Sherpa STT, we might download a zip file first
            val tempFile = File(outputLocation.parentFile, "${cloudModel.modelName}.zip")

            downloadFile(
                fileUrl = downloadUrl,
                outputFile = tempFile,
                onProgress = { progress ->
                    downloadEvents.onProgress(progress)
                },
                onComplete = {
                    // Unzip to output location
                    try {
                        unzipFile(tempFile, outputLocation)
                        tempFile.delete()
                        downloadEvents.onComplete()
                    } catch (e: Exception) {
                        downloadEvents.onError(e)
                    }
                },
                onError = { error ->
                    tempFile.delete()
                    downloadEvents.onError(error)
                }
            )
        } catch (e: Exception) {
            Log.e(TAG, "Sherpa STT download failed: ${e.message}", e)
            downloadEvents.onError(e)
        }
    }

    override suspend fun installModel(
        cloudModel: CloudModel,
        outputLocation: File,
        baseDir: File
    ): Result<Unit> {
        return try {
            val sttModel = cloudModel.toSherpaSTTModel(baseDir)
            SherpaSTTModelManager.addModel(sttModel)
            Log.i(TAG, "Sherpa STT model installed: ${cloudModel.modelName}")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Sherpa STT installation failed: ${e.message}", e)
            Result.failure(e)
        }
    }

    override suspend fun deleteModel(modelId: String): Result<Unit> {
        val model = SherpaSTTModelManager.getModel(modelId)
            ?: return Result.failure(Exception("Model not found..!"))

        val file = File(model.modelDir)
        if (file.exists()) {
            file.deleteRecursively()
        }
        return SherpaSTTModelManager.removeModel(modelId)
    }

    fun unzipFile(zipFile: File, outputDir: File) {
        Log.d(TAG, "Unzipping ${zipFile.name} to ${outputDir.absolutePath}")

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
                        Log.v(TAG, "Created directory: ${entryFile.name}")
                    } else {
                        // Ensure parent directory exists
                        entryFile.parentFile?.mkdirs()

                        // Extract file
                        entryFile.outputStream().use { outputStream ->
                            zipInputStream.copyTo(outputStream)
                        }
                        Log.v(TAG, "Extracted file: ${entryFile.name} (${entryFile.length()} bytes)")
                    }

                    zipInputStream.closeEntry()
                    entry = zipInputStream.nextEntry
                }
            }
        }

        Log.i(TAG, "Successfully unzipped ${zipFile.name}")
    }

    companion object {
        private const val TAG = "SherpaSTTInstaller"
    }
}