package com.dark.tool_neuron.viewmodel

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.dark.tool_neuron.data.AppSettingsDataStore
import com.dark.tool_neuron.di.AppContainer
import com.dark.tool_neuron.models.enums.ProviderType
import com.dark.tool_neuron.models.table_schema.Model
import com.dark.tool_neuron.network.NetworkUtils
import com.dark.tool_neuron.plugins.PluginManager
import com.dark.tool_neuron.service.ModelDownloadService
import com.dark.tool_neuron.tts.TTSDataStore
import com.dark.tool_neuron.tts.TTSManager
import com.dark.tool_neuron.tts.TTSSettings
import com.dark.tool_neuron.worker.SystemBackupManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val appSettingsDataStore = AppSettingsDataStore(application)
    private val ttsDataStore = TTSDataStore(application)

    private val modelRepository = AppContainer.getModelRepository()

    init {
        // Sync bypass setting with PluginManager on startup
        viewModelScope.launch {
            appSettingsDataStore.toolCallingBypassEnabled.collect { enabled ->
                PluginManager.setToolCallingBypassEnabled(enabled)
            }
        }
    }

    // Installed models
    val installedModels: Flow<List<Model>> = modelRepository.getAllModels()

    // TTS install state
    val hasTtsModel: StateFlow<Boolean> = modelRepository.getAllModels()
        .map { models -> models.any { it.providerType == ProviderType.TTS } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    // Tool calling model install state — any GGUF model can support tool calling
    // (actual compatibility is checked at load time via native chat-template detection)
    val hasToolCallingModel: StateFlow<Boolean> = modelRepository.getAllModels()
        .map { models ->
            models.any { model -> model.providerType == ProviderType.GGUF }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val ttsDownloadStates: StateFlow<Map<String, ModelDownloadService.DownloadState>> =
        ModelDownloadService.downloadStates

    // Tool calling model download state
    val toolCallingModelDownloadState: StateFlow<Map<String, ModelDownloadService.DownloadState>> =
        ModelDownloadService.downloadStates

    // App settings
    val streamingEnabled: StateFlow<Boolean> = appSettingsDataStore.streamingEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val chatMemoryEnabled: StateFlow<Boolean> = appSettingsDataStore.chatMemoryEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val toolCallingEnabled: StateFlow<Boolean> = appSettingsDataStore.toolCallingEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val toolCallingBypassEnabled: StateFlow<Boolean> = appSettingsDataStore.toolCallingBypassEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val imageBlurEnabled: StateFlow<Boolean> = appSettingsDataStore.imageBlurEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val loadTTSOnStart: StateFlow<Boolean> = appSettingsDataStore.loadTTSOnStart
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val codeHighlightEnabled: StateFlow<Boolean> = appSettingsDataStore.codeHighlightEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val aiMemoryEnabled: StateFlow<Boolean> = appSettingsDataStore.aiMemoryEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    // Remote API Settings
    val remoteApiEnabled: StateFlow<Boolean> = appSettingsDataStore.remoteApiEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val remoteApiPort: StateFlow<Int> = appSettingsDataStore.remoteApiPort
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 11434)

    val remoteApiNsdEnabled: StateFlow<Boolean> = appSettingsDataStore.remoteApiNsdEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val localIpAddress: String? = NetworkUtils.getLocalIpAddress()

    // TTS settings
    val ttsSettings: StateFlow<TTSSettings> = ttsDataStore.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), TTSSettings())

    val ttsModelLoaded: StateFlow<Boolean> = TTSManager.isModelLoaded
    val ttsAvailableVoices: StateFlow<List<String>> = TTSManager.availableVoices

    // App info
    val appVersion: String = try {
        val pInfo = application.packageManager.getPackageInfo(application.packageName, 0)
        pInfo.versionName ?: "2.1.0"
    } catch (_: Exception) {
        "2.1.0"
    }

    // App settings updaters
    fun setStreamingEnabled(enabled: Boolean) {
        viewModelScope.launch { appSettingsDataStore.updateStreamingEnabled(enabled) }
    }

    fun setChatMemoryEnabled(enabled: Boolean) {
        viewModelScope.launch { appSettingsDataStore.updateChatMemoryEnabled(enabled) }
    }

    fun setToolCallingEnabled(enabled: Boolean) {
        viewModelScope.launch { appSettingsDataStore.updateToolCallingEnabled(enabled) }
    }

    fun setToolCallingBypassEnabled(enabled: Boolean) {
        viewModelScope.launch {
            appSettingsDataStore.updateToolCallingBypassEnabled(enabled)
            // Sync with PluginManager
            PluginManager.setToolCallingBypassEnabled(enabled)
        }
    }

    fun setImageBlurEnabled(enabled: Boolean) {
        viewModelScope.launch { appSettingsDataStore.updateImageBlurEnabled(enabled) }
    }

    fun setLoadTTSOnStart(enabled: Boolean) {
        viewModelScope.launch { appSettingsDataStore.updateLoadTTSOnStart(enabled) }
    }

    fun setCodeHighlightEnabled(enabled: Boolean) {
        viewModelScope.launch { appSettingsDataStore.updateCodeHighlightEnabled(enabled) }
    }

    fun setAiMemoryEnabled(enabled: Boolean) {
        viewModelScope.launch { appSettingsDataStore.updateAiMemoryEnabled(enabled) }
    }

    // Remote API Updaters
    fun setRemoteApiEnabled(enabled: Boolean) {
        viewModelScope.launch { appSettingsDataStore.updateRemoteApiEnabled(enabled) }
    }

    fun setRemoteApiPort(port: Int) {
        viewModelScope.launch { appSettingsDataStore.updateRemoteApiPort(port) }
    }

    fun setRemoteApiNsdEnabled(enabled: Boolean) {
        viewModelScope.launch { appSettingsDataStore.updateRemoteApiNsdEnabled(enabled) }
    }

    // TTS settings updaters
    fun updateVoice(voice: String) {
        viewModelScope.launch { ttsDataStore.updateVoice(voice) }
    }

    fun updateSpeed(speed: Float) {
        viewModelScope.launch { ttsDataStore.updateSpeed(speed) }
    }

    fun updateSteps(steps: Int) {
        viewModelScope.launch { ttsDataStore.updateSteps(steps) }
    }

    fun updateLanguage(language: String) {
        viewModelScope.launch { ttsDataStore.updateLanguage(language) }
    }

    fun updateAutoSpeak(enabled: Boolean) {
        viewModelScope.launch { ttsDataStore.updateAutoSpeak(enabled) }
    }

    fun updateUseNNAPI(enabled: Boolean) {
        viewModelScope.launch { ttsDataStore.updateUseNNAPI(enabled) }
    }

    // Downloads
    companion object {
        private const val TTS_MODEL_ID = "supertonic-v2-tts"
    }

    fun downloadTts() {
        val context = getApplication<Application>()
        val intent = Intent(context, ModelDownloadService::class.java).apply {
            action = ModelDownloadService.ACTION_START_DOWNLOAD
            putExtra(ModelDownloadService.EXTRA_MODEL_ID, TTS_MODEL_ID)
            putExtra(ModelDownloadService.EXTRA_MODEL_NAME, "Supertonic v2 TTS")
            putExtra(ModelDownloadService.EXTRA_FILE_URL, "https://huggingface.co/Supertone/supertonic-2/resolve/main")
            putExtra(ModelDownloadService.EXTRA_IS_ZIP, false)
            putExtra(ModelDownloadService.EXTRA_MODEL_TYPE, "TTS")
            putExtra(ModelDownloadService.EXTRA_RUN_ON_CPU, true)
            putExtra(ModelDownloadService.EXTRA_TEXT_EMBEDDING_SIZE, 0)
        }
        context.startForegroundService(intent)
    }

    fun downloadToolCallingModel() {
        val context = getApplication<Application>()
        val model = PluginManager.TOOL_CALLING_MODEL
        val intent = Intent(context, ModelDownloadService::class.java).apply {
            action = ModelDownloadService.ACTION_START_DOWNLOAD
            putExtra(ModelDownloadService.EXTRA_MODEL_ID, model.id)
            putExtra(ModelDownloadService.EXTRA_MODEL_NAME, model.name)
            putExtra(ModelDownloadService.EXTRA_FILE_URL, "https://huggingface.co/${model.fileUri}")
            putExtra(ModelDownloadService.EXTRA_IS_ZIP, model.isZip)
            putExtra(ModelDownloadService.EXTRA_MODEL_TYPE, "GGUF")
            putExtra(ModelDownloadService.EXTRA_RUN_ON_CPU, model.runOnCpu)
            putExtra(ModelDownloadService.EXTRA_TEXT_EMBEDDING_SIZE, model.textEmbeddingSize)
        }
        context.startForegroundService(intent)
    }

    fun loadTtsAfterDownload() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                val modelDir = TTSManager.getModelDirectory() ?: return@withContext
                TTSManager.loadModel(modelDir)
            }
        }
    }

    // ==================== Backup / Restore / Delete ====================

    private val _backupProgress = MutableStateFlow<SystemBackupManager.BackupProgress?>(null)
    val backupProgress: StateFlow<SystemBackupManager.BackupProgress?> = _backupProgress

    private val _backupOptions = MutableStateFlow(SystemBackupManager.BackupOptions())
    val backupOptions: StateFlow<SystemBackupManager.BackupOptions> = _backupOptions

    private val _backupSizeEstimate = MutableStateFlow<SystemBackupManager.BackupSizeEstimate?>(null)
    val backupSizeEstimate: StateFlow<SystemBackupManager.BackupSizeEstimate?> = _backupSizeEstimate

    fun updateBackupOptions(options: SystemBackupManager.BackupOptions) {
        _backupOptions.value = options
        estimateBackupSize(options)
    }

    fun estimateBackupSize(options: SystemBackupManager.BackupOptions = _backupOptions.value) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val manager = SystemBackupManager(getApplication())
                _backupSizeEstimate.value = manager.estimateBackupSize(options)
            } catch (e: Exception) {
                _backupSizeEstimate.value = null
            }
        }
    }

    fun createBackup(uri: Uri, password: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val manager = SystemBackupManager(getApplication())
            manager.createBackup(uri, password, _backupOptions.value) { progress ->
                _backupProgress.value = progress
            }
        }
    }

    fun restoreBackup(uri: Uri, password: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val manager = SystemBackupManager(getApplication())
            manager.restoreBackup(uri, password) { progress ->
                _backupProgress.value = progress
            }
        }
    }

    fun deleteAllData() {
        viewModelScope.launch(Dispatchers.IO) {
            val manager = SystemBackupManager(getApplication())
            manager.deleteAllData { progress ->
                _backupProgress.value = progress
            }
        }
    }

    fun clearBackupProgress() {
        _backupProgress.value = null
    }
}