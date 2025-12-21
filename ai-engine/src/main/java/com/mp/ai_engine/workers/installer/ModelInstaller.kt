package com.mp.ai_engine.workers.installer

import android.content.Context
import android.util.Log
import com.mp.ai_engine.managers.DiffusionModelManager
import com.mp.ai_engine.managers.GGUFModelManager
import com.mp.ai_engine.managers.OpenRouterModelManager
import com.mp.ai_engine.managers.SherpaSTTModelManager
import com.mp.ai_engine.managers.SherpaTTSModelManager
import com.mp.ai_engine.models.llm_models.CloudModel
import com.mp.ai_engine.models.llm_models.GGUFDatabaseModel
import com.mp.ai_engine.models.llm_models.ModelProvider
import com.mp.ai_engine.models.llm_models.ModelSearchResult
import com.mp.ai_engine.models.llm_models.ModelType
import com.mp.ai_engine.service.ModelDownloadService
import com.mp.ai_engine.workers.DownloadState
import com.mp.ai_engine.workers.DownloadsState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Clean, simplified API for model installation and management
 * Single entry point for all model operations
 */
object ModelInstaller {

    private const val TAG = "ModelInstaller"
    private const val DEFAULT_MODELS_DIR = "ai_models"
    private const val GGUF_DIR = "gguf"
    private const val SHERPA_TTS_DIR = "sherpa_tts"
    private const val DIFFUSION_DIR = "diffusion"
    private const val SHERPA_STT_DIR = "sherpa_stt"
    private const val OPENROUTER_DIR = "openrouter"

    private lateinit var applicationContext: Context
    private lateinit var baseModelsDir: File

    // Exposed StateFlow for download progress tracking
    val downloadsState: StateFlow<DownloadsState> = DownloadProgressManager.downloadsState

    /**
     * Initialize ModelInstaller - MUST be called before any operations
     */
    fun initialize(context: Context) {
        applicationContext = context.applicationContext
        baseModelsDir = File(applicationContext.filesDir, DEFAULT_MODELS_DIR)
        ensureDirectoriesExist()
        
        // Initialize all managers
        GGUFModelManager.init(context)
        OpenRouterModelManager.init(context)
        SherpaTTSModelManager.init(context)
        SherpaSTTModelManager.init(context)
        DiffusionModelManager.init(context)
        
        Log.i(TAG, "ModelInstaller initialized: ${baseModelsDir.absolutePath}")
    }

    // ==================== Installation Methods ====================

    /**
     * Install model from online source (downloads then installs)
     * @param cloudModel Model metadata
     * @param onStarted Callback when download starts
     * @param onError Callback on error
     */
    fun installOnline(
        cloudModel: CloudModel,
        onStarted: (() -> Unit)? = null,
        onError: ((String) -> Unit)? = null
    ) {
        checkInitialized()

        try {
            // Validate installer exists
            if (!InstallerFactory.hasInstaller(cloudModel)) {
                val error = "No installer available for: ${cloudModel.modelType}"
                Log.e(TAG, error)
                onError?.invoke(error)
                return
            }

            // Get target directory
            val targetDir = getModelTypeDirectory(cloudModel)
            targetDir.mkdirs()

            // Start download service
            ModelDownloadService.startDownload(
                context = applicationContext,
                cloudModel = cloudModel,
                baseDir = targetDir
            )

            Log.i(TAG, "Online installation started: ${cloudModel.modelName}")
            onStarted?.invoke()

        } catch (e: Exception) {
            val error = "Failed to start installation: ${e.message}"
            Log.e(TAG, error, e)
            onError?.invoke(error)
        }
    }

    /**
     * Install model from local file (installs directly, no download)
     * @param cloudModel Model metadata
     * @param localPath Path to local model file/directory
     * @param onSuccess Callback on successful installation
     * @param onError Callback on error
     */
    suspend fun installOffline(
        cloudModel: CloudModel,
        localPath: String,
        onSuccess: (() -> Unit)? = null,
        onError: ((String) -> Unit)? = null
    ) = withContext(Dispatchers.IO) {
        checkInitialized()

        try {
            val installer = InstallerFactory.getInstaller(cloudModel)
            if (installer == null) {
                onError?.invoke("No installer found for this model type")
                return@withContext
            }

            val localFile = File(localPath)
            if (!localFile.exists()) {
                onError?.invoke("Local file does not exist: $localPath")
                return@withContext
            }

            val targetDir = getModelTypeDirectory(cloudModel)
            targetDir.mkdirs()

            // Install directly
            val result = installer.installModel(cloudModel, localFile, targetDir)

            if (result.isSuccess) {
                Log.i(TAG, "Offline installation successful: ${cloudModel.modelName}")
                onSuccess?.invoke()
            } else {
                val error = result.exceptionOrNull()?.message ?: "Installation failed"
                Log.e(TAG, "Offline installation failed: $error")
                onError?.invoke(error)
            }

        } catch (e: Exception) {
            val error = "Installation error: ${e.message}"
            Log.e(TAG, error, e)
            onError?.invoke(error)
        }
    }

    // ==================== Download Control ====================

    /**
     * Cancel a specific download
     */
    fun cancelDownload(downloadId: String) {
        checkInitialized()
        ModelDownloadService.cancelDownload(applicationContext, downloadId)
        Log.i(TAG, "Download cancelled: $downloadId")
    }

    /**
     * Cancel all active downloads
     */
    fun cancelAllDownloads() {
        checkInitialized()
        ModelDownloadService.cancelAll(applicationContext)
        Log.i(TAG, "All downloads cancelled")
    }

    /**
     * Get download state for a specific download
     */
    fun getDownloadState(downloadId: String): DownloadState? {
        return DownloadProgressManager.getDownloadState(downloadId)
    }

    /**
     * Get all active downloads
     */
    fun getActiveDownloads(): List<DownloadState> {
        return DownloadProgressManager.getActiveDownloads()
    }

    /**
     * Clear completed/failed downloads from tracking
     */
    fun clearInactiveDownloads() {
        DownloadProgressManager.clearInactive()
    }

    // ==================== Model Management ====================

    /**
     * Delete an installed model
     */
    suspend fun deleteModel(
        modelId: String,
        onSuccess: (() -> Unit)? = null,
        onError: ((String) -> Unit)? = null
    ) = withContext(Dispatchers.IO) {
        checkInitialized()

        try {
            val searchResult = findModel(modelId)
            if (searchResult == null) {
                val error = "Model not found: $modelId"
                Log.e(TAG, error)
                onError?.invoke(error)
                return@withContext
            }

            val cloudModel = CloudModel(
                providerName = searchResult.provider.toString(),
                modelType = searchResult.modelType
            )

            val installer = InstallerFactory.getInstaller(cloudModel)
            if (installer == null) {
                val error = "No installer found for: ${cloudModel.modelType}"
                Log.e(TAG, error)
                onError?.invoke(error)
                return@withContext
            }

            // Delete from database
            val result = installer.deleteModel(modelId)
            
            if (result.isSuccess) {
                // Delete files
                val targetDir = getModelTypeDirectory(cloudModel)
                val modelLocation = installer.determineOutputLocation(cloudModel, targetDir)
                
                if (modelLocation.exists()) {
                    if (modelLocation.isDirectory) {
                        modelLocation.deleteRecursively()
                    } else {
                        modelLocation.delete()
                    }
                }

                Log.i(TAG, "Model deleted: ${searchResult.modelName}")
                onSuccess?.invoke()
            } else {
                val error = result.exceptionOrNull()?.message ?: "Deletion failed"
                Log.e(TAG, error)
                onError?.invoke(error)
            }

        } catch (e: Exception) {
            val error = "Error deleting model: ${e.message}"
            Log.e(TAG, error, e)
            onError?.invoke(error)
        }
    }

    /**
     * Check if a model is installed
     */
    fun isModelInstalled(cloudModel: CloudModel): Boolean {
        checkInitialized()

        return try {
            val installer = InstallerFactory.getInstaller(cloudModel) ?: return false
            val targetDir = getModelTypeDirectory(cloudModel)
            val modelLocation = installer.determineOutputLocation(cloudModel, targetDir)

            modelLocation.exists() && (
                modelLocation.isFile || 
                (modelLocation.isDirectory && modelLocation.listFiles()?.isNotEmpty() == true)
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error checking installation: ${e.message}", e)
            false
        }
    }

    /**
     * Get installed model size in bytes
     */
    suspend fun getModelSize(cloudModel: CloudModel): Long = withContext(Dispatchers.IO) {
        checkInitialized()

        try {
            val installer = InstallerFactory.getInstaller(cloudModel) ?: return@withContext 0L
            val targetDir = getModelTypeDirectory(cloudModel)
            val modelLocation = installer.determineOutputLocation(cloudModel, targetDir)

            if (!modelLocation.exists()) return@withContext 0L

            calculateSize(modelLocation)
        } catch (e: Exception) {
            Log.e(TAG, "Error calculating size: ${e.message}", e)
            0L
        }
    }

    /**
     * Get model file path
     */
    fun getModelPath(cloudModel: CloudModel): String? {
        checkInitialized()

        return try {
            val installer = InstallerFactory.getInstaller(cloudModel) ?: return null
            val targetDir = getModelTypeDirectory(cloudModel)
            val modelLocation = installer.determineOutputLocation(cloudModel, targetDir)

            if (modelLocation.exists()) modelLocation.absolutePath else null
        } catch (e: Exception) {
            Log.e(TAG, "Error getting path: ${e.message}", e)
            null
        }
    }

    // ==================== Query Methods ====================

    /**
     * Find a model by ID across all managers
     */
    suspend fun findModel(modelId: String): ModelSearchResult? {
        GGUFModelManager.getModel(modelId)?.let {
            return ModelSearchResult(
                modelId = it.id,
                modelName = it.modelName,
                modelType = it.modelType,
                provider = ModelProvider.GGUF,
                ggufModel = it
            )
        }

        OpenRouterModelManager.getModel(modelId)?.let {
            return ModelSearchResult(
                modelId = it.id,
                modelName = it.modelName,
                modelType = it.modelType,
                provider = ModelProvider.OPEN_ROUTER,
                openRouterModel = it
            )
        }

        SherpaTTSModelManager.getModel(modelId)?.let {
            return ModelSearchResult(
                modelId = it.id,
                modelName = it.modelName,
                modelType = ModelType.TTS,
                provider = ModelProvider.SHERPA,
                sherpaTTSModel = it
            )
        }

        SherpaSTTModelManager.getModel(modelId)?.let {
            return ModelSearchResult(
                modelId = it.id,
                modelName = it.modelName,
                modelType = ModelType.STT,
                provider = ModelProvider.SHERPA,
                sherpaSTTModel = it
            )
        }

        DiffusionModelManager.getModel(modelId)?.let {
            return ModelSearchResult(
                modelId = it.id,
                modelName = it.name,
                modelType = ModelType.IMAGE_GEN,
                provider = ModelProvider.DIFFUSION,
                diffusionModel = it
            )
        }

        return null
    }

    /**
     * Get all installed GGUF models
     */
    suspend fun getInstalledGGUFModels(): List<GGUFDatabaseModel> {
        return GGUFModelManager.getAllModels()
    }

    // ==================== Private Helpers ====================

    private fun checkInitialized() {
        check(::applicationContext.isInitialized) {
            "ModelInstaller not initialized. Call initialize(context) first."
        }
    }

    private fun ensureDirectoriesExist() {
        baseModelsDir.mkdirs()
        File(baseModelsDir, GGUF_DIR).mkdirs()
        File(baseModelsDir, SHERPA_TTS_DIR).mkdirs()
        File(baseModelsDir, SHERPA_STT_DIR).mkdirs()
        File(baseModelsDir, OPENROUTER_DIR).mkdirs()
        File(baseModelsDir, DIFFUSION_DIR).mkdirs()
    }

    private fun getModelTypeDirectory(cloudModel: CloudModel): File {
        return when {
            cloudModel.providerName.contains("GGUF", ignoreCase = true) -> 
                File(baseModelsDir, GGUF_DIR)
            
            cloudModel.providerName.contains("SHERPA", ignoreCase = true) && 
            cloudModel.providerName.contains("TTS", ignoreCase = true) -> 
                File(baseModelsDir, SHERPA_TTS_DIR)
            
            cloudModel.providerName.contains("SHERPA", ignoreCase = true) && 
            cloudModel.providerName.contains("STT", ignoreCase = true) -> 
                File(baseModelsDir, SHERPA_STT_DIR)
            
            cloudModel.providerName.contains("DIFFUSION", ignoreCase = true) -> 
                File(baseModelsDir, DIFFUSION_DIR)
            
            cloudModel.providerName.contains("OPENROUTER", ignoreCase = true) -> 
                File(baseModelsDir, OPENROUTER_DIR)
            
            else -> baseModelsDir
        }
    }

    private fun calculateSize(file: File): Long {
        if (!file.exists()) return 0L

        return if (file.isDirectory) {
            file.walkTopDown().filter { it.isFile }.map { it.length() }.sum()
        } else {
            file.length()
        }
    }
}