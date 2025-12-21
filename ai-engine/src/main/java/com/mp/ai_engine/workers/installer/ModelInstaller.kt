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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Public API for all model installation operations
 * This is the single entry point for model management
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

    /**
     * Initialize the ModelInstaller with application context
     * Must be called before any other operations
     */
    fun initialize(context: Context) {
        applicationContext = context.applicationContext
        baseModelsDir = File(applicationContext.filesDir, DEFAULT_MODELS_DIR)
        ensureDirectoriesExist()
        GGUFModelManager.init(context)
        OpenRouterModelManager.init(context)
        SherpaTTSModelManager.init(context)
        SherpaSTTModelManager.init(context)
        DiffusionModelManager.init(context)
        Log.i(TAG, "ModelInstaller initialized with base dir: ${baseModelsDir.absolutePath}")
    }

    /**
     * Install a model from the cloud
     * This starts the download service and handles the entire installation process
     */
    fun install(
        cloudModel: CloudModel,
        downloadUrl: String,
        onSuccess: (() -> Unit)? = null,
        onError: ((String) -> Unit)? = null
    ) {
        checkInitialized()

        try {
            // Validate installer availability
            if (!InstallerFactory.hasInstaller(cloudModel)) {
                val error = "No installer available for model type: ${cloudModel.modelType}"
                Log.e(TAG, error)
                onError?.invoke(error)
                return
            }

            // Get the appropriate directory for this model type
            val targetDir = getModelTypeDirectory(cloudModel)
            targetDir.mkdirs()

            // Start the download service
            ModelDownloadService.startDownload(
                context = applicationContext,
                cloudModel = cloudModel,
                downloadUrl = downloadUrl,
                baseDir = targetDir
            )

            Log.i(TAG, "Installation started for: ${cloudModel.modelName}")
            onSuccess?.invoke()

        } catch (e: Exception) {
            val error = "Failed to start installation: ${e.message}"
            Log.e(TAG, error, e)
            onError?.invoke(error)
        }
    }

    suspend fun installLocalModels(
        cloudModel: CloudModel,
        localPath: String,
        onSuccess: (() -> Unit)? = null,
        onError: ((String) -> Unit)? = null
    ) {
        checkInitialized()

        try {
            val installer = InstallerFactory.getInstaller(cloudModel)
            if (installer == null) {
                onError?.invoke("No Installer Found For this Model")
                return
            }
            val result = installer.installModel(
                cloudModel, File(localPath), baseModelsDir
            )

            if (result.isSuccess) {
                onSuccess?.invoke()
            } else {
                onError?.invoke(result.exceptionOrNull()?.message ?: "Unknown Error")
            }

        } catch (e: Exception) {
            val error = "Failed to start installation: ${e.message}"
            Log.e(TAG, error, e)
            onError?.invoke(error)
        }
    }

    /**
     * Cancel an ongoing model download
     */
    fun cancelDownload(downloadUrl: String) {
        checkInitialized()
        ModelDownloadService.cancelDownload(applicationContext, downloadUrl)
        Log.i(TAG, "Download cancellation requested for: $downloadUrl")
    }

    /**
     * Delete a model from storage
     */
    suspend fun deleteModel(
        modelId: String, onSuccess: (() -> Unit)? = null, onError: ((String) -> Unit)? = null
    ) = withContext(Dispatchers.IO) {
        checkInitialized()

        try {
            val searchResult = findModel(modelId)
            if (searchResult == null) {
                val error = "Model not found: $modelId"
                Log.e(TAG, error)
            }

            val cloudModel = CloudModel(
                providerName = searchResult?.provider.toString(),
                modelType = searchResult?.modelType ?: ModelType.NONE,
            )

            val installer = InstallerFactory.getInstaller(cloudModel)
            if (installer == null) {
                val error = "No installer found for model type: ${cloudModel.modelType}"
                Log.e(TAG, error)
                onError?.invoke(error)
                return@withContext
            }

            val targetDir = getModelTypeDirectory(cloudModel)
            val modelLocation = installer.determineOutputLocation(cloudModel, targetDir)

            if (modelLocation.exists()) {
                val deleted = if (modelLocation.isDirectory) {
                    modelLocation.deleteRecursively()
                } else {
                    modelLocation.delete()
                }

                if (deleted) {
                    installer.deleteModel(searchResult?.modelId ?: return@withContext)
                    Log.i(TAG, "Successfully deleted: ${searchResult.modelName}")
                    onSuccess?.invoke()
                } else {
                    val error = "Failed to delete model files"
                    Log.e(TAG, error)
                    onError?.invoke(error)
                }
            } else {
                Log.w(TAG, "Model file not found: ${modelLocation.absolutePath}")
                onSuccess?.invoke() // Consider it a success if file doesn't exist
            }

        } catch (e: Exception) {
            val error = "Error deleting model: ${e.message}"
            Log.e(TAG, error, e)
            onError?.invoke(error)
        }
    }

    /**
     * Check if a model is already installed
     */
    fun isModelInstalled(cloudModel: CloudModel): Boolean {
        checkInitialized()

        return try {
            val installer = InstallerFactory.getInstaller(cloudModel) ?: return false
            val targetDir = getModelTypeDirectory(cloudModel)
            val modelLocation = installer.determineOutputLocation(cloudModel, targetDir)

            val exists =
                modelLocation.exists() && (modelLocation.isFile || (modelLocation.isDirectory && modelLocation.listFiles()
                    ?.isNotEmpty() == true))

            Log.d(TAG, "Model ${cloudModel.modelName} installed: $exists")
            exists
        } catch (e: Exception) {
            Log.e(TAG, "Error checking model installation: ${e.message}", e)
            false
        }
    }

    /**
     * Get the file size of an installed model
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
            Log.e(TAG, "Error calculating model size: ${e.message}", e)
            0L
        }
    }

    /**
     * Get model path
     */
    fun getModelPath(cloudModel: CloudModel): String? {
        checkInitialized()

        return try {
            val installer = InstallerFactory.getInstaller(cloudModel) ?: return null
            val targetDir = getModelTypeDirectory(cloudModel)
            val modelLocation = installer.determineOutputLocation(cloudModel, targetDir)

            if (modelLocation.exists()) {
                modelLocation.absolutePath
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting model path: ${e.message}", e)
            null
        }
    }

    // Private helper methods

    private fun ensureDirectoriesExist() {
        baseModelsDir.mkdirs()
        File(baseModelsDir, GGUF_DIR).mkdirs()
        File(baseModelsDir, SHERPA_TTS_DIR).mkdirs()
        File(baseModelsDir, SHERPA_STT_DIR).mkdirs()
        File(baseModelsDir, OPENROUTER_DIR).mkdirs()
        File(baseModelsDir, DIFFUSION_DIR).mkdirs()
    }

    private fun calculateSize(file: File): Long {
        if (!file.exists()) return 0L

        return if (file.isDirectory) {
            file.walkTopDown().filter { it.isFile }.map { it.length() }.sum()
        } else {
            file.length()
        }
    }

    private fun checkInitialized() {
        check(::applicationContext.isInitialized) {
            "ModelInstaller not initialized. Call initialize(context) first."
        }
    }

    /**
     * Get the directory for a specific model type
     */
    private fun getModelTypeDirectory(cloudModel: CloudModel): File {
        checkInitialized()
        return when {
            cloudModel.providerName.contains("GGUF", ignoreCase = true) -> File(
                baseModelsDir,
                GGUF_DIR
            )

            cloudModel.providerName.contains(
                "SHERPA",
                ignoreCase = true
            ) && cloudModel.providerName.contains("TTS", ignoreCase = true) -> File(
                baseModelsDir,
                SHERPA_TTS_DIR
            )

            cloudModel.providerName.contains(
                "SHERPA",
                ignoreCase = true
            ) && cloudModel.providerName.contains("STT", ignoreCase = true) -> File(
                baseModelsDir,
                SHERPA_STT_DIR
            )

            cloudModel.providerName.contains(
                "DIFFUSION",
                ignoreCase = true
            ) -> File(
                baseModelsDir,
                DIFFUSION_DIR
            )

            cloudModel.providerName.contains("OPENROUTER", ignoreCase = true) -> File(
                baseModelsDir,
                OPENROUTER_DIR
            )

            else -> baseModelsDir
        }
    }

    suspend fun findModel(modelID: String): ModelSearchResult? {
        GGUFModelManager.getModel(modelID)?.let {
            return ModelSearchResult(
                modelId = it.id,
                modelName = it.modelName,
                modelType = it.modelType,
                provider = ModelProvider.GGUF,
                ggufModel = it
            )
        }

        OpenRouterModelManager.getModel(modelID)?.let {
            return ModelSearchResult(
                modelId = it.id,
                modelName = it.modelName,
                modelType = it.modelType,
                provider = ModelProvider.OPEN_ROUTER,
                openRouterModel = it
            )
        }

        SherpaTTSModelManager.getModel(modelID)?.let {
            return ModelSearchResult(
                modelId = it.id,
                modelName = it.modelName,
                modelType = ModelType.TTS,
                provider = ModelProvider.SHERPA,
                sherpaTTSModel = it
            )
        }

        SherpaSTTModelManager.getModel(modelID)?.let {
            return ModelSearchResult(
                modelId = it.id,
                modelName = it.modelName,
                modelType = ModelType.STT,
                provider = ModelProvider.SHERPA,
                sherpaSTTModel = it
            )
        }

        DiffusionModelManager.getModel(modelID)?.let {
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

    suspend fun getInstalledGGUFModels(): List<GGUFDatabaseModel>{
        return GGUFModelManager.getAllModels()
    }
}
sealed class DownloadState {
    abstract val isDownloading: Boolean
    abstract val progress: Float
    abstract val isComplete: Boolean
    abstract val errorMessage: String?

    data class Downloading(override val progress: Float) : DownloadState() {
        override val isDownloading = true
        override val isComplete = false
        override val errorMessage: String? = null
    }
    data class Complete(val filePath: String) : DownloadState() {
        override val isDownloading = false
        override val progress = 100f
        override val isComplete = true
        override val errorMessage: String? = null
    }

    data class Failed(val error: String) : DownloadState() {
        override val isDownloading = false
        override val progress = 0f
        override val isComplete = false
        override val errorMessage: String = error
    }
}