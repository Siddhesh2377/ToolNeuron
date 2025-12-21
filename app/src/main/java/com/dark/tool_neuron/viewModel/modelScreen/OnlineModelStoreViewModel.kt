package com.dark.tool_neuron.viewModel.modelScreen

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dark.tool_neuron.data.ModelDataProvider
import com.mp.ai_engine.models.llm_models.CloudModel
import com.mp.ai_engine.models.llm_models.GGUFDatabaseModel
import com.mp.ai_engine.workers.DownloadState
import com.mp.ai_engine.workers.DownloadsState
import com.mp.ai_engine.workers.installer.ModelInstaller
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Clean ViewModel for managing online model downloads
 */
class OnlineModelStoreViewModel : ViewModel() {

    companion object {
        private const val TAG = "OnlineModelStoreVM"
    }

    // Available models from server/firebase
    private val _availableModels = MutableStateFlow<List<CloudModel>>(emptyList())
    val availableModels: StateFlow<List<CloudModel>> = _availableModels.asStateFlow()

    // Installed models from database
    private val _installedModels = MutableStateFlow<List<GGUFDatabaseModel>>(emptyList())
    val installedModels: StateFlow<List<GGUFDatabaseModel>> = _installedModels.asStateFlow()

    // Download states from ModelInstaller
    val downloadsState: StateFlow<DownloadsState> = ModelInstaller.downloadsState.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = DownloadsState()
    )

    init {
        loadInstalledModels()
        loadAvailableModels()
    }

    // ==================== Data Loading ====================

    /**
     * Load installed models from database
     */
    private fun loadInstalledModels() {
        viewModelScope.launch {
            try {
                val models = ModelInstaller.getInstalledGGUFModels()
                _installedModels.value = models
                Log.d(TAG, "Loaded ${models.size} installed models")
            } catch (e: Exception) {
                Log.e(TAG, "Error loading installed models", e)
            }
        }
    }

    /**
     * Load available models from server/firebase
     */
    private fun loadAvailableModels() {
        viewModelScope.launch {
            try {
                // Load from pre-configured list (can be replaced with Firebase/API)
                val models = ModelDataProvider.getGGUFModels()
                _availableModels.value = models

                Log.d(TAG, "Loaded ${models.size} available models")

                // TODO: Replace with Firebase fetch if needed:
                // val firebaseModels = firebaseRepository.getAvailableModels()
                // _availableModels.value = firebaseModels
            } catch (e: Exception) {
                Log.e(TAG, "Error loading available models", e)
            }
        }
    }

    /**
     * Refresh both lists
     */
    fun refresh() {
        loadInstalledModels()
        loadAvailableModels()
    }

    // ==================== Installation ====================

    /**
     * Download and install a model from online source
     */
    fun downloadModel(cloudModel: CloudModel) {
        viewModelScope.launch {
            ModelInstaller.installOnline(
                cloudModel = cloudModel,
                onStarted = {
                    Log.i(TAG, "Download started: ${cloudModel.modelName}")
                },
                onError = { error ->
                    Log.e(TAG, "Download failed to start: $error")
                }
            )
        }
    }

    /**
     * Install model from local file
     */
    fun installLocalModel(cloudModel: CloudModel, localPath: String) {
        viewModelScope.launch {
            ModelInstaller.installOffline(
                cloudModel = cloudModel,
                localPath = localPath,
                onSuccess = {
                    Log.i(TAG, "Local model installed: ${cloudModel.modelName}")
                    loadInstalledModels() // Refresh installed list
                },
                onError = { error ->
                    Log.e(TAG, "Local install failed: $error")
                }
            )
        }
    }

    // ==================== Download Control ====================

    /**
     * Cancel a specific download
     */
    fun cancelDownload(downloadId: String) {
        ModelInstaller.cancelDownload(downloadId)
        Log.i(TAG, "Cancelled download: $downloadId")
    }

    /**
     * Cancel all active downloads
     */
    fun cancelAllDownloads() {
        ModelInstaller.cancelAllDownloads()
        Log.i(TAG, "Cancelled all downloads")
    }

    // ==================== Model Management ====================

    /**
     * Delete an installed model
     */
    fun deleteModel(modelId: String) {
        viewModelScope.launch {
            ModelInstaller.deleteModel(
                modelId = modelId,
                onSuccess = {
                    Log.i(TAG, "Model deleted: $modelId")
                    loadInstalledModels() // Refresh installed list
                },
                onError = { error ->
                    Log.e(TAG, "Delete failed: $error")
                }
            )
        }
    }

    // ==================== Query Methods ====================

    /**
     * Check if a model is installed
     */
    fun isModelInstalled(modelName: String): Boolean {
        return _installedModels.value.any { it.modelName == modelName }
    }

    /**
     * Get download state for a specific model
     */
    fun getDownloadState(downloadId: String): DownloadState? {
        return downloadsState.value.getDownload(downloadId)
    }

    /**
     * Check if model is currently downloading
     */
    fun isDownloading(downloadId: String): Boolean {
        return downloadsState.value.isDownloading(downloadId)
    }

    /**
     * Get all active downloads
     */
    fun getActiveDownloads(): List<DownloadState> {
        return downloadsState.value.activeDownloads
    }

    /**
     * Clear completed/failed downloads from tracking
     */
    fun clearInactiveDownloads() {
        ModelInstaller.clearInactiveDownloads()
    }

    override fun onCleared() {
        super.onCleared()
        Log.d(TAG, "ViewModel cleared")
    }
}