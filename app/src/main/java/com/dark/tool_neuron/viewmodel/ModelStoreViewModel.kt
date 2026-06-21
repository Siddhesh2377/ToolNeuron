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
import com.dark.tool_neuron.model.ModelTaxonomy
import com.dark.tool_neuron.model.SizeCategory
import com.dark.tool_neuron.model.enums.PathType
import com.dark.tool_neuron.model.enums.ProviderType
import com.dark.tool_neuron.repo.DownloadCoordinator
import com.dark.tool_neuron.repo.ExplorerRepo
import com.dark.tool_neuron.repo.HuggingFaceExplorer
import com.dark.tool_neuron.repo.InstallProgressTracker
import com.dark.tool_neuron.repo.BackupProgress
import com.dark.tool_neuron.repo.BackupPreview
import com.dark.tool_neuron.repo.ModelBackupManager
import com.dark.tool_neuron.repo.ModelCatalog
import com.dark.tool_neuron.repo.ModelRepository
import com.dark.tool_neuron.repo.RagManager
import com.dark.tool_neuron.repo.RepositoryDataStore
import com.dark.tool_neuron.service.server.ServerController
import com.dark.tool_neuron.viewmodel.home_vm.ModelSessionManager
import com.dark.tool_neuron.repo.RepositoryValidator
import com.dark.tool_neuron.repo.ValidationResult
import com.dark.tool_neuron.data.AppPreferences
import com.dark.tool_neuron.data.UserNotificationManager
import com.dark.tool_neuron.service.inference.InferenceClient
import com.dark.tool_neuron.util.extractParameterCount
import com.dark.tool_neuron.util.extractQuantization
import com.dark.tool_neuron.voice.VoiceArchive
import com.dark.tool_neuron.voice.VoiceKind
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.security.MessageDigest
import javax.inject.Inject

enum class StoreTab { MODELS, INSTALLED, SETTINGS }

enum class SortOption { NAME, SIZE, RECENTLY_ADDED }

data class RepoGroupInfo(
    val displayName: String,
    val author: String,
    val modelCount: Int,
    val priority: Int = 999,
)

@HiltViewModel
class ModelStoreViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val modelRepo: ModelRepository,
    private val catalog: ModelCatalog,
    private val repoDataStore: RepositoryDataStore,
    private val explorer: HuggingFaceExplorer,
    private val validator: RepositoryValidator,
    private val ragManager: RagManager,
    private val prefs: AppPreferences,
    private val serverController: ServerController,
    private val installProgress: InstallProgressTracker,
    private val modelSession: ModelSessionManager,
    private val downloadCoordinator: DownloadCoordinator,
    private val modelBackup: ModelBackupManager,
    private val userNotifications: UserNotificationManager,
) : ViewModel() {

    val installedModels: StateFlow<List<ModelInfo>> = modelRepo.models
    val activeDownloadCount: StateFlow<Int> = downloadCoordinator.activeCount
    val activeDownloadLabels: StateFlow<Map<Int, com.dark.tool_neuron.repo.DownloadLabel>> = downloadCoordinator.labelsFlow
    val repositories: StateFlow<List<HFRepository>> = repoDataStore.repositories
    val defaultEmbeddingModelId: StateFlow<String?> = ragManager.defaultEmbeddingModelId

    fun setDefaultEmbeddingModel(modelId: String?) {
        ragManager.setDefaultEmbeddingModelId(modelId)
    }

    private val _selectedTab = MutableStateFlow(StoreTab.MODELS)
    val selectedTab: StateFlow<StoreTab> = _selectedTab.asStateFlow()

    private val _models = MutableStateFlow<List<HuggingFaceModel>>(emptyList())
    val models: StateFlow<List<HuggingFaceModel>> = _models.asStateFlow()

    private val _filteredModels = MutableStateFlow<List<HuggingFaceModel>>(emptyList())
    val filteredModels: StateFlow<List<HuggingFaceModel>> = _filteredModels.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _deleteInProgress = MutableStateFlow<String?>(null)
    val deleteInProgress: StateFlow<String?> = _deleteInProgress.asStateFlow()

    private val _backupStatus = MutableStateFlow<String?>(null)
    val backupStatus: StateFlow<String?> = _backupStatus.asStateFlow()
    private val _backupProgress = MutableStateFlow<BackupProgress?>(null)
    val backupProgress: StateFlow<BackupProgress?> = _backupProgress.asStateFlow()
    private val _backupImportPreview = MutableStateFlow<BackupPreview?>(null)
    val backupImportPreview: StateFlow<BackupPreview?> = _backupImportPreview.asStateFlow()
    private var pendingImportUri: Uri? = null

    private val _deviceInfo = MutableStateFlow<Map<String, String>>(emptyMap())
    val deviceInfo: StateFlow<Map<String, String>> = _deviceInfo.asStateFlow()

    private val _downloadIds = MutableStateFlow<Map<String, Int>>(emptyMap())
    private val _downloadStates = MutableStateFlow<Map<String, HxdState>>(emptyMap())
    val downloadStates: StateFlow<Map<String, HxdState>> = _downloadStates.asStateFlow()

    private val _installedMmprojIds = MutableStateFlow<Set<String>>(emptySet())
    val installedMmprojIds: StateFlow<Set<String>> = _installedMmprojIds.asStateFlow()

    private val _extractingIds = MutableStateFlow<Set<String>>(emptySet())
    val extractingIds: StateFlow<Set<String>> = _extractingIds.asStateFlow()

    private val _extractingFile = MutableStateFlow<Map<String, String>>(emptyMap())
    val extractingFile: StateFlow<Map<String, String>> = _extractingFile.asStateFlow()

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
            _isRefreshing.value = true
            _error.value = null
            try {
                catalog.clearCache()
                val repos = repoDataStore.repositories.value
                _models.value = catalog.getModels(repos, forceRefresh = true)
                applyAllFilters()
            } catch (e: Exception) {
                _error.value = e.message ?: "Failed to refresh"
            }
            _isRefreshing.value = false
        }
    }

    fun exportModels(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            _backupStatus.value = "Exporting models..."
            _backupProgress.value = BackupProgress("Preparing export")
            try {
                val models = modelRepo.models.value
                modelBackup.exportTo(uri, models) { progress -> _backupProgress.value = progress }
                _backupStatus.value = "Exported ${models.size} models"
                userNotifications.notifyBackupFinished("Model export complete", "Exported ${models.size} models.")
            } catch (t: Throwable) {
                val detail = "Export failed: ${t.message ?: t.javaClass.simpleName}"
                _backupStatus.value = detail
                userNotifications.notifyBackupFinished("Model export failed", detail)
            } finally {
                _backupProgress.value = null
            }
        }
    }

    fun importModels(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            _backupStatus.value = "Importing models..."
            _backupProgress.value = BackupProgress("Preparing import")
            try {
                val count = modelBackup.importFrom(uri) { progress -> _backupProgress.value = progress }
                refreshServerCatalog()
                _backupStatus.value = "Imported $count models"
                userNotifications.notifyBackupFinished("Model import complete", "Imported $count models.")
            } catch (t: Throwable) {
                val detail = "Import failed: ${t.message ?: t.javaClass.simpleName}"
                _backupStatus.value = detail
                userNotifications.notifyBackupFinished("Model import failed", detail)
            } finally {
                _backupProgress.value = null
            }
        }
    }

    fun previewImport(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                pendingImportUri = uri
                _backupImportPreview.value = modelBackup.preview(uri)
            } catch (t: Throwable) {
                _backupStatus.value = "Import preview failed: ${t.message ?: t.javaClass.simpleName}"
            }
        }
    }

    fun confirmPreviewImport(
        selectedIds: Set<String>,
        overwriteExisting: Boolean,
        restoreSettings: Boolean = true,
    ) {
        val uri = pendingImportUri ?: return
        viewModelScope.launch(Dispatchers.IO) {
            _backupImportPreview.value = null
            _backupStatus.value = "Importing models..."
            _backupProgress.value = BackupProgress("Preparing import")
            try {
                val count = modelBackup.importFrom(
                    uri = uri,
                    selectedIds = selectedIds,
                    overwriteExisting = overwriteExisting,
                    restoreSettings = restoreSettings,
                ) { progress -> _backupProgress.value = progress }
                refreshServerCatalog()
                _backupStatus.value = "Imported $count models"
                userNotifications.notifyBackupFinished("Model import complete", "Imported $count models.")
            } catch (t: Throwable) {
                val detail = "Import failed: ${t.message ?: t.javaClass.simpleName}"
                _backupStatus.value = detail
                userNotifications.notifyBackupFinished("Model import failed", detail)
            } finally {
                pendingImportUri = null
                _backupProgress.value = null
            }
        }
    }

    fun dismissImportPreview() {
        pendingImportUri = null
        _backupImportPreview.value = null
    }

    fun clearBackupStatus() {
        _backupStatus.value = null
    }

    private fun refreshServerCatalog() {
        serverController.refreshCatalogIfRunning()
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
            filtered = filtered.filter { SizeCategory.fromBytes(sizeBytesOf(it)) == size }
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
            filtered = filtered.filter {
                when (type) {
                    "vlm" -> it.isVlm && !it.isMmproj
                    "gguf" -> it.modelType == "gguf" && !it.isVlm && !it.isMmproj
                    else -> it.modelType == type
                }
            }
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
            SortOption.SIZE -> filtered.sortedBy { sizeBytesOf(it).takeIf { bytes -> bytes > 0 } ?: Long.MAX_VALUE }
            SortOption.RECENTLY_ADDED -> filtered.reversed()
        }

        _filteredModels.value = filtered
        refreshInstalledMmproj()
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

    private fun sizeBytesOf(model: HuggingFaceModel): Long =
        model.sizeBytes.takeIf { it > 0 } ?: SizeCategory.parseSizeToBytes(model.approximateSize)

    fun getAvailableTags(): List<String> =
        _models.value.flatMap { it.tags }.distinct()
            .filter { it !in listOf("GGUF") && !it.matches(Regex("Q\\d.*")) }
            .sorted()

    fun getGroupedRepos(): Map<String, RepoGroupInfo> {
        val grouped = mutableMapOf<String, RepoGroupInfo>()

        _filteredModels.value.groupBy { ModelTaxonomy.groupKey(it) }.forEach { (key, models) ->
            val family = ModelTaxonomy.family(models.first())
            val taskSummary = models
                .map { ModelTaxonomy.task(it).displayName }
                .distinct()
                .take(4)
                .joinToString(" / ")
            grouped[key] = RepoGroupInfo(
                displayName = family.displayName,
                author = taskSummary,
                modelCount = models.size,
                priority = family.priority,
            )
        }
        return grouped.entries
            .sortedWith(compareBy<Map.Entry<String, RepoGroupInfo>> { it.value.priority }.thenBy { it.value.displayName })
            .associate { it.key to it.value }
    }

    fun getModelsForRepo(repoKey: String): List<HuggingFaceModel> =
        _filteredModels.value.filter { ModelTaxonomy.groupKey(it) == repoKey }
            .sortedWith(compareBy<HuggingFaceModel> { ModelTaxonomy.task(it).ordinal }
                .thenBy { sizeBytesOf(it).takeIf { bytes -> bytes > 0 } ?: Long.MAX_VALUE }
                .thenBy { it.name.lowercase() })


    fun downloadByQuickStartId(modelId: String) {
        viewModelScope.launch {
            val pool = _filteredModels.value.takeIf { it.isNotEmpty() }
                ?: _filteredModels.first { it.isNotEmpty() }
            enqueueChatModel(pool, modelId)
        }
    }

    fun downloadPack(packId: String) {
        val ids = PACK_CONTENTS[packId] ?: return
        viewModelScope.launch {
            val pool = _models.value.takeIf { it.isNotEmpty() }
                ?: _models.first { it.isNotEmpty() }
            ids.forEach { entry ->
                when (entry.kind) {
                    PackEntryKind.Chat -> enqueueChatModel(pool, entry.id)
                    PackEntryKind.Voice -> enqueueVoiceModel(pool, entry.id)
                }
            }
        }
    }

    private fun enqueueChatModel(pool: List<HuggingFaceModel>, modelId: String) {
        val candidates = pool.filter { it.repoId == modelId || it.id.startsWith(modelId) }
        val preferred = QUICK_START_QUANT_PRIORITY.firstNotNullOfOrNull { q ->
            candidates.firstOrNull { it.quantization.equals(q, ignoreCase = true) }
        } ?: candidates.filter { it.sizeBytes > 0 }.minByOrNull { it.sizeBytes }
            ?: candidates.firstOrNull()
        if (preferred != null) downloadModel(preferred)
    }

    private fun enqueueVoiceModel(pool: List<HuggingFaceModel>, modelId: String) {
        val match = pool.firstOrNull { it.id == modelId } ?: return
        downloadModel(match)
    }

    companion object {
        val QUICK_START_QUANT_PRIORITY = listOf("Q4_K_M", "Q4_K_S", "Q4_0", "Q5_K_M", "Q5_K_S", "Q8_0")

        const val PACK_CHAT_ONLY = "pack_chat_only"
        const val PACK_CHAT_VOICE = "pack_chat_voice"
        const val PACK_LARGE_CHAT_VOICE = "pack_large_chat_voice"

        private val PACK_CONTENTS: Map<String, List<PackEntry>> = mapOf(
            PACK_CHAT_ONLY to listOf(
                PackEntry("lfm25-350m", PackEntryKind.Chat),
            ),
            PACK_CHAT_VOICE to listOf(
                PackEntry("lfm25-350m", PackEntryKind.Chat),
                PackEntry("sherpa-onnx-whisper-tiny-en", PackEntryKind.Voice),
                PackEntry("vits-piper-en_US-amy-low", PackEntryKind.Voice),
            ),
            PACK_LARGE_CHAT_VOICE to listOf(
                PackEntry("qwen3-0.6b", PackEntryKind.Chat),
                PackEntry("sherpa-onnx-whisper-tiny-en", PackEntryKind.Voice),
                PackEntry("vits-piper-en_US-amy-low", PackEntryKind.Voice),
            ),
        )
    }

    private enum class PackEntryKind { Chat, Voice }
    private data class PackEntry(val id: String, val kind: PackEntryKind)

    fun downloadModel(model: HuggingFaceModel) {
        if (_downloadIds.value.containsKey(model.id)) return

        val destFile = when {
            model.isMmproj && model.repoPath.isNotBlank() ->
                modelRepo.vlmMmprojFile(model.repoPath, model.fileName)
            model.isVlm && model.repoPath.isNotBlank() ->
                modelRepo.vlmModelFile(model.repoPath, model.fileName)
            model.modelType == "tts" ->
                modelRepo.voiceArchiveFile("tts", model.id, model.fileName)
            model.modelType == "stt" ->
                modelRepo.voiceArchiveFile("stt", model.id, model.fileName)
            model.modelType == "image_gen" ->
                modelRepo.imageModelArchive(model.id, model.fileName)
            model.modelType == "image_upscaler" ->
                modelRepo.imageUpscalerFile(model.id, model.fileName)
            else ->
                modelRepo.modelFile(model.id, model.fileName)
        }
        destFile.parentFile?.mkdirs()

        val hxdId = HxdManager.enqueue(context, model.fileUri, destFile.absolutePath)
        downloadCoordinator.registerLabel(hxdId, model.name, downloadTypeOf(model))
        _downloadIds.value = _downloadIds.value + (model.id to hxdId)
        _downloadStates.value = _downloadStates.value + (model.id to
            HxdState(hxdId, model.fileUri, destFile.absolutePath, 0L, -1L, 0L, HxdStatus.QUEUED))

        viewModelScope.launch(Dispatchers.IO) {
            HxdManager.observe(hxdId).collect { state ->
                if (state == null) return@collect
                _downloadStates.value = _downloadStates.value + (model.id to state)

                when (state.status) {
                    HxdStatus.COMPLETED -> {
                        if (model.isMmproj) {
                            finalizeMmprojDownload(model, destFile)
                        } else if (model.isVlm && model.mmprojFileUri.isNotBlank()) {
                            val mmprojFile = modelRepo.vlmMmprojFile(model.repoPath, model.mmprojFileName)
                            downloadVlmProjectorThenFinalize(model, destFile, mmprojFile)
                        } else if (model.isVlm) {
                            _error.value = "${model.name} is missing a vision projector file"
                            _downloadIds.value = _downloadIds.value - model.id
                            _downloadStates.value = _downloadStates.value - model.id
                        } else if (model.modelType == "tts" || model.modelType == "stt") {
                            finalizeVoiceDownload(model, destFile)
                        } else if (model.modelType == "image_gen") {
                            finalizeImageGenDownload(model, destFile)
                        } else if (model.modelType == "image_upscaler") {
                            finalizeImageUpscalerDownload(model, destFile)
                        } else {
                            finalizeNonVlmDownload(model, destFile)
                        }
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

    private fun finalizeMmprojDownload(model: HuggingFaceModel, destFile: java.io.File) {
        _downloadIds.value = _downloadIds.value - model.id
        _downloadStates.value = _downloadStates.value - model.id
        _installedMmprojIds.value = _installedMmprojIds.value + model.id
    }

    private fun downloadVlmProjectorThenFinalize(
        model: HuggingFaceModel,
        baseFile: java.io.File,
        mmprojFile: java.io.File,
    ) {
        if (mmprojFile.exists() && mmprojFile.length() > 0L) {
            finalizeVlmDownload(model, baseFile, mmprojFile)
            return
        }

        val hxdId = HxdManager.enqueue(context, model.mmprojFileUri, mmprojFile.absolutePath)
        downloadCoordinator.registerLabel(hxdId, "${model.name} projector", "mmproj")
        _downloadIds.value = _downloadIds.value + (model.id to hxdId)
        _downloadStates.value = _downloadStates.value + (model.id to
            HxdState(hxdId, model.mmprojFileUri, mmprojFile.absolutePath, 0L, -1L, 0L, HxdStatus.QUEUED))

        viewModelScope.launch(Dispatchers.IO) {
            HxdManager.observe(hxdId).collect { state ->
                if (state == null) return@collect
                _downloadStates.value = _downloadStates.value + (model.id to state)
                when (state.status) {
                    HxdStatus.COMPLETED -> finalizeVlmDownload(model, baseFile, mmprojFile)
                    HxdStatus.FAILED -> {
                        _error.value = "Projector download failed for ${model.name}"
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

    fun refreshInstalledMmproj() {
        val catalog = _filteredModels.value
        val installed = catalog.asSequence()
            .filter { it.isMmproj && it.repoPath.isNotBlank() }
            .filter { modelRepo.vlmMmprojFile(it.repoPath, it.fileName).exists() }
            .map { it.id }
            .toSet()
        _installedMmprojIds.value = installed
    }

    private fun finalizeVoiceDownload(model: HuggingFaceModel, archive: java.io.File) {
        viewModelScope.launch(Dispatchers.IO) {
            _extractingIds.value = _extractingIds.value + model.id
            installProgress.extractStarted(model.id)
            try {
                if (!archive.exists()) {
                    _error.value = "Downloaded archive missing for ${model.name}"
                    return@launch
                }
                val kind = if (model.modelType == "tts") VoiceKind.TTS else VoiceKind.STT
                val parent = modelRepo.voiceDir(if (kind == VoiceKind.TTS) "tts" else "stt")
                val result = VoiceArchive.extractAndBuildConfig(archive, parent, kind) { entryName ->
                    _extractingFile.update { it + (model.id to entryName) }
                }
                when (result) {
                    is VoiceArchive.ExtractResult.Success -> {
                        archive.delete()
                        val provider = if (kind == VoiceKind.TTS) ProviderType.TTS else ProviderType.STT
                        val folderSize = result.folder.walkTopDown()
                            .filter { it.isFile }
                            .sumOf { it.length() }
                        modelRepo.insert(
                            ModelInfo(
                                id = model.id, name = model.name,
                                path = result.folder.absolutePath, pathType = PathType.FILE,
                                providerType = provider, fileSize = folderSize,
                            ),
                            ModelConfig(
                                id = model.id, modelId = model.id,
                                loadingParamsJson = result.configJson,
                                inferenceParamsJson = "{}",
                            ),
                        )
                        refreshServerCatalog()
                        if (kind == VoiceKind.TTS && prefs.activeTtsModelId.isBlank()) {
                            prefs.activeTtsModelId = model.id
                        }
                        if (kind == VoiceKind.STT && prefs.activeSttModelId.isBlank()) {
                            prefs.activeSttModelId = model.id
                        }
                    }
                    is VoiceArchive.ExtractResult.Failure -> {
                        android.util.Log.e("ModelStoreVM", "Voice extract failed: ${result.reason}")
                        _error.value = "Extraction failed: ${result.reason}"
                    }
                }
            } catch (t: Throwable) {
                android.util.Log.e("ModelStoreVM", "finalizeVoiceDownload threw", t)
                _error.value = "Extraction error: ${t.message}"
            } finally {
                _extractingIds.value = _extractingIds.value - model.id
                _extractingFile.value = _extractingFile.value - model.id
                _downloadIds.value = _downloadIds.value - model.id
                _downloadStates.value = _downloadStates.value - model.id
                installProgress.extractFinished(model.id)
            }
        }
    }

    private fun finalizeImageGenDownload(model: HuggingFaceModel, archive: java.io.File) {
        viewModelScope.launch(Dispatchers.IO) {
            _extractingIds.value = _extractingIds.value + model.id
            installProgress.extractStarted(model.id)
            try {
                if (!archive.exists()) {
                    _error.value = "Downloaded archive missing for ${model.name}"
                    return@launch
                }
                val targetDir = modelRepo.imageModelDir(model.id)
                targetDir.listFiles()?.forEach { it.deleteRecursively() }
                val ok = unzipInto(archive, targetDir) { entryName ->
                    _extractingFile.update { it + (model.id to entryName) }
                }
                if (!ok) {
                    _error.value = "Extraction failed for ${model.name}"
                    targetDir.deleteRecursively()
                    return@launch
                }
                archive.delete()
                // Walk down through any chain of single-subdir wrappers (xororz/sd-qnn ZIPs
                // wrap their contents in `output_<size>/qnn_models_<bucket>/`) until we find
                // the dir that actually contains unet.bin / clip*.mnn / tokenizer.json.
                val effectiveDir = liftToModelDir(targetDir)
                val folderSize = effectiveDir.walkTopDown()
                    .filter { it.isFile }
                    .sumOf { it.length() }
                modelRepo.insert(
                    ModelInfo(
                        id = model.id, name = model.name,
                        path = effectiveDir.absolutePath, pathType = PathType.FILE,
                        providerType = ProviderType.IMAGE_GEN,
                        fileSize = folderSize,
                    ),
                )
                refreshServerCatalog()
            } catch (t: Throwable) {
                android.util.Log.e("ModelStoreVM", "finalizeImageGenDownload threw", t)
                _error.value = "Extraction error: ${t.message}"
            } finally {
                _extractingIds.value = _extractingIds.value - model.id
                _extractingFile.value = _extractingFile.value - model.id
                _downloadIds.value = _downloadIds.value - model.id
                _downloadStates.value = _downloadStates.value - model.id
                installProgress.extractFinished(model.id)
            }
        }
    }

    private fun finalizeImageUpscalerDownload(model: HuggingFaceModel, destFile: java.io.File) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                if (!destFile.exists()) {
                    _error.value = "Downloaded file missing for ${model.name}"
                    return@launch
                }
                modelRepo.insert(
                    ModelInfo(
                        id = model.id, name = model.name,
                        path = destFile.absolutePath, pathType = PathType.FILE,
                        providerType = ProviderType.IMAGE_UPSCALER,
                        fileSize = if (model.sizeBytes > 0) model.sizeBytes else destFile.length(),
                    ),
                )
                refreshServerCatalog()
            } finally {
                _downloadIds.value = _downloadIds.value - model.id
                _downloadStates.value = _downloadStates.value - model.id
            }
        }
    }

    private fun liftToModelDir(root: java.io.File): java.io.File {
        val signals = setOf(
            "unet.bin", "unet.mnn", "clip.mnn", "clip_v2.mnn",
            "vae_decoder.bin", "vae_decoder.mnn", "tokenizer.json",
        )
        var cur = root
        repeat(6) {
            val files = cur.listFiles()?.toList().orEmpty()
            val hasModelFile = files.any { it.isFile && it.name in signals }
            if (hasModelFile) return cur
            val onlyDir = files.singleOrNull { it.isDirectory && !it.name.startsWith(".") }
                ?: return cur
            cur = onlyDir
        }
        return cur
    }

    private fun unzipInto(
        archive: java.io.File,
        target: java.io.File,
        onEntry: (String) -> Unit,
    ): Boolean {
        val targetCanonical = target.canonicalPath + java.io.File.separator
        val workers = minOf(4, Runtime.getRuntime().availableProcessors().coerceAtLeast(1))
        val bufSize = 256 * 1024

        return java.util.zip.ZipFile(archive).use { zip ->
            val fileEntries = ArrayList<java.util.zip.ZipEntry>()
            val it = zip.entries()
            while (it.hasMoreElements()) {
                val e = it.nextElement()
                val raw = e.name
                if (raw.contains("..")) continue
                val outFile = java.io.File(target, raw)
                val canonical = outFile.canonicalPath
                if (!canonical.startsWith(targetCanonical) && canonical != target.canonicalPath) {
                    return@use false
                }
                if (e.isDirectory) {
                    outFile.mkdirs()
                } else {
                    outFile.parentFile?.mkdirs()
                    fileEntries.add(e)
                }
            }

            val executor = java.util.concurrent.Executors.newFixedThreadPool(workers)
            try {
                val futures = fileEntries.map { entry ->
                    executor.submit {
                        val outFile = java.io.File(target, entry.name)
                        onEntry(entry.name.substringAfterLast('/'))
                        zip.getInputStream(entry).use { input ->
                            java.io.BufferedOutputStream(
                                java.io.FileOutputStream(outFile),
                                bufSize,
                            ).use { out -> input.copyTo(out, bufSize) }
                        }
                    }
                }
                for (f in futures) {
                    try {
                        f.get()
                    } catch (ee: java.util.concurrent.ExecutionException) {
                        throw ee.cause ?: ee
                    }
                }
            } finally {
                executor.shutdown()
            }
            true
        }
    }

    private fun finalizeNonVlmDownload(model: HuggingFaceModel, destFile: java.io.File) {
        val provider = when (model.modelType) {
            "tts" -> ProviderType.TTS
            "stt" -> ProviderType.STT
            "embedding" -> ProviderType.EMBEDDING
            "tool_search" -> ProviderType.TOOL_SEARCH
            else -> ProviderType.GGUF
        }
        modelRepo.insert(ModelInfo(
            id = model.id, name = model.name,
            path = destFile.absolutePath, pathType = PathType.FILE,
            providerType = provider,
            fileSize = if (model.sizeBytes > 0) model.sizeBytes else destFile.length(),
        ))
        refreshServerCatalog()
        if (provider == ProviderType.GGUF &&
            modelRepo.models.value.none { it.providerType == ProviderType.GGUF && it.isActive && it.id != model.id }
        ) {
            modelRepo.setActive(model.id)
        }
        _downloadIds.value = _downloadIds.value - model.id
        _downloadStates.value = _downloadStates.value - model.id
    }

    private fun finalizeVlmDownload(
        model: HuggingFaceModel,
        baseFile: java.io.File,
        mmprojFile: java.io.File,
    ) {
        if (!mmprojFile.exists() || mmprojFile.length() <= 0L) {
            _error.value = "Vision projector missing for ${model.name}"
            _downloadIds.value = _downloadIds.value - model.id
            _downloadStates.value = _downloadStates.value - model.id
            return
        }
        modelRepo.insert(ModelInfo(
            id = model.id, name = model.name,
            path = baseFile.absolutePath, pathType = PathType.FILE,
            providerType = ProviderType.VISION_CHAT,
            fileSize = if (model.sizeBytes > 0) model.sizeBytes else baseFile.length(),
        ))
        refreshServerCatalog()
        _downloadIds.value = _downloadIds.value - model.id
        _downloadStates.value = _downloadStates.value - model.id
        _installedMmprojIds.value = _installedMmprojIds.value + model.id
    }

    private fun downloadTypeOf(model: HuggingFaceModel): String = when {
        model.isMmproj -> "mmproj"
        model.isVlm -> "vlm"
        model.modelType.isNotBlank() -> model.modelType
        else -> "gguf"
    }

    fun cancelDownload(modelId: String) {
        _downloadIds.value[modelId]?.let { HxdManager.cancel(it) }
        _downloadIds.value = _downloadIds.value - modelId
        _downloadStates.value = _downloadStates.value - modelId
    }


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
        if (serverController.isBusy) return
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
        if (serverController.isBusy) return
        viewModelScope.launch { InferenceClient.unloadModel() }
    }

    fun importLocalModel(
        uri: Uri,
        fileName: String,
        fileSize: Long,
        providerType: ProviderType = ProviderType.GGUF,
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                val modelId = checksumId(uri.toString(), fileName, fileSize)
                val localFile = when (providerType) {
                    ProviderType.IMAGE_UPSCALER -> copyLocalImport(uri, modelRepo.imageUpscalerFile(modelId, fileName))
                    ProviderType.IMAGE_GEN -> copyLocalImport(uri, java.io.File(modelRepo.imageModelDir(modelId), fileName.substringAfterLast('/')))
                    else -> null
                }
                modelRepo.insert(ModelInfo(
                    id = modelId,
                    name = fileName.removeSuffix(".gguf"),
                    path = localFile?.absolutePath ?: uri.toString(),
                    pathType = if (localFile != null) PathType.FILE else PathType.CONTENT_URI,
                    providerType = providerType,
                    fileSize = localFile?.length() ?: fileSize,
                ))
                refreshServerCatalog()
            }.onFailure { throwable ->
                _error.value = "Import failed: ${throwable.message ?: throwable.javaClass.simpleName}"
            }
        }
    }

    private fun copyLocalImport(uri: Uri, dest: java.io.File): java.io.File {
        dest.parentFile?.mkdirs()
        context.contentResolver.openInputStream(uri)?.use { input ->
            dest.outputStream().use { output -> input.copyTo(output) }
        } ?: error("Unable to open local model file")
        return dest
    }

    fun updateModelProviderType(modelId: String, providerType: ProviderType) {
        viewModelScope.launch(Dispatchers.IO) {
            val current = modelRepo.getModelById(modelId) ?: return@launch
            if (current.providerType == providerType) return@launch
            updateModelProviderTypeNow(current, providerType)
        }
    }

    private suspend fun updateModelProviderTypeNow(current: ModelInfo, providerType: ProviderType) {
        val modelId = current.id
        if (current.isActive && providerType != ProviderType.GGUF) {
            InferenceClient.unloadModel()
        }
        if (current.providerType == ProviderType.EMBEDDING && providerType != ProviderType.EMBEDDING &&
            ragManager.defaultEmbeddingModelId.value == modelId
        ) {
            ragManager.setDefaultEmbeddingModelId(null)
        }
        if (current.providerType == ProviderType.TTS && providerType != ProviderType.TTS &&
            prefs.activeTtsModelId == modelId
        ) {
            prefs.activeTtsModelId = ""
        }
        if (current.providerType == ProviderType.STT && providerType != ProviderType.STT &&
            prefs.activeSttModelId == modelId
        ) {
            prefs.activeSttModelId = ""
        }
        if (current.providerType == ProviderType.GGUF && providerType != ProviderType.GGUF &&
            serverController.selectedModelId() == modelId
        ) {
            serverController.setSelectedModelId("")
        }

        val migrated = migrateLocalUriForImageModel(current, providerType)
        if (migrated != null) {
            modelRepo.insert(migrated)
        } else {
            modelRepo.updateProviderType(modelId, providerType)
        }
        refreshServerCatalog()
    }

    private fun migrateLocalUriForImageModel(
        current: ModelInfo,
        providerType: ProviderType,
    ): ModelInfo? {
        if (current.pathType != PathType.CONTENT_URI) return null
        val uri = Uri.parse(current.path)
        val fileName = current.name.substringAfterLast('/').ifBlank { current.id.substringAfterLast('/') }
        val localFile = when (providerType) {
            ProviderType.IMAGE_UPSCALER -> copyLocalImport(uri, modelRepo.imageUpscalerFile(current.id, fileName))
            ProviderType.IMAGE_GEN -> copyLocalImport(uri, java.io.File(modelRepo.imageModelDir(current.id), fileName))
            else -> return null
        }
        return current.copy(
            path = localFile.absolutePath,
            pathType = PathType.FILE,
            providerType = providerType,
            fileSize = localFile.length(),
            isActive = false,
        )
    }

    suspend fun getModelConfig(modelId: String): ModelConfig? = modelRepo.getConfig(modelId)

    fun saveModelConfig(config: ModelConfig) {
        modelRepo.updateConfig(config)
        val info = installedModels.value.firstOrNull { it.id == config.modelId } ?: return
        if (info.providerType != ProviderType.GGUF) return
        if (serverController.isBusy) return
        viewModelScope.launch { modelSession.load(info) }
    }

    fun saveModelConfig(config: ModelConfig, providerType: ProviderType) {
        viewModelScope.launch(Dispatchers.IO) {
            val current = modelRepo.getModelById(config.modelId)
            if (current != null && current.providerType != providerType) {
                updateModelProviderTypeNow(current, providerType)
            }
            modelRepo.updateConfig(config)
            refreshServerCatalog()
            val latest = modelRepo.getModelById(config.modelId)
            if (latest?.providerType == ProviderType.GGUF && !serverController.isBusy) {
                withContext(Dispatchers.Main) { modelSession.load(latest) }
            }
        }
    }


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
