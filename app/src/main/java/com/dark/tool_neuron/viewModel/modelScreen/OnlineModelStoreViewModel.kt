package com.dark.tool_neuron.viewModel.modelScreen

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dark.tool_neuron.model.GGUFModels
import com.mp.ai_engine.models.llm_models.CloudModel
import com.mp.ai_engine.models.llm_models.ModelProvider
import com.mp.ai_engine.models.llm_models.ModelType
import com.mp.ai_engine.workers.installer.DownloadProgressManager
import com.mp.ai_engine.workers.installer.DownloadState
import com.mp.ai_engine.workers.installer.ModelInstaller
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class OnlineModelStoreViewModel : ViewModel() {

    companion object {
        private const val TAG = "OnlineModelStoreViewModel"
    }

    private val _ggufModels = MutableStateFlow<List<GGUFModels>>(emptyList())
    val ggufModels: StateFlow<List<GGUFModels>> = _ggufModels

    // Observe download states from the centralized manager
    val downloadStates: StateFlow<Map<String, DownloadState>> =
        DownloadProgressManager.downloadStates.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyMap()
        )

    init {
        observeGGUFModels()
    }

    private fun observeGGUFModels() {
        viewModelScope.launch(Dispatchers.IO) {
            // TODO: Implement your Firebase/database fetching logic
//            db.collection("gguf-models").get().addOnSuccessListener {
//                val models = it.toObjects(GGUFModels::class.java)
//                _ggufModels.value = models
//            }.addOnFailureListener {
//                Log.e(TAG, "Error fetching GGUF models", it)
//            }
        }
    }

    /**
     * Start downloading a model
     */
    fun startDownload(model: GGUFModels) {
        viewModelScope.launch {
            try {
                // Convert GGUFModels to CloudModel
                val cloudModel = CloudModel(
                    modelName = model.modelName,
                    providerName = ModelProvider.GGUF.toString(),
                    modelType = when (model.modelType) {
                        "TXT" -> ModelType.TEXT
                        "VLM" -> ModelType.VLM
                        "EMBED" -> ModelType.EMBEDDING
                        else -> ModelType.TEXT
                    },
                    modelDescription = model.modelDescription,
                )

                ModelInstaller.install(
                    cloudModel = cloudModel,
                    downloadUrl = model.modelFileLink,
                    onSuccess = {
                        Log.i(TAG, "Download started successfully for: ${model.modelName}")
                    },
                    onError = { error ->
                        Log.e(TAG, "Failed to start download: $error")
                        DownloadProgressManager.markFailed(model.modelFileLink, error)
                    }
                )
            } catch (e: Exception) {
                Log.e(TAG, "Error starting download", e)
                DownloadProgressManager.markFailed(
                    model.modelFileLink,
                    e.message ?: "Unknown error"
                )
            }
        }
    }

    /**
     * Cancel an ongoing download
     */
    fun cancelDownload(modelName: String, downloadUrl: String, context: Context) {
        viewModelScope.launch {
            try {
                ModelInstaller.cancelDownload(downloadUrl)
                DownloadProgressManager.removeDownload(downloadUrl)
                Log.i(TAG, "Download cancelled for: $modelName")
            } catch (e: Exception) {
                Log.e(TAG, "Error cancelling download", e)
            }
        }
    }

    /**
     * Delete an installed model
     */
    fun removeModel(model: GGUFModels) {
        viewModelScope.launch {
            try {
                // You'll need to get the model ID from your database
                // For now, using model name as a fallback
                withContext(Dispatchers.IO) {
                    val installedModels = ModelInstaller.getInstalledGGUFModels()
                    val installedModel = installedModels.find { it.modelName == model.modelName }

                    if (installedModel != null) {
                        ModelInstaller.deleteModel(
                            modelId = installedModel.id,
                            onSuccess = {
                                Log.i(TAG, "Model deleted successfully: ${model.modelName}")
                            },
                            onError = { error ->
                                Log.e(TAG, "Failed to delete model: $error")
                            }
                        )
                    } else {
                        Log.w(TAG, "Model not found in installed models: ${model.modelName}")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error deleting model", e)
            }
        }
    }

    /**
     * Get download state for a specific model
     */
    fun getDownloadState(downloadUrl: String): DownloadState? {
        return downloadStates.value[downloadUrl]
    }

    /**
     * Check if a model is currently downloading
     */
    fun isModelDownloading(downloadUrl: String): Boolean {
        return downloadStates.value[downloadUrl]?.isDownloading == true
    }

    /**
     * Get all active downloads
     */
    fun getActiveDownloads(): List<String> {
        return DownloadProgressManager.getActiveDownloads()
    }

    /**
     * Get download progress for a specific model (0-100)
     */
    fun getDownloadProgress(downloadUrl: String): Float {
        return downloadStates.value[downloadUrl]?.progress ?: 0f
    }

    /**
     * Cancel all active downloads
     */
    fun cancelAllDownloads(context: Context) {
        viewModelScope.launch {
            val activeDownloads = getActiveDownloads()
            activeDownloads.forEach { downloadUrl ->
                ModelInstaller.cancelDownload(downloadUrl)
                DownloadProgressManager.removeDownload(downloadUrl)
            }
            Log.i(TAG, "Cancelled all downloads (${activeDownloads.size})")
        }
    }

    override fun onCleared() {
        super.onCleared()
        Log.d(TAG, "ViewModel cleared")
    }
}