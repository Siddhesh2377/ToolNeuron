package com.dark.tool_neuron.viewmodel

import android.app.Application
import android.content.Intent
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.dark.tool_neuron.di.AppContainer
import com.dark.tool_neuron.models.data.HuggingFaceModel
import com.dark.tool_neuron.models.data.ModelType
import com.dark.tool_neuron.repo.ModelRepository
import com.dark.tool_neuron.repo.ModelStoreRepository
import com.dark.tool_neuron.service.ModelDownloadService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toSet
import kotlinx.coroutines.launch

class ModelStoreViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = ModelStoreRepository(application)
    private val systemRepo = AppContainer.getModelRepository()

    private val _models = MutableStateFlow<List<HuggingFaceModel>>(emptyList())
    val models: StateFlow<List<HuggingFaceModel>> = _models

    private val _filteredModels = MutableStateFlow<List<HuggingFaceModel>>(emptyList())
    val filteredModels: StateFlow<List<HuggingFaceModel>> = _filteredModels

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    private val _installedModels = MutableStateFlow<Set<String>>(emptySet())
    val installedModels: StateFlow<Set<String>> = _installedModels

    val downloadState = ModelDownloadService.downloadState

    init {
        loadModels()
        loadInstalledModels()
    }

    fun loadModels() {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null

            repository.getAvailableModels()
                .onSuccess { modelsList ->
                    _models.value = modelsList
                    _filteredModels.value = modelsList
                }
                .onFailure { exception ->
                    _error.value = exception.message
                }

            _isLoading.value = false
        }
    }

    private fun loadInstalledModels() {
        viewModelScope.launch {
            try {
                val installedList = systemRepo.getAllModels().first() // Get first emission only
                _installedModels.value = installedList.map { it.modelName }.toSet()
            } catch (e: Exception) {
                Log.e("ModelStoreViewModel", "Error loading installed models", e)
            }
        }
    }

    fun filterModels(query: String) {
        _filteredModels.value = if (query.isBlank()) {
            _models.value
        } else {
            _models.value.filter {
                it.name.contains(query, ignoreCase = true) ||
                        it.description.contains(query, ignoreCase = true)
            }
        }
    }

    fun filterByType(modelType: ModelType?) {
        _filteredModels.value = if (modelType == null) {
            _models.value
        } else {
            _models.value.filter { it.modelType == modelType }
        }
    }

    fun downloadModel(model: HuggingFaceModel) {
        val context = getApplication<Application>()
        val fileUrl = "https://huggingface.co/${model.fileUri}"

        val intent = Intent(context, ModelDownloadService::class.java).apply {
            action = ModelDownloadService.ACTION_START_DOWNLOAD
            putExtra(ModelDownloadService.EXTRA_MODEL_ID, model.id)
            putExtra(ModelDownloadService.EXTRA_MODEL_NAME, model.name)
            putExtra(ModelDownloadService.EXTRA_FILE_URL, fileUrl)
            putExtra(ModelDownloadService.EXTRA_IS_ZIP, model.isZip)
            putExtra(ModelDownloadService.EXTRA_MODEL_TYPE, model.modelType.name)
            putExtra(ModelDownloadService.EXTRA_RUN_ON_CPU, model.runOnCpu)
            putExtra(ModelDownloadService.EXTRA_TEXT_EMBEDDING_SIZE, model.textEmbeddingSize)
        }

        context.startForegroundService(intent)

        // Refresh installed models after download completes
        // You might want to observe the download completion and then refresh
    }

    fun isInstalled(modelName: String): Boolean {
        return _installedModels.value.contains(modelName)
    }

    fun cancelDownload() {
        val context = getApplication<Application>()
        val intent = Intent(context, ModelDownloadService::class.java).apply {
            action = ModelDownloadService.ACTION_CANCEL_DOWNLOAD
        }
        context.startService(intent)
    }
}