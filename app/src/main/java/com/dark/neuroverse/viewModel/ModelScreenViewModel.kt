package com.dark.neuroverse.viewModel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dark.ai_module.model.ModelsData
import com.dark.ai_module.workers.ModelManager
import com.dark.ai_module.workers.downloadFile
import com.dark.neuroverse.model.DownloadState
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

class ModelScreenViewModel() : ViewModel() {

    private val _models = MutableStateFlow<List<ModelsData>>(emptyList())
    val models: StateFlow<List<ModelsData>> = _models

    private val _downloadStates = MutableStateFlow<Map<String, DownloadState>>(emptyMap())
    val downloadStates: StateFlow<Map<String, DownloadState>> = _downloadStates
    private val downloadJobs = mutableMapOf<String, Job>()

    init {
        observeModels()
    }

    private fun observeModels() {
        viewModelScope.launch {
            ModelManager.observeModels().collectLatest { modelList ->
                _models.value = modelList
            }
        }
    }

    fun startDownload(modelData: ModelsData) {
        if (downloadJobs.containsKey(modelData.modeName)) return // already downloading

        _downloadStates.update {
            it + (modelData.modeName to DownloadState(isDownloading = true))
        }

        val job = viewModelScope.launch {
            try {
                downloadFile(
                    fileUrl = modelData.modelLink,
                    outputFile = File(modelData.modelPath),
                    onProgress = { progress ->
                        _downloadStates.update {
                            it + (modelData.modeName to it[modelData.modeName]!!.copy(progress = progress))
                        }
                    },
                    onComplete = {
                        addModel(modelData)
                        downloadJobs.remove(modelData.modeName)
                        _downloadStates.update {
                            it + (modelData.modeName to it[modelData.modeName]!!.copy(
                                isDownloading = false, isComplete = true
                            ))
                        }
                    },
                    onError = { e ->
                        downloadJobs.remove(modelData.modeName)
                        _downloadStates.update {
                            it + (modelData.modeName to it[modelData.modeName]!!.copy(
                                isDownloading = false, errorMessage = e.message
                            ))
                        }
                    })
            } catch (e: Exception) {
                downloadJobs.remove(modelData.modeName)
                _downloadStates.update {
                    it + (modelData.modeName to it[modelData.modeName]!!.copy(
                        isDownloading = false, errorMessage = e.message
                    ))
                }
            }
        }

        downloadJobs[modelData.modeName] = job
    }

    fun cancelDownload(modelName: String, modelPath: String) {
        downloadJobs[modelName]?.cancel()
        downloadJobs.remove(modelName)

        File(modelPath).delete()

        _downloadStates.update {
            it + (modelName to DownloadState(isDownloading = false))
        }
    }

    fun addModel(model: ModelsData) {
        viewModelScope.launch {
            ModelManager.addModel(model)
        }
    }

    fun checkIfInstalled(modelName: String, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            val exists = ModelManager.checkIfInstalled(modelName)
            onResult(exists)
        }
    }

    fun removeModel(modelName: String) {
        viewModelScope.launch {
            ModelManager.getModel(modelName)?.let { model ->
                val isLocalImport = model.modelLink.isBlank() && model.modelPageLink.isBlank()
                if (!isLocalImport) {
                    File(model.modelPath).delete()
                }
            }
            ModelManager.removeModel(modelName)
        }
    }
}