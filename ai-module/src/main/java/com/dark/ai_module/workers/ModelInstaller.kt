package com.dark.ai_module.workers

import android.content.Context
import com.dark.ai_module.db.DatabaseProvider
import com.dark.ai_module.db.ModelDAO
import com.dark.ai_module.model.ModelData
import com.dark.ai_module.model.ModelType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicBoolean
import java.util.zip.ZipInputStream
import kotlin.coroutines.cancellation.CancellationException

/**
 * Universal Model Installation Manager
 *
 * Handles all model lifecycle operations:
 * - Download models from URL
 * - Install models to database
 * - Update models based on file hash
 * - Delete models and cleanup files
 */
object ModelInstallationManager {

    private const val TAG = "ModelInstallationManager"
    private const val BUFFER_SIZE = 8192
    private const val PROGRESS_UPDATE_INTERVAL = 500L
    private const val MODELS_DIR = "models"

    private lateinit var dao: ModelDAO
    private lateinit var modelsDirectory: File
    private val initGuard = AtomicBoolean(false)

    // Download progress tracking
    private val _downloadProgress = MutableStateFlow<Map<String, DownloadState>>(emptyMap())
    val downloadProgress: StateFlow<Map<String, DownloadState>> = _downloadProgress.asStateFlow()

    /**
     * Initialize the manager with application context
     * Must be called before any other operations
     */
    fun init(context: Context) {
        if (initGuard.compareAndSet(false, true)) {
            dao = DatabaseProvider.getDatabase(context).ModelDAO()
            modelsDirectory = File(context.filesDir, MODELS_DIR).apply { mkdirs() }
        }
    }

    /* ========================================================================= *//* PUBLIC API                                                                *//* ========================================================================= */

    /**
     * Download a model from URL
     *
     * @param modelData Model information including download URL
     * @param onProgress Callback for progress updates (0-100)
     * @return Downloaded file path on success, null on failure
     */
    suspend fun downloadModel(
        modelData: ModelData, onProgress: ((Float) -> Unit)? = null
    ): Result<String> = withContext(Dispatchers.IO) {
        ensureInitialized()

        val url = modelData.modelUrl
        if (url.isNullOrBlank()) {
            return@withContext Result.failure(IllegalArgumentException("Model URL is required"))
        }

        val outputFile = File(modelData.modelPath)

        // Check if already downloading
        if (_downloadProgress.value.containsKey(url)) {
            return@withContext Result.failure(IllegalStateException("Download already in progress"))
        }

        updateDownloadState(url, DownloadState.Downloading(0f))

        try {
            val connection = URL(url).openConnection() as HttpURLConnection

            connection.apply {
                requestMethod = "GET"
                connectTimeout = 30000
                readTimeout = 30000
                setRequestProperty("User-Agent", "ToolNeuron/1.0")
            }

            connection.connect()

            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                throw Exception("HTTP ${connection.responseCode}: ${connection.responseMessage}")
            }

            val fileSize = connection.contentLengthLong
            val inputStream = connection.inputStream
            val outputStream = FileOutputStream(outputFile)

            var downloadedBytes = 0L
            val buffer = ByteArray(BUFFER_SIZE)
            var lastProgressUpdate = System.currentTimeMillis()

            while (true) {
                // Check if job was cancelled
                if (!isActive) {
                    inputStream.close()
                    outputStream.close()
                    connection.disconnect()
                    if (outputFile.exists()) {
                        outputFile.delete()
                    }
                    clearDownloadState(url)
                    throw CancellationException("Download cancelled")
                }

                val bytesRead = inputStream.read(buffer)
                if (bytesRead == -1) break

                outputStream.write(buffer, 0, bytesRead)
                downloadedBytes += bytesRead

                val progress = if (fileSize > 0) {
                    (downloadedBytes.toFloat() / fileSize) * 100
                } else 0f

                // Throttled progress updates
                val now = System.currentTimeMillis()
                if (now - lastProgressUpdate > PROGRESS_UPDATE_INTERVAL) {
                    updateDownloadState(url, DownloadState.Downloading(progress))
                    onProgress?.invoke(progress)
                    lastProgressUpdate = now
                }
            }

            outputStream.flush()
            outputStream.close()
            inputStream.close()
            connection.disconnect()

            updateDownloadState(url, DownloadState.Complete(outputFile.absolutePath))

            if (modelData.modelType == ModelType.TTS) {

            }

            Result.success(outputFile.absolutePath)

        } catch (e: CancellationException) {
            // Handle cancellation specifically
            if (outputFile.exists()) {
                outputFile.delete()
            }
            clearDownloadState(url)
            Result.failure(e)
        } catch (e: Exception) {
            // Cleanup partial download
            if (outputFile.exists()) {
                outputFile.delete()
            }
            updateDownloadState(url, DownloadState.Failed(e.message ?: "Download failed"))
            Result.failure(e)
        } finally {
            // Clear state after a delay to allow UI to read final state
            kotlinx.coroutines.delay(2000)
            clearDownloadState(url)
        }
    }

    /**
     * Install a model to the database
     *
     * @param modelData Model information with valid modelPath
     * @return Success or failure result
     */
    suspend fun installModel(modelData: ModelData): Result<Unit> = withContext(Dispatchers.IO) {
        ensureInitialized()

//        when (modelData.providerName) {
//            ModelProvider.OpenRouter.toString() -> installOpenRouterModel(
//                modelData.modelName, modelData.modelUrl.toString(), ModelType.TEXT, onComplete
//            )
//
//            ModelProvider.LocalGGUF.toString() -> installLocalGGUFModel(
//                context, name, url, fileName, modelType, onProgress, onComplete, onError
//            )
//
//            ModelProvider.HuggingFace.toString() -> installLocalGGUFModel(
//                context, name, url, fileName, modelType, onProgress, onComplete, onError
//            )
//
//            ModelProvider.SherpaONNX.toString() -> installSherpaModel(
//                context, name, url, fileName, modelType, onProgress, onComplete, onError
//            )
//        }

        if (modelData.modelPath.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("Model path is required"))
        }

        val modelFile = File(modelData.modelPath)
        if (!modelFile.exists()) {
            return@withContext Result.failure(IllegalStateException("Model file not found: ${modelData.modelPath}"))
        }

        try {
            // Calculate hash for future update checks
            calculateFileHash(modelFile)

            // Store model with hash in metadata (you may want to add a hash field to ModelData)
            dao.insertModel(modelData)

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Delete a model from database and optionally delete the file
     *
     * @param modelId ID of the model to delete
     * @param deleteFile If true, also delete the model file from storage
     * @return Success or failure result
     */
    suspend fun deleteModel(modelId: String, deleteFile: Boolean = true): Result<Unit> =
        withContext(Dispatchers.IO) {
            ensureInitialized()

            try {
                val model = dao.getModelById(modelId) ?: return@withContext Result.failure(
                    IllegalArgumentException("Model not found: $modelId")
                )

                // Delete from database
                dao.deleteModel(model)

                // Optionally delete file
                if (deleteFile && model.modelPath.isNotBlank()) {
                    val modelFile = File(model.modelPath)
                    if (modelFile.exists()) {
                        modelFile.delete()
                    }
                }

                Result.success(Unit)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    /**
     * Download and install a model in one operation
     *
     * @param modelData Model information including download URL
     * @param onProgress Progress callback (0-100)
     * @return Installed ModelData on success
     */
    suspend fun downloadAndInstallModel(
        modelData: ModelData, onProgress: ((Float) -> Unit)? = null
    ): Result<ModelData> = withContext(Dispatchers.IO) {
        // Download the model
        val downloadResult = downloadModel(modelData, onProgress)

        if (downloadResult.isFailure) {
            return@withContext Result.failure(downloadResult.exceptionOrNull()!!)
        }

        val filePath = downloadResult.getOrNull()!!
        val updatedModelData = modelData.copy(modelPath = filePath)

        // Install to database
        val installResult = installModel(updatedModelData)

        if (installResult.isFailure) {
            return@withContext Result.failure(installResult.exceptionOrNull()!!)
        }

        Result.success(updatedModelData)
    }

    /**
     * Check if a model is already installed
     *
     * @param modelName Name of the model
     * @return True if installed, false otherwise
     */
    suspend fun isModelInstalled(modelName: String): Boolean = withContext(Dispatchers.IO) {
        ensureInitialized()
        dao.getModelByName(modelName) != null
    }

    /* ========================================================================= *//* HELPER FUNCTIONS                                                          *//* ========================================================================= */

    /**
     * Calculate SHA-256 hash of a file for update detection
     */
    private fun calculateFileHash(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val inputStream = FileInputStream(file)
        val buffer = ByteArray(BUFFER_SIZE)
        var bytesRead: Int

        while (inputStream.read(buffer).also { bytesRead = it } != -1) {
            digest.update(buffer, 0, bytesRead)
        }

        inputStream.close()

        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun updateDownloadState(url: String, state: DownloadState) {
        _downloadProgress.value += (url to state)
    }

    private fun clearDownloadState(url: String) {
        _downloadProgress.value -= url
    }

    private fun ensureInitialized() {
        check(initGuard.get()) { "ModelInstallationManager.init(context) must be called first" }
    }

    // ---------------------
    //  LOCAL GGUF MODELS
    // ---------------------


    // ---------------------
    //  SHERPA ONNX MODELS


    // ---------------------
    //  OPENROUTER MODELS


    // ---------------------
    //  UTILITIES
    // ---------------------
    private fun unzipFlatten(zipFile: File, destDir: File) {
        if (!destDir.exists()) destDir.mkdirs()
        ZipInputStream(BufferedInputStream(FileInputStream(zipFile))).use { zipIn ->
            var entry = zipIn.nextEntry
            val buffer = ByteArray(8192)
            while (entry != null) {
                val normalizedName = entry.name.substringAfter('/')
                if (normalizedName.isEmpty()) {
                    entry = zipIn.nextEntry
                    continue
                }

                val outputFile = File(destDir, normalizedName)
                if (entry.isDirectory) {
                    outputFile.mkdirs()
                } else {
                    outputFile.parentFile?.mkdirs()
                    BufferedOutputStream(FileOutputStream(outputFile)).use { out ->
                        var len: Int
                        while (zipIn.read(buffer).also { len = it } != -1) out.write(buffer, 0, len)
                    }
                }
                zipIn.closeEntry()
                entry = zipIn.nextEntry
            }
        }
    }
}

/**
 * Represents the state of a model download
 */
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