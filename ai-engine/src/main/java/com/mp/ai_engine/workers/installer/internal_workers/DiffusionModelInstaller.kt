package com.mp.ai_engine.workers.installer.internal_workers

import android.util.Log
import com.mp.ai_engine.managers.DiffusionModelManager
import com.mp.ai_engine.models.llm_models.CloudModel
import com.mp.ai_engine.models.llm_models.ModelType
import com.mp.ai_engine.models.llm_models.toDiffusionModel
import com.mp.ai_engine.util.downloadFile
import com.mp.ai_engine.workers.installer.DownloadEvents
import com.mp.ai_engine.workers.installer.SuperInstaller
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipInputStream

/**
 * Installer for Diffusion models (ZIP based)
 */
class DiffusionModelInstaller : SuperInstaller() {

    override fun canHandle(cloudModel: CloudModel): Boolean {
        return cloudModel.modelType == ModelType.IMAGE_GEN || cloudModel.providerName.contains(
            "DIFFUSION",
            ignoreCase = true
        )
    }

    override fun determineOutputLocation(cloudModel: CloudModel, baseDir: File): File {
        // Return temp zip file location
        return baseDir.also { it.mkdirs() }
    }

    override suspend fun downloadModel(
        cloudModel: CloudModel,
        downloadUrl: String,
        outputLocation: File,
        downloadEvents: DownloadEvents
    ) {
        try {
            // For Sherpa TTS, we might download a zip file first
            val tempFile = File(outputLocation.parentFile, "${cloudModel.modelName}.zip")

            downloadFile(fileUrl = downloadUrl, outputFile = tempFile, onProgress = { progress ->
                downloadEvents.onProgress(progress)
            }, onComplete = {
                // Unzip to output location
                try {
                    unzipFile(tempFile, File(outputLocation, cloudModel.modelName))
                    tempFile.delete()
                    downloadEvents.onComplete()
                } catch (e: Exception) {
                    downloadEvents.onError(e)
                }
            }, onError = { error ->
                tempFile.delete()
                downloadEvents.onError(error)
            })
        } catch (e: Exception) {
            Log.e(TAG, "Diffusion model download failed: ${e.message}", e)
            downloadEvents.onError(e)
        }
    }

    override suspend fun installModel(
        cloudModel: CloudModel, outputLocation: File, baseDir: File
    ): Result<Unit> {
        return try {
            val diffusionModel = cloudModel.toDiffusionModel(baseDir)
            DiffusionModelManager.addModel(diffusionModel)
            Log.i(TAG, "Diffusion model installed: ${cloudModel.modelName}")
            Result.success(Unit)

        } catch (e: Exception) {
            Log.e(TAG, "Diffusion model installation failed: ${e.message}", e)
            // Cleanup on failure
            outputLocation.delete()
            Result.failure(e)
        }
    }

    fun unzipFile(
        zipFile: File,
        destDir: File,
    ) {
        destDir.mkdirs()

        ZipInputStream(zipFile.inputStream().buffered()).use { zis ->
            var entry = zis.nextEntry

            while (entry != null) {
                if (!entry.isDirectory) {
                    val fileName = entry.name.substringAfterLast('/')
                    if (fileName.isNotEmpty() && !fileName.startsWith(".") && !fileName.startsWith("__MACOSX")) {
                        val file = File(destDir, fileName)

                        java.io.BufferedOutputStream(FileOutputStream(file)).use { output ->
                            zis.copyTo(output)
                        }
                    }
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }
    }

    override suspend fun deleteModel(modelId: String): Result<Unit> {
        return try {
            DiffusionModelManager.getModel(modelId)
                ?: return Result.failure(Exception("Model not found"))

//            // Delete model directory
//            val modelDir = File(model.fileUrl)
//            if (modelDir.exists()) {
//                modelDir.deleteRecursively()
//            }

            // Remove from database
            DiffusionModelManager.removeModel(modelId)

            Log.i(TAG, "Diffusion model deleted: $modelId")
            Result.success(Unit)

        } catch (e: Exception) {
            Log.e(TAG, "Diffusion model deletion failed: ${e.message}", e)
            Result.failure(e)
        }
    }

    companion object {
        private const val TAG = "DiffusionModelInstaller"
    }
}