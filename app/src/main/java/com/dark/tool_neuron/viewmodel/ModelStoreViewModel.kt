package com.dark.tool_neuron.viewmodel

import android.content.Context
import android.net.Uri
import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dark.download_manager.HxdManager
import com.dark.download_manager.HxdState
import com.dark.download_manager.HxdStatus
import com.dark.tool_neuron.model.HFRepository
import com.dark.tool_neuron.model.HuggingFaceModel
import com.dark.tool_neuron.model.ModelCategory
import com.dark.tool_neuron.model.ModelConfig
import com.dark.tool_neuron.model.ModelInfo
import com.dark.tool_neuron.model.SizeCategory
import com.dark.tool_neuron.model.enums.PathType
import com.dark.tool_neuron.model.enums.ProviderType
import com.dark.tool_neuron.repo.ExplorerRepo
import com.dark.tool_neuron.repo.HuggingFaceExplorer
import com.dark.tool_neuron.repo.ModelCatalog
import com.dark.tool_neuron.repo.ModelRepository
import com.dark.tool_neuron.repo.RepositoryDataStore
import com.dark.tool_neuron.repo.RepositoryValidator
import com.dark.tool_neuron.repo.ValidationResult
import com.dark.tool_neuron.service.inference.InferenceClient
import com.dark.tool_neuron.util.extractParameterCount
import com.dark.tool_neuron.util.extractQuantization
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.security.MessageDigest
import javax.inject.Inject

enum class StoreTab { MODELS, INSTALLED, SETTINGS }

enum class SortOption { NAME, SIZE, RECENTLY_ADDED }

data class RepoGroupInfo(
    val displayName: String,
    val author: String,
    val modelCount: Int,
)

@HiltViewModel
class ModelStoreViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val modelRepo: ModelRepository,
    private val catalog: ModelCatalog,
    private val repoDataStore: RepositoryDataStore,
    private val explorer: HuggingFaceExplorer,
    private val validator: RepositoryValidator,
) : ViewModel() {

    val installedModels: StateFlow<List<ModelInfo>> = modelRepo.models
    val repositories: StateFlow<List<HFRepository>> = repoDataStore.repositories

    private val _selectedTab = MutableStateFlow(StoreTab.MODELS)
    val selectedTab: StateFlow<StoreTab> = _selectedTab.asStateFlow()

    private val _models = MutableStateFlow<List<HuggingFaceModel>>(emptyList())
    val models: StateFlow<List<HuggingFaceModel>> = _models.asStateFlow()

    private val _filteredModels = MutableStateFlow<List<HuggingFaceModel>>(emptyList())
    val filteredModels: StateFlow<List<HuggingFaceModel>> = _filteredModels.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _deleteInProgress = MutableStateFlow<String?>(null)
    val deleteInProgress: StateFlow<String?> = _deleteInProgress.asStateFlow()

    private val _deviceInfo = MutableStateFlow<Map<String, String>>(emptyMap())
    val deviceInfo: StateFlow<Map<String, String>> = _deviceInfo.asStateFlow()

    private val _downloadIds = MutableStateFlow<Map<String, Int>>(emptyMap())
    private val _downloadStates = MutableStateFlow<Map<String, HxdState>>(emptyMap())
    val downloadStates: StateFlow<Map<String, HxdState>> = _downloadStates.asStateFlow()

    val isModelLoaded = InferenceClient.isModelLoaded
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    // Filter states
    private val _selectedModelType = MutableStateFlow<String?>(null)
    val selectedModelType: StateFlow<String?> = _selectedModelType.asStateFlow()

    private val _selectedCategory = MutableStateFlow<ModelCategory?>(null)
    val selectedCategory: StateFlow<ModelCategory?> = _selectedCategory.asStateFlow()

    private val _selectedParameters = MutableStateFlow<Set<String>>(emptySet())
    val selectedParameters: StateFlow<Set<String>> = _selectedParameters.asStateFlow()

    private val _selectedQuantizations = MutableStateFlow<Set<String>>(emptySet())
    val selectedQuantizations: StateFlow<Set<String>> = _selectedQuantizations.asStateFlow()

    private val _selectedSizeCategory = MutableStateFlow<SizeCategory?>(null)
    val selectedSizeCategory: StateFlow<SizeCategory?> = _selectedSizeCategory.asStateFlow()

    private val _selectedTags = MutableStateFlow<Set<String>>(emptySet())
    val selectedTags: StateFlow<Set<String>> = _selectedTags.asStateFlow()

    private val _showNsfw = MutableStateFlow(true)
    val showNsfw: StateFlow<Boolean> = _showNsfw.asStateFlow()

    private val _executionTarget = MutableStateFlow<String?>(null)
    val executionTarget: StateFlow<String?> = _executionTarget.asStateFlow()

    private val _sortBy = MutableStateFlow(SortOption.NAME)
    val sortBy: StateFlow<SortOption> = _sortBy.asStateFlow()

    private val _searchQuery = MutableStateFlow("")

    private val _selectedRepository = MutableStateFlow<String?>(null)
    val selectedRepository: StateFlow<String?> = _selectedRepository.asStateFlow()

    // Validation
    private val _validationResults = MutableStateFlow<Map<String, ValidationResult>>(emptyMap())
    val validationResults: StateFlow<Map<String, ValidationResult>> = _validationResults.asStateFlow()

    // Explorer
    private val _explorerQuery = MutableStateFlow("")
    val explorerQuery: StateFlow<String> = _explorerQuery.asStateFlow()

    private val _explorerResults = MutableStateFlow<List<ExplorerRepo>>(emptyList())
    val explorerResults: StateFlow<List<ExplorerRepo>> = _explorerResults.asStateFlow()

    private val _isExplorerLoading = MutableStateFlow(false)
    val isExplorerLoading: StateFlow<Boolean> = _isExplorerLoading.asStateFlow()

    private val _explorerError = MutableStateFlow<String?>(null)
    val explorerError: StateFlow<String?> = _explorerError.asStateFlow()

    private var explorerSearchJob: Job? = null

    init {
        loadDeviceInfo()
        loadModels()
    }

    fun selectTab(tab: StoreTab) { _selectedTab.value = tab }

    fun selectRepository(repoKey: String?) { _selectedRepository.value = repoKey }

    fun loadModels() {
        viewModelScope.launch(Dispatchers.IO) {
            _isLoading.value = true
            _error.value = null
            try {
                val repos = repoDataStore.repositories.value
                _models.value = catalog.getModels(repos)
                applyAllFilters()
            } catch (e: Exception) {
                _error.value = e.message ?: "Failed to load models"
            }
            _isLoading.value = false
        }
    }

    fun refreshModels() {
        viewModelScope.launch(Dispatchers.IO) {
            _isLoading.value = true
            _error.value = null
            try {
                catalog.clearCache()
                val repos = repoDataStore.repositories.value
                _models.value = catalog.getModels(repos, forceRefresh = true)
                applyAllFilters()
            } catch (e: Exception) {
                _error.value = e.message ?: "Failed to refresh"
            }
            _isLoading.value = false
        }
    }

    private fun loadDeviceInfo() {
        val soc = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) Build.SOC_MODEL else "Unknown"
        _deviceInfo.value = mapOf(
            "device" to "${Build.MANUFACTURER} ${Build.MODEL}",
            "soc" to soc,
            "arch" to (Build.SUPPORTED_ABIS.firstOrNull() ?: "unknown"),
            "sdk" to "${Build.VERSION.SDK_INT}",
        )
    }

    // ── Filtering ──

    private fun applyAllFilters() {
        var filtered = _models.value

        _selectedCategory.value?.let { cat ->
            val repos = repoDataStore.repositories.value
            val matching = repos.filter { it.category == cat && it.isEnabled }.map { it.id }.toSet()
            filtered = filtered.filter { model -> matching.any { model.id.startsWith(it) } }
        }

        if (_selectedParameters.value.isNotEmpty()) {
            filtered = filtered.filter { model ->
                val params = extractParameterCount(model.name)
                params != null && params in _selectedParameters.value
            }
        }

        if (_selectedQuantizations.value.isNotEmpty()) {
            filtered = filtered.filter { model ->
                val quant = extractQuantization(model.name)
                quant != null && quant in _selectedQuantizations.value
            }
        }

        _selectedSizeCategory.value?.let { size ->
            filtered = filtered.filter { SizeCategory.fromSize(it.approximateSize) == size }
        }

        if (_selectedTags.value.isNotEmpty()) {
            filtered = filtered.filter { model ->
                _selectedTags.value.all { tag -> tag in model.tags }
            }
        }

        if (!_showNsfw.value) {
            filtered = filtered.filter { "NSFW" !in it.tags }
        }

        _selectedModelType.value?.let { type ->
            filtered = filtered.filter { it.modelType == type }
        }

        _executionTarget.value?.let { target ->
            filtered = filtered.filter { target in it.tags }
        }

        if (_searchQuery.value.isNotBlank()) {
            val q = _searchQuery.value
            filtered = filtered.filter {
                it.name.contains(q, ignoreCase = true) ||
                it.tags.any { t -> t.contains(q, ignoreCase = true) }
            }
        }

        filtered = when (_sortBy.value) {
            SortOption.NAME -> filtered.sortedBy { it.name.lowercase() }
            SortOption.SIZE -> filtered.sortedBy { SizeCategory.parseSizeToBytes(it.approximateSize) }
            SortOption.RECENTLY_ADDED -> filtered.reversed()
        }

        _filteredModels.value = filtered
    }

    fun filterModels(query: String) { _searchQuery.value = query; applyAllFilters() }
    fun filterByModelType(type: String?) { _selectedModelType.value = type; applyAllFilters() }
    fun filterByCategory(cat: ModelCategory?) { _selectedCategory.value = cat; applyAllFilters() }
    fun toggleParameterFilter(p: String) {
        _selectedParameters.value = if (p in _selectedParameters.value) _selectedParameters.value - p else _selectedParameters.value + p
        applyAllFilters()
    }
    fun toggleQuantizationFilter(q: String) {
        _selectedQuantizations.value = if (q in _selectedQuantizations.value) _selectedQuantizations.value - q else _selectedQuantizations.value + q
        applyAllFilters()
    }
    fun filterBySizeCategory(s: SizeCategory?) { _selectedSizeCategory.value = s; applyAllFilters() }
    fun setSortOption(o: SortOption) { _sortBy.value = o; applyAllFilters() }
    fun toggleTagFilter(tag: String) {
        _selectedTags.value = if (tag in _selectedTags.value) _selectedTags.value - tag else _selectedTags.value + tag
        applyAllFilters()
    }
    fun setShowNsfw(show: Boolean) { _showNsfw.value = show; applyAllFilters() }
    fun setExecutionTarget(t: String?) { _executionTarget.value = t; applyAllFilters() }
    fun clearAllFilters() {
        _selectedModelType.value = null; _selectedCategory.value = null
        _selectedParameters.value = emptySet(); _selectedQuantizations.value = emptySet()
        _selectedSizeCategory.value = null; _selectedTags.value = emptySet()
        _showNsfw.value = true; _executionTarget.value = null
        _sortBy.value = SortOption.NAME; _searchQuery.value = ""
        applyAllFilters()
    }

    fun getAvailableTags(): List<String> =
        _models.value.flatMap { it.tags }.distinct()
            .filter { it !in listOf("GGUF") && !it.matches(Regex("Q\\d.*")) }
            .sorted()

    fun getGroupedRepos(): Map<String, RepoGroupInfo> {
        val repos = repoDataStore.repositories.value
        val repoNameLookup = repos.associate { it.repoPath to it.name }
        val grouped = mutableMapOf<String, RepoGroupInfo>()

        _filteredModels.value.groupBy { it.repoId }.forEach { (repoId, models) ->
            val repoPath = repos.find { it.id == repoId }?.repoPath ?: repoId
            val displayName = repoNameLookup[repoPath] ?: repoId.substringAfterLast("/")
            val author = if (repoPath.contains("/")) repoPath.substringBefore("/") else ""
            grouped[repoId] = RepoGroupInfo(displayName, author, models.size)
        }
        return grouped
    }

    fun getModelsForRepo(repoKey: String): List<HuggingFaceModel> =
        _filteredModels.value.filter { it.repoId == repoKey }

    // ── Downloads ──

    fun downloadModel(model: HuggingFaceModel) {
        if (_downloadIds.value.containsKey(model.id)) return
        _downloadStates.value = _downloadStates.value - model.id
        val destFile = modelRepo.modelFile(model.id, model.fileName)
        val hxdId = HxdManager.enqueue(context, model.fileUri, destFile.absolutePath)
        _downloadIds.value = _downloadIds.value + (model.id to hxdId)

        viewModelScope.launch(Dispatchers.IO) {
            HxdManager.observe(hxdId).collect { state ->
                if (state == null) return@collect
                _downloadStates.value = _downloadStates.value + (model.id to state)

                when (state.status) {
                    HxdStatus.COMPLETED -> {
                        val provider = when (model.modelType) {
                            "tts" -> ProviderType.TTS
                            "stt" -> ProviderType.STT
                            else -> ProviderType.GGUF
                        }
                        modelRepo.insert(ModelInfo(
                            id = model.id, name = model.name,
                            path = destFile.absolutePath, pathType = PathType.FILE,
                            providerType = provider,
                            fileSize = if (model.sizeBytes > 0) model.sizeBytes else destFile.length(),
                        ))
                        _downloadIds.value = _downloadIds.value - model.id
                        _downloadStates.value = _downloadStates.value - model.id
                    }
                    HxdStatus.FAILED -> {
                        _downloadIds.value = _downloadIds.value - model.id
                    }
                    HxdStatus.CANCELLED -> {
                        _downloadIds.value = _downloadIds.value - model.id
                        _downloadStates.value = _downloadStates.value - model.id
                    }
                    else -> {}
                }
            }
        }
    }

    fun cancelDownload(modelId: String) {
        _downloadIds.value[modelId]?.let { HxdManager.cancel(it) }
        _downloadIds.value = _downloadIds.value - modelId
        _downloadStates.value = _downloadStates.value - modelId
    }

    // ── Model management ──

    fun deleteModel(model: ModelInfo) {
        viewModelScope.launch(Dispatchers.IO) {
            _deleteInProgress.value = model.id
            try {
                if (model.pathType == PathType.FILE) java.io.File(model.path).delete()
                modelRepo.delete(model.id)
            } finally {
                _deleteInProgress.value = null
            }
        }
    }

    fun loadModel(model: ModelInfo) {
        viewModelScope.launch {
            val config = modelRepo.getConfig(model.id)
            val configJson = buildConfigJson(config)
            when (model.pathType) {
                PathType.FILE -> InferenceClient.loadModel(model.path, configJson)
                PathType.CONTENT_URI -> InferenceClient.loadModelFromUri(context, Uri.parse(model.path), configJson)
            }
            modelRepo.setActive(model.id)
        }
    }

    fun unloadModel() {
        viewModelScope.launch { InferenceClient.unloadModel() }
    }

    fun importLocalModel(uri: Uri, fileName: String, fileSize: Long) {
        modelRepo.insert(ModelInfo(
            id = checksumId(uri.toString(), fileName, fileSize),
            name = fileName.removeSuffix(".gguf"),
            path = uri.toString(), pathType = PathType.CONTENT_URI,
            providerType = ProviderType.GGUF, fileSize = fileSize,
        ))
    }

    suspend fun getModelConfig(modelId: String): ModelConfig? = modelRepo.getConfig(modelId)

    // ── Repository management ──

    fun addRepository(repo: HFRepository) {
        viewModelScope.launch { repoDataStore.addRepository(repo); loadModels() }
    }

    fun removeRepository(repoId: String) {
        viewModelScope.launch { repoDataStore.removeRepository(repoId); loadModels() }
    }

    fun toggleRepository(repoId: String) {
        viewModelScope.launch { repoDataStore.toggleRepository(repoId); loadModels() }
    }

    fun updateRepository(repo: HFRepository) {
        viewModelScope.launch { repoDataStore.updateRepository(repo); loadModels() }
    }

    fun validateRepository(repo: HFRepository) {
        viewModelScope.launch {
            _validationResults.value += repo.id to ValidationResult.Checking
            _validationResults.value += repo.id to validator.validate(repo)
        }
    }

    // ── Explorer ──

    fun setExplorerQuery(query: String) {
        _explorerQuery.value = query
        if (query.isBlank()) { _explorerResults.value = emptyList(); _explorerError.value = null }
    }

    fun searchExplorerRepositories() {
        explorerSearchJob?.cancel()
        explorerSearchJob = viewModelScope.launch {
            val q = _explorerQuery.value.trim()
            if (q.isBlank()) { _explorerError.value = "Enter a search term"; return@launch }
            _isExplorerLoading.value = true; _explorerError.value = null
            try {
                explorer.searchGgufRepos(q).onSuccess { repos ->
                    _explorerResults.value = repos
                    if (repos.isEmpty()) _explorerError.value = "No repositories found"
                }.onFailure { _explorerResults.value = emptyList(); _explorerError.value = it.message }
            } finally { _isExplorerLoading.value = false }
        }
    }

    fun addExplorerRepository(repo: ExplorerRepo) {
        viewModelScope.launch {
            val existing = repoDataStore.repositories.value
            if (existing.any { it.repoPath.equals(repo.id, ignoreCase = true) }) {
                _explorerError.value = "Repository already added"; return@launch
            }
            repoDataStore.addRepository(HFRepository(
                id = "hf-${repo.id.replace("/", "-").lowercase()}",
                name = repo.id.substringAfter("/"),
                repoPath = repo.id,
            ))
            _explorerError.value = null
            loadModels()
        }
    }

    // ── Private ──

    private fun buildConfigJson(config: ModelConfig?): String {
        if (config == null) return "{}"
        val sb = StringBuilder(256).append('{')
        val loading = config.loadingParamsJson
        val inference = config.inferenceParamsJson
        if (loading != "{}" && loading.isNotBlank()) {
            val inner = loading.trim().removePrefix("{").removeSuffix("}")
            if (inner.isNotBlank()) sb.append(inner).append(',')
        }
        if (inference != "{}" && inference.isNotBlank()) {
            val inner = inference.trim().removePrefix("{").removeSuffix("}")
            if (inner.isNotBlank()) sb.append(inner)
        }
        if (sb.last() == ',') sb.deleteCharAt(sb.length - 1)
        sb.append('}')
        return sb.toString()
    }

    private fun checksumId(uri: String, name: String, size: Long): String {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(uri.toByteArray()); digest.update(name.toByteArray()); digest.update(size.toString().toByteArray())
        return digest.digest().take(16).joinToString("") { "%02x".format(it) }
    }
}
