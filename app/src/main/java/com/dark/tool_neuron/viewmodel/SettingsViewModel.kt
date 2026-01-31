package com.dark.tool_neuron.viewmodel

import android.app.Application
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.dark.tool_neuron.data.AppSettingsDataStore
import com.dark.tool_neuron.di.AppContainer
import com.dark.tool_neuron.models.enums.ProviderType
import com.dark.tool_neuron.models.table_schema.Model
import com.dark.tool_neuron.service.ModelDownloadService
import com.dark.tool_neuron.tts.TTSDataStore
import com.dark.tool_neuron.tts.TTSManager
import com.dark.tool_neuron.tts.TTSSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
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

    // Installed models
    val installedModels: Flow<List<Model>> = modelRepository.getAllModels()

    // TTS install state
    val hasTtsModel: StateFlow<Boolean> = modelRepository.getAllModels()
        .map { models -> models.any { it.providerType == ProviderType.TTS } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val ttsDownloadStates: StateFlow<Map<String, ModelDownloadService.DownloadState>> =
        ModelDownloadService.downloadStates

    // App settings
    val streamingEnabled: StateFlow<Boolean> = appSettingsDataStore.streamingEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val chatMemoryEnabled: StateFlow<Boolean> = appSettingsDataStore.chatMemoryEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val toolCallingEnabled: StateFlow<Boolean> = appSettingsDataStore.toolCallingEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val imageBlurEnabled: StateFlow<Boolean> = appSettingsDataStore.imageBlurEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val loadTTSOnStart: StateFlow<Boolean> = appSettingsDataStore.loadTTSOnStart
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    // TTS settings
    val ttsSettings: StateFlow<TTSSettings> = ttsDataStore.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), TTSSettings())

    val ttsModelLoaded: StateFlow<Boolean> = TTSManager.isModelLoaded
    val ttsAvailableVoices: StateFlow<List<String>> = TTSManager.availableVoices

    // App info
    val appVersion: String = try {
        val pInfo = application.packageManager.getPackageInfo(application.packageName, 0)
        pInfo.versionName ?: "1.0"
    } catch (_: Exception) {
        "1.0"
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

    fun setImageBlurEnabled(enabled: Boolean) {
        viewModelScope.launch { appSettingsDataStore.updateImageBlurEnabled(enabled) }
    }

    fun setLoadTTSOnStart(enabled: Boolean) {
        viewModelScope.launch { appSettingsDataStore.updateLoadTTSOnStart(enabled) }
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

    // TTS download
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

    fun loadTtsAfterDownload() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                val modelDir = TTSManager.getModelDirectory() ?: return@withContext
                TTSManager.loadModel(modelDir)
            }
        }
    }
}
