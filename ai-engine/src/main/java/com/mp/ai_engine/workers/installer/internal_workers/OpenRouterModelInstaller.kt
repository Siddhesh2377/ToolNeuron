package com.mp.ai_engine.workers.installer.internal_workers

import android.util.Log
import com.mp.ai_engine.managers.OpenRouterModelManager
import com.mp.ai_engine.managers.SherpaTTSModelManager
import com.mp.ai_engine.models.CloudModel
import com.mp.ai_engine.models.ModelType
import com.mp.ai_engine.models.toOpenRouterModel
import com.mp.ai_engine.workers.installer.DownloadEvents
import com.mp.ai_engine.workers.installer.SuperInstaller
import java.io.File

/**
 * Installer for OpenRouter models (API-based, no file download)
 */
class OpenRouterModelInstaller : SuperInstaller() {

    override fun canHandle(cloudModel: CloudModel): Boolean {
        return cloudModel.providerName.contains("OPENROUTER", ignoreCase = true)
    }

    override fun determineOutputLocation(cloudModel: CloudModel, baseDir: File): File {
        // OpenRouter models don't need file storage, just return a placeholder
        return File(baseDir, "${cloudModel.modelName}.json")
    }

    override suspend fun downloadModel(
        cloudModel: CloudModel,
        downloadUrl: String,
        outputLocation: File,
        downloadEvents: DownloadEvents
    ) {
        // OpenRouter models are API-based, no actual download needed
        // Just simulate quick "download" for UI consistency
        try {
            downloadEvents.onProgress(0.5f)
            // Small delay to make it feel natural
            kotlinx.coroutines.delay(500)
            downloadEvents.onProgress(1.0f)
            downloadEvents.onComplete()
        } catch (e: Exception) {
            Log.e(TAG, "OpenRouter setup failed: ${e.message}", e)
            downloadEvents.onError(e)
        }
    }

    override suspend fun installModel(
        cloudModel: CloudModel,
        outputLocation: File,
        baseDir: File
    ): Result<Unit> {
        return try {
            val openRouterModel = cloudModel.toOpenRouterModel()
            OpenRouterModelManager.addModel(openRouterModel)
            Log.i(TAG, "OpenRouter model installed: ${cloudModel.modelName}")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "OpenRouter installation failed: ${e.message}", e)
            Result.failure(e)
        }
    }

    override suspend fun deleteModel(modelId: String): Result<Unit> {
        OpenRouterModelManager.getModel(modelId) ?: return Result.failure(Exception("Model not found..!"))
        return OpenRouterModelManager.removeModel(modelId)
    }

    override fun cleanup(outputLocation: File) {
        // Nothing to clean up for API-based models
        Log.d(TAG, "No cleanup needed for OpenRouter model")
    }

    companion object {
        private const val TAG = "OpenRouterInstaller"
    }
}