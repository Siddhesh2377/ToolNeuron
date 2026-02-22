package com.dark.tool_neuron.viewmodel

import android.app.Application
import android.content.Intent
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.dark.tool_neuron.di.AppContainer
import com.dark.tool_neuron.models.data.HFModelRepository
import com.dark.tool_neuron.models.data.HuggingFaceModel
import com.dark.tool_neuron.models.data.ModelCategory
import com.dark.tool_neuron.models.data.ModelType
import com.dark.tool_neuron.models.table_schema.Model
import com.dark.tool_neuron.repo.ModelRepositoryDataStore
import com.dark.tool_neuron.repo.ModelStoreRepository
import com.dark.tool_neuron.repo.RepositoryValidator
import com.dark.tool_neuron.repo.ValidationResult
import com.dark.tool_neuron.service.ModelDownloadService
import com.dark.tool_neuron.utils.ModelMetadataExtractor
import com.dark.tool_neuron.utils.SizeCategory
import com.dark.tool_neuron.ui.screen.StoreTab
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.File

enum class SortOption {
    NAME,
    SIZE,
    RECENTLY_ADDED
}

class ModelStoreViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = ModelStoreRepository(application)
    private val systemRepo = AppContainer.getModelRepository()
    private val repoDataStore = ModelRepositoryDataStore(application)
    private val repositoryValidator = RepositoryValidator()

    private val _selectedTab = MutableStateFlow(StoreTab.MODELS)
    val selectedTab: StateFlow<StoreTab> = _selectedTab

    private val _models = MutableStateFlow<List<HuggingFaceModel>>(emptyList())
    val models: StateFlow<List<HuggingFaceModel>> = _models

    private val _filteredModels = MutableStateFlow<List<HuggingFaceModel>>(emptyList())
    val filteredModels: StateFlow<List<HuggingFaceModel>> = _filteredModels

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    private val _installedModels = MutableStateFlow<List<Model>>(emptyList())
    val installedModels: StateFlow<List<Model>> = _installedModels

    private val _deviceInfo = MutableStateFlow<Map<String, String>>(emptyMap())
    val deviceInfo: StateFlow<Map<String, String>> = _deviceInfo

    private val _deleteInProgress = MutableStateFlow<String?>(null)
    val deleteInProgress: StateFlow<String?> = _deleteInProgress

    val repositories = repoDataStore.repositories
    val downloadStates = ModelDownloadService.downloadStates

    // Filter states
    private val _selectedModelType = MutableStateFlow<ModelType?>(null)
    val selectedModelType: StateFlow<ModelType?> = _selectedModelType

    private val _selectedCategory = MutableStateFlow<ModelCategory?>(null)
    val selectedCategory: StateFlow<ModelCategory?> = _selectedCategory

    private val _selectedParameters = MutableStateFlow<Set<String>>(emptySet())
    val selectedParameters: StateFlow<Set<String>> = _selectedParameters

    private val _selectedQuantizations = MutableStateFlow<Set<String>>(emptySet())
    val selectedQuantizations: StateFlow<Set<String>> = _selectedQuantizations

    private val _selectedSizeCategory = MutableStateFlow<SizeCategory?>(null)
    val selectedSizeCategory: StateFlow<SizeCategory?> = _selectedSizeCategory

    private val _sortBy = MutableStateFlow(SortOption.NAME)
    val sortBy: StateFlow<SortOption> = _sortBy

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery

    // Validation results
    private val _validationResults = MutableStateFlow<Map<String, ValidationResult>>(emptyMap())
    val validationResults: StateFlow<Map<String, ValidationResult>> = _validationResults

    // App's internal models directory
    private val appModelsDir = File(application.filesDir, "models")

    init {
        loadDeviceInfo()
        loadModels()
        loadInstalledModels()
    }

    private fun loadDeviceInfo() {
        _deviceInfo.value = repository.getDeviceInfo()
    }

    fun selectTab(tab: StoreTab) {
        _selectedTab.value = tab
    }

    fun refreshModels() {
        repository.invalidateCache()
        loadModels()
    }

    fun loadModels() {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null

            try {
                val repos = repositories.first()
                repository.getAvailableModels(repos).onSuccess { modelsList ->
                    _models.value = modelsList
                    applyAllFilters()
                }.onFailure { exception ->
                    _error.value = exception.message
                }
            } catch (e: Exception) {
                _error.value = e.message
            } finally {
                _isLoading.value = false
            }
        }
    }

    private fun loadInstalledModels() {
        viewModelScope.launch {
            try {
                val installedList = systemRepo.getAllModels().first()
                _installedModels.value = installedList
            } catch (e: Exception) {
                Log.e("ModelStoreViewModel", "Error loading installed models", e)
            }
        }
    }

    private fun applyAllFilters() {
        viewModelScope.launch {
            var filtered = _models.value

            // Model type filter (GGUF, SD, TTS)
            _selectedModelType.value?.let { type ->
                filtered = filtered.filter { it.modelType == type }
            }

            // Category filter (repository level) - only applies to GGUF
            _selectedCategory.value?.let { category ->
                val repos = repositories.first()
                val enabledRepos = repos
                    .filter { it.category == category && it.isEnabled }
                    .map { it.id }
                    .toSet()
                filtered = filtered.filter { model ->
                    // Category filter only applies to GGUF models
                    model.modelType != ModelType.GGUF ||
                            enabledRepos.any { model.id.startsWith(it) }
                }
            }

            // Parameter count filter (GGUF only)
            if (_selectedParameters.value.isNotEmpty()) {
                filtered = filtered.filter { model ->
                    if (model.modelType != ModelType.GGUF) true
                    else {
                        val params = ModelMetadataExtractor.extractParameterCount(model.name)
                        params != null && params in _selectedParameters.value
                    }
                }
            }

            // Quantization filter (GGUF only)
            if (_selectedQuantizations.value.isNotEmpty()) {
                filtered = filtered.filter { model ->
                    if (model.modelType != ModelType.GGUF) true
                    else {
                        val quant = ModelMetadataExtractor.extractQuantization(model.name)
                        quant != null && quant in _selectedQuantizations.value
                    }
                }
            }

            // Size category filter
            _selectedSizeCategory.value?.let { sizeCategory ->
                filtered = filtered.filter { model ->
                    ModelMetadataExtractor.extractSizeCategory(model.approximateSize) == sizeCategory
                }
            }

            // Search query filter
            if (_searchQuery.value.isNotBlank()) {
                val query = _searchQuery.value
                filtered = filtered.filter {
                    it.name.contains(query, ignoreCase = true) ||
                            it.description.contains(query, ignoreCase = true) ||
                            it.tags.any { tag -> tag.contains(query, ignoreCase = true) }
                }
            }

            // Apply sorting
            filtered = when (_sortBy.value) {
                SortOption.NAME -> filtered.sortedBy { it.name.lowercase() }
                SortOption.SIZE -> filtered.sortedBy { ModelMetadataExtractor.parseSizeToBytes(it.approximateSize) }
                SortOption.RECENTLY_ADDED -> filtered.reversed()
            }

            _filteredModels.value = filtered
        }
    }

    fun filterModels(query: String) {
        _searchQuery.value = query
        applyAllFilters()
    }

    fun filterByModelType(modelType: ModelType?) {
        _selectedModelType.value = modelType
        applyAllFilters()
    }

    fun filterByCategory(category: ModelCategory?) {
        _selectedCategory.value = category
        applyAllFilters()
    }

    fun toggleParameterFilter(parameter: String) {
        _selectedParameters.value = if (parameter in _selectedParameters.value) {
            _selectedParameters.value - parameter
        } else {
            _selectedParameters.value + parameter
        }
        applyAllFilters()
    }

    fun toggleQuantizationFilter(quantization: String) {
        _selectedQuantizations.value = if (quantization in _selectedQuantizations.value) {
            _selectedQuantizations.value - quantization
        } else {
            _selectedQuantizations.value + quantization
        }
        applyAllFilters()
    }

    fun filterBySizeCategory(sizeCategory: SizeCategory?) {
        _selectedSizeCategory.value = sizeCategory
        applyAllFilters()
    }

    fun setSortOption(sortOption: SortOption) {
        _sortBy.value = sortOption
        applyAllFilters()
    }

    fun clearAllFilters() {
        _selectedModelType.value = null
        _selectedCategory.value = null
        _selectedParameters.value = emptySet()
        _selectedQuantizations.value = emptySet()
        _selectedSizeCategory.value = null
        _sortBy.value = SortOption.NAME
        _searchQuery.value = ""
        applyAllFilters()
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
    }

    fun cancelDownload(modelId: String) {
        val context = getApplication<Application>()
        val intent = Intent(context, ModelDownloadService::class.java).apply {
            action = ModelDownloadService.ACTION_CANCEL_DOWNLOAD
            putExtra(ModelDownloadService.EXTRA_MODEL_ID, modelId)
        }
        context.startService(intent)
    }

    fun deleteModel(model: Model) {
        viewModelScope.launch {
            _deleteInProgress.value = model.id
            try {
                // Delete model file if it's in app's internal directory
                val modelFile = File(model.modelPath)
                if (modelFile.exists() && modelFile.absolutePath.startsWith(appModelsDir.absolutePath)) {
                    val deleted = modelFile.delete()
                    Log.d("ModelStoreViewModel", "Model file deleted: $deleted - ${modelFile.absolutePath}")

                    // If it's a directory (for SD models), delete recursively
                    if (modelFile.isDirectory) {
                        modelFile.deleteRecursively()
                    }
                }

                // Delete config from database
                val config = systemRepo.getConfigByModelId(model.id)
                if (config != null) {
                    systemRepo.deleteConfig(config)
                    Log.d("ModelStoreViewModel", "Model config deleted for: ${model.id}")
                }

                // Delete model from database
                systemRepo.deleteModel(model)
                Log.d("ModelStoreViewModel", "Model deleted from database: ${model.modelName}")

                // Reload installed models
                loadInstalledModels()
            } catch (e: Exception) {
                Log.e("ModelStoreViewModel", "Error deleting model", e)
                _error.value = "Failed to delete model: ${e.message}"
            } finally {
                _deleteInProgress.value = null
            }
        }
    }

    fun addRepository(repo: HFModelRepository) {
        viewModelScope.launch {
            repoDataStore.addRepository(repo)
            loadModels()
        }
    }

    fun removeRepository(repoId: String) {
        viewModelScope.launch {
            repoDataStore.removeRepository(repoId)
            loadModels()
        }
    }

    fun toggleRepository(repoId: String) {
        viewModelScope.launch {
            repoDataStore.toggleRepository(repoId)
            loadModels()
        }
    }

    fun updateRepository(repo: HFModelRepository) {
        viewModelScope.launch {
            repoDataStore.updateRepository(repo)
            loadModels()
        }
    }

    fun validateRepository(repo: HFModelRepository) {
        viewModelScope.launch {
            _validationResults.value += repo.id to ValidationResult.Checking
            val result = repositoryValidator.validateRepository(repo)
            _validationResults.value += repo.id to result
        }
    }

    fun getValidationResult(repoId: String): ValidationResult? {
        return _validationResults.value[repoId]
    }
}