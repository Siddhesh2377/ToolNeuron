package com.mp.ai_engine.workers.installer.internal_workers

import android.util.Log
import com.mp.ai_engine.managers.GGUFModelManager
import com.mp.ai_engine.models.CloudModel
import com.mp.ai_engine.models.ModelType
import com.mp.ai_engine.models.toGGUFModel
import com.mp.ai_engine.util.downloadFile
import com.mp.ai_engine.workers.installer.DownloadEvents
import com.mp.ai_engine.workers.installer.SuperInstaller
import java.io.File

/**
 * Installer for GGUF models
 */
class GGUFModelInstaller : SuperInstaller() {

    override fun canHandle(cloudModel: CloudModel): Boolean {
        return cloudModel.providerName.contains("GGUF", ignoreCase = true)
    }

    override fun determineOutputLocation(cloudModel: CloudModel, baseDir: File): File {
        return File(baseDir, "${cloudModel.modelName}.gguf")
    }

    override suspend fun downloadModel(
        cloudModel: CloudModel,
        downloadUrl: String,
        outputLocation: File,
        downloadEvents: DownloadEvents
    ) {
        try {
            downloadFile(
                fileUrl = downloadUrl,
                outputFile = outputLocation,
                onProgress = { progress ->
                    downloadEvents.onProgress(progress)
                },
                onComplete = {
                    downloadEvents.onComplete()
                },
                onError = { error ->
                    downloadEvents.onError(error)
                }
            )
        } catch (e: Exception) {
            Log.e(TAG, "GGUF download failed: ${e.message}", e)
            downloadEvents.onError(e)
        }
    }

    override suspend fun deleteModel(modelId: String): Result<Unit> {
        val model = GGUFModelManager.getModel(modelId)
            ?: return Result.failure(Exception("Model not found..!"))

        val file = File(model.modelPath)
        if (file.exists()) {
            file.delete()
        }
        return GGUFModelManager.removeModel(modelId)
    }

    override suspend fun installModel(
        cloudModel: CloudModel,
        outputLocation: File,
        baseDir: File
    ): Result<Unit> {
        return try {
            val ggufModel = cloudModel.toGGUFModel(baseDir)
            GGUFModelManager.addModel(ggufModel)
            Log.i(TAG, "GGUF model installed: ${cloudModel.modelName}")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "GGUF installation failed: ${e.message}", e)
            Result.failure(e)
        }
    }

    companion object {
        private const val TAG = "GGUFModelInstaller"
    }
}