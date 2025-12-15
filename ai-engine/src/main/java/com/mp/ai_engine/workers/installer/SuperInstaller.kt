package com.mp.ai_engine.workers.installer

import com.mp.ai_engine.models.llm_models.CloudModel
import java.io.File

/**
 * Base installer interface for all model types
 */
abstract class SuperInstaller {

    /**
     * Validates if the installer can handle this CloudModel
     */
    abstract fun canHandle(cloudModel: CloudModel): Boolean

    /**
     * Determines the output file/directory for the model
     */
    abstract fun determineOutputLocation(cloudModel: CloudModel, baseDir: File): File

    /**
     * Downloads the model file(s)
     */
    abstract suspend fun downloadModel(
        cloudModel: CloudModel,
        downloadUrl: String,
        outputLocation: File,
        downloadEvents: DownloadEvents
    )

    abstract suspend fun deleteModel(modelId: String): Result<Unit>

    /**
     * Installs the model into the appropriate database
     */
    abstract suspend fun installModel(
        cloudModel: CloudModel,
        outputLocation: File,
        baseDir: File
    ): Result<Unit>

    /**
     * Cleans up resources on error or cancellation
     */
    open fun cleanup(outputLocation: File) {
        if (outputLocation.exists()) {
            if (outputLocation.isDirectory) {
                outputLocation.deleteRecursively()
            } else {
                outputLocation.delete()
            }
        }
    }
}

/**
 * Events callback interface for download progress
 */
interface DownloadEvents {
    fun onProgress(progress: Float)
    fun onComplete()
    fun onError(error: Throwable)
}