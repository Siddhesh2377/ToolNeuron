package com.dark.tool_neuron.viewmodel

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.dark.tool_neuron.data.AppSettingsDataStore
import com.dark.tool_neuron.data.SetupDataStore
import com.dark.tool_neuron.data.VaultManager
import com.dark.tool_neuron.di.AppContainer
import com.dark.tool_neuron.global.HardwareScanner
import com.dark.tool_neuron.models.data.HuggingFaceModel
import com.dark.tool_neuron.models.data.ModelType
import com.dark.tool_neuron.models.enums.ProviderType
import com.dark.tool_neuron.global.PerformanceMode
import com.dark.tool_neuron.repo.ModelStoreRepository
import com.dark.tool_neuron.service.ModelDownloadService
import com.dark.tool_neuron.worker.SystemBackupManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

enum class SetupOption {
    TEXT,
    TEXT_TTS,
    IMAGE_GEN,
    POWER_MODE
}

class SetupViewModel(application: Application) : AndroidViewModel(application) {

    private val setupDataStore = SetupDataStore(application)
    private val appSettingsDataStore = AppSettingsDataStore(application)
    private val modelStoreRepository = ModelStoreRepository(application)

    // ModelRepository is deferred until vault is ready
    private val modelRepository get() = AppContainer.getModelRepository()

    val downloadStates = ModelDownloadService.downloadStates

    private val _selectedOption = MutableStateFlow<SetupOption?>(null)
    val selectedOption: StateFlow<SetupOption?> = _selectedOption

    private val _setupComplete = MutableStateFlow(false)
    val setupComplete: StateFlow<Boolean> = _setupComplete

    private val _downloadError = MutableStateFlow<String?>(null)
    val downloadError: StateFlow<String?> = _downloadError

    private val _primaryModelId = MutableStateFlow<String?>(null)
    val primaryModelId: StateFlow<String?> = _primaryModelId

    private val _showPerformancePicker = MutableStateFlow(false)
    val showPerformancePicker: StateFlow<Boolean> = _showPerformancePicker

    private val _selectedPerformanceMode = MutableStateFlow(PerformanceMode.BALANCED)
    val selectedPerformanceMode: StateFlow<PerformanceMode> = _selectedPerformanceMode

    // ==================== Setup Model Definitions ====================

    private val textModel = HuggingFaceModel(
        id = "lfm2-350m",
        name = "LFM2 350M",
        description = "Compact text generation model by LiquidAI",
        fileUri = "LiquidAI/LFM2-350M-GGUF/resolve/main/LFM2-350M-Q4_K_M-hip-optimized.gguf",
        approximateSize = "200 MB",
        modelType = ModelType.GGUF,
        isZip = false,
        tags = listOf("GGUF", "Q4_K_M"),
        requiresNPU = false,
        repositoryUrl = "LiquidAI/LFM2-350M-GGUF"
    )

    private val ttsModel = HuggingFaceModel(
        id = "supertonic-v2-tts",
        name = "Supertonic v2 TTS",
        description = "Multilingual text-to-speech engine",
        fileUri = "Supertone/supertonic-2/resolve/main",
        approximateSize = "263 MB",
        modelType = ModelType.TTS,
        isZip = false,
        runOnCpu = true,
        textEmbeddingSize = 0,
        tags = listOf("TTS"),
        requiresNPU = false,
        repositoryUrl = "Supertone/supertonic-2"
    )

    private fun getImageModel(): HuggingFaceModel {
        val isQualcomm = modelStoreRepository.isQualcommDevice()
        return HuggingFaceModel(
            id = "absolutereality-sd",
            name = "AbsoluteReality",
            description = "Realistic image generation",
            fileUri = "xororz/sd-qnn/resolve/main/AbsoluteReality_qnn2.28_min.zip",
            approximateSize = "1.1 GB",
            modelType = ModelType.SD,
            isZip = true,
            runOnCpu = !isQualcomm,
            textEmbeddingSize = 768,
            tags = if (isQualcomm) listOf("NPU", "Realistic") else listOf("CPU", "Realistic"),
            requiresNPU = false,
            repositoryUrl = "xororz/sd-qnn"
        )
    }

    companion object {
        private const val TAG = "SetupVM"
        private const val PROFILE_MAX_AGE_MS = 30L * 24 * 60 * 60 * 1000 // 30 days
    }

    // ==================== Initialization ====================

    init {
        // Auto-init plaintext vault if not ready
        if (!VaultManager.isReady.value) {
            viewModelScope.launch(Dispatchers.IO) {
                try {
                    VaultManager.initPlaintext(getApplication())
                    AppContainer.ensureVaultInitialized()
                } catch (e: Exception) {
                    Log.e(TAG, "Auto vault init failed", e)
                }
            }
        }

        // Scan hardware if no profile exists or profile is stale (>30 days)
        viewModelScope.launch(Dispatchers.IO) {
            scanHardwareIfNeeded()
        }

        // Resume active setup downloads if any
        if (VaultManager.isReady.value) {
            resumeActiveDownloads()
            watchModelInstallations()
        }

        // Watch for vault readiness to start model monitoring
        viewModelScope.launch {
            VaultManager.isReady.collect { ready ->
                if (ready) {
                    resumeActiveDownloads()
                    watchModelInstallations()
                }
            }
        }

        // Watch for download errors
        viewModelScope.launch {
            downloadStates.collect { states ->
                val primaryId = _primaryModelId.value ?: return@collect
                val state = states[primaryId]
                if (state is ModelDownloadService.DownloadState.Error) {
                    _downloadError.value = state.message
                    _selectedOption.value = null
                    _primaryModelId.value = null
                }
            }
        }
    }

    private fun watchModelInstallations() {
        viewModelScope.launch {
            modelRepository.getAllModels().collect { models ->
                if (_selectedOption.value != null && _selectedOption.value != SetupOption.POWER_MODE && !_showPerformancePicker.value && !_setupComplete.value) {
                    val hasTextOrImage = models.any {
                        it.providerType == ProviderType.GGUF || it.providerType == ProviderType.DIFFUSION
                    }
                    if (hasTextOrImage) {
                        _showPerformancePicker.value = true
                    }
                }
            }
        }
    }

    private fun resumeActiveDownloads() {
        val currentStates = downloadStates.value
        val imageModelId = getImageModel().id

        when {
            currentStates.containsKey(textModel.id) && currentStates.containsKey(ttsModel.id) -> {
                _selectedOption.value = SetupOption.TEXT_TTS
                _primaryModelId.value = textModel.id
            }
            currentStates.containsKey(textModel.id) -> {
                _selectedOption.value = SetupOption.TEXT
                _primaryModelId.value = textModel.id
            }
            currentStates.containsKey(imageModelId) -> {
                _selectedOption.value = SetupOption.IMAGE_GEN
                _primaryModelId.value = imageModelId
            }
        }
    }

    // ==================== Actions ====================

    fun selectOption(option: SetupOption) {
        if (_selectedOption.value != null) return

        _selectedOption.value = option
        _downloadError.value = null

        when (option) {
            SetupOption.TEXT -> {
                _primaryModelId.value = textModel.id
                downloadModel(textModel)
            }
            SetupOption.TEXT_TTS -> {
                _primaryModelId.value = textModel.id
                downloadModel(textModel)
                downloadModel(ttsModel)
            }
            SetupOption.IMAGE_GEN -> {
                val imageModel = getImageModel()
                _primaryModelId.value = imageModel.id
                downloadModel(imageModel)
            }
            SetupOption.POWER_MODE -> {
                viewModelScope.launch {
                    setupDataStore.skipSetup()
                    _setupComplete.value = true
                }
            }
        }
    }

    fun selectPerformanceMode(mode: PerformanceMode) {
        _selectedPerformanceMode.value = mode
    }

    fun confirmPerformanceMode() {
        viewModelScope.launch {
            appSettingsDataStore.savePerformanceMode(_selectedPerformanceMode.value)
            setupDataStore.completeSetup()
            _setupComplete.value = true
        }
    }

    fun retryDownload() {
        val lastOption = _selectedOption.value
        _selectedOption.value = null
        _downloadError.value = null
        _primaryModelId.value = null
        if (lastOption != null) {
            selectOption(lastOption)
        }
    }

    private fun downloadModel(model: HuggingFaceModel) {
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

        androidx.core.content.ContextCompat.startForegroundService(context, intent)
    }

    // ==================== Restore from Backup ====================

    private val _restoreProgress = MutableStateFlow<SystemBackupManager.BackupProgress?>(null)
    val restoreProgress: StateFlow<SystemBackupManager.BackupProgress?> = _restoreProgress

    fun restoreFromBackup(uri: Uri, password: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val manager = SystemBackupManager(getApplication())
            val success = manager.restoreBackup(uri, password) { progress ->
                _restoreProgress.value = progress
            }
            if (success) {
                setupDataStore.completeSetup()
                _setupComplete.value = true
            }
        }
    }

    // ==================== Hardware Scan ====================

    private val lenientJson = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private suspend fun scanHardwareIfNeeded() {
        try {
            val existingJson = appSettingsDataStore.hardwareProfileJson.firstOrNull()
            val needsScan = if (existingJson.isNullOrBlank()) {
                true
            } else {
                val profile = lenientJson.decodeFromString<com.dark.tool_neuron.global.HardwareProfile>(existingJson)
                System.currentTimeMillis() - profile.scanTimestamp > PROFILE_MAX_AGE_MS
            }
            if (needsScan) {
                val profile = HardwareScanner.scan(getApplication())
                val json = lenientJson.encodeToString(profile)
                appSettingsDataStore.saveHardwareProfile(json)
                val topo = profile.cpuTopology
                val coreInfo = if (topo.scanSucceeded) "${topo.primeCoreCount}P+${topo.performanceCoreCount}P+${topo.efficiencyCoreCount}E" else "${profile.cpuCores}"
                Log.d(TAG, "Hardware profile scanned: ${profile.totalRamMB}MB RAM, $coreInfo cores")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Hardware scan failed", e)
        }
    }

}
