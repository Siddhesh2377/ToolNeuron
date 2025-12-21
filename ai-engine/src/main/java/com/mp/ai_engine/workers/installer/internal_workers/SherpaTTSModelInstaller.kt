package com.mp.ai_engine.workers.installer.internal_workers

import android.util.Log
import com.mp.ai_engine.managers.SherpaTTSModelManager
import com.mp.ai_engine.models.llm_models.CloudModel
import com.mp.ai_engine.models.llm_models.ModelType
import com.mp.ai_engine.models.llm_models.toSherpaTTSModel
import com.mp.ai_engine.util.downloadFile
import com.mp.ai_engine.util.unzipFile
import com.mp.ai_engine.workers.installer.DownloadEvents
import com.mp.ai_engine.workers.installer.SuperInstaller
import java.io.File
import java.util.zip.ZipInputStream

/**
 * Installer for Sherpa TTS models (typically directories with multiple files)
 */
class SherpaTTSModelInstaller : SuperInstaller() {

    override fun canHandle(cloudModel: CloudModel): Boolean {
        return cloudModel.modelType == ModelType.TTS && cloudModel.providerName.contains(
            "SHERPA",
            ignoreCase = true
        )
    }

    override fun determineOutputLocation(cloudModel: CloudModel, baseDir: File): File {
        // Sherpa TTS models are typically directories
        return File(baseDir, cloudModel.modelName).also { it.mkdirs() }
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
                    unzipFile(tempFile, outputLocation)
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
            Log.e(TAG, "Sherpa TTS download failed: ${e.message}", e)
            downloadEvents.onError(e)
        }
    }

    override suspend fun installModel(
        cloudModel: CloudModel, outputLocation: File, baseDir: File
    ): Result<Unit> {
        return try {
            val ttsModel = cloudModel.toSherpaTTSModel(baseDir)
            SherpaTTSModelManager.addModel(ttsModel)
            Log.i(TAG, "Sherpa TTS model installed: ${cloudModel.modelName}")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Sherpa TTS installation failed: ${e.message}", e)
            Result.failure(e)
        }
    }

    override suspend fun deleteModel(modelId: String): Result<Unit> {
        val model = SherpaTTSModelManager.getModel(modelId)
            ?: return Result.failure(Exception("Model not found..!"))

        val file = File(model.modelDir)
        if (file.exists()) {
            file.deleteRecursively()
        }
        return SherpaTTSModelManager.removeModel(modelId)
    }

    companion object {
        private const val TAG = "SherpaTTSInstaller"
    }
}