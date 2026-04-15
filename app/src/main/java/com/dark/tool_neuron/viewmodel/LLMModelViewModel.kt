package com.dark.tool_neuron.viewmodel

import android.app.Application
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.dark.tool_neuron.data.AppSettingsDataStore
import com.dark.tool_neuron.data.VaultManager
import com.dark.tool_neuron.di.AppContainer
import com.dark.tool_neuron.models.enums.PathType
import com.dark.tool_neuron.models.enums.ProviderType
import com.dark.tool_neuron.models.table_schema.Model
import com.dark.tool_neuron.models.table_schema.ModelConfig
import com.dark.tool_neuron.state.AppStateManager
import com.dark.tool_neuron.worker.DiffusionConfig
import com.dark.tool_neuron.worker.DiffusionInferenceParams
import com.dark.tool_neuron.worker.LlmModelWorker
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import androidx.core.net.toUri
import com.dark.tool_neuron.global.AppPaths
import java.io.File

@HiltViewModel
class LLMModelViewModel @Inject constructor(
    application: Application
) : AndroidViewModel(application) {

    private val appSettings = AppSettingsDataStore(application)

    // Deferred until vault is ready
    private val repository get() = AppContainer.getModelRepository()

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val installedModels: Flow<List<Model>> = VaultManager.isReady
        .flatMapLatest { ready ->
            if (ready) repository.getAllModels()
            else flowOf(emptyList())
        }
        .map { models -> models.filter { it.providerType != ProviderType.TTS } }

    private val _currentModelID = MutableStateFlow("")
    val currentModelID: StateFlow<String> = _currentModelID.asStateFlow()

    private val _currentModelType = MutableStateFlow<ProviderType?>(null)

    // Last loaded model — shown once on startup to offer reloading
    private val _lastModelOffer = MutableStateFlow<Model?>(null)
    val lastModelOffer: StateFlow<Model?> = _lastModelOffer.asStateFlow()

    // ── QNN Runtime Setup Gate ──

    private val _pendingDiffusionModel = MutableStateFlow<Model?>(null)

    private val _needsQnnSetup = MutableStateFlow(false)
    val needsQnnSetup: StateFlow<Boolean> = _needsQnnSetup.asStateFlow()

    private fun isQnnRuntimeReady(): Boolean = try {
        val runtimeDir = File(getApplication<Application>().filesDir, "runtime_libs/qnnlibs")
        val marker = File(runtimeDir, ".extracted")
        marker.exists() && (runtimeDir.listFiles()?.size ?: 0) > 1
    } catch (_: Exception) {
        false
    }

    fun onQnnSetupComplete() {
        _needsQnnSetup.value = false
        val pending = _pendingDiffusionModel.value ?: return
        _pendingDiffusionModel.value = null
        loadModel(pending)
    }

    fun onQnnSetupDismissed() {
        _needsQnnSetup.value = false
        _pendingDiffusionModel.value = null
    }

    init {
        viewModelScope.launch(Dispatchers.IO) {
            val savedId = appSettings.lastModelId.first() ?: return@launch
            // Only offer if no model is currently loaded
            if (_currentModelID.value.isNotEmpty()) return@launch
            val model = repository.getModelById(savedId) ?: return@launch
            if (!model.isActive) return@launch
            // Wait for the LLM service to be ready before showing the dialog,
            // so clicking "Load" starts instantly instead of blocking on service bind
            try {
                LlmModelWorker.ensureServiceReady()
            } catch (_: Exception) {
                return@launch
            }

            val askDialog = appSettings.askModelReloadDialog.first()
            if (askDialog) {
                _lastModelOffer.value = model  // show dialog
            } else {
                loadModel(model)  // auto-load silently
            }
        }

        // Watch for performance mode reload requests
        viewModelScope.launch {
            AppStateManager.reloadModelRequested.collect { requested ->
                if (requested && _currentModelID.value.isNotEmpty()) {
                    AppStateManager.clearReloadRequest()
                    reloadCurrentModel()
                }
            }
        }
    }

    fun dismissLastModelOffer() {
        _lastModelOffer.value = null
    }

    fun acceptLastModelOffer() {
        val model = _lastModelOffer.value ?: return
        _lastModelOffer.value = null
        loadModel(model)
    }

    // Model loading states
    val isGgufModelLoaded = LlmModelWorker.isGgufModelLoaded
    val isDiffusionModelLoaded = LlmModelWorker.isDiffusionModelLoaded

    suspend fun getModelConfig(modelId: String): ModelConfig? {
        return repository.getConfigByModelId(modelId)
    }

    fun loadModel(model: Model) {
        // Gate: DIFFUSION models require QNN runtime to be extracted first
        if (model.providerType == ProviderType.DIFFUSION && !isQnnRuntimeReady()) {
            _pendingDiffusionModel.value = model
            _needsQnnSetup.value = true
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            try {
                // Unload any existing model first
                if (_currentModelID.value.isNotEmpty()) {
                    unloadCurrentModel()
                    delay(300)
                }

                AppStateManager.setLoadingModel(model.modelName, 0f)

                val config = getModelConfig(model.id)
                if (config == null) {
                    AppStateManager.setError("Model configuration not found")
                    return@launch
                }

                when (model.providerType) {
                    ProviderType.GGUF -> loadGgufModel(model, config)
                    ProviderType.DIFFUSION -> loadDiffusionModel(model, config)
                    ProviderType.TTS -> { /* TTS models are managed by TTSManager, not LLMService */ }
                }
            } catch (e: Exception) {
                AppStateManager.setError(e.message ?: "Unknown error")
            }
        }
    }

    private suspend fun loadGgufModel(model: Model, config: ModelConfig) {
        val success = if (model.pathType == PathType.CONTENT_URI) {
            // Use FD-based loading for content:// URIs (SAF)
            val uri = model.modelPath.toUri()
            LlmModelWorker.loadGgufModelFromUri(
                context = getApplication(),
                uri = uri,
                modelName = model.modelName,
                modelConfig = config
            )
        } else {
            // Use path-based loading for regular file paths
            LlmModelWorker.loadGgufModel(model, config)
        }

        if (success) {
            LlmModelWorker.setCurrentGgufModelId(model.id)
            _currentModelID.value = model.id
            _currentModelType.value = ProviderType.GGUF
            AppStateManager.setModelLoaded(model.modelName)
            appSettings.saveLastModelId(model.id)

            // Wire optimizations after model load
            try {
                val cacheDir = AppPaths.promptCache(getApplication<android.app.Application>())
                    .also { it.mkdirs() }
                LlmModelWorker.setPromptCacheDirGguf(cacheDir.absolutePath)
                // Speculative decoding disabled — causes KV cache position mismatch on some models
                // TODO: re-enable once native engine position tracking is fixed
                LlmModelWorker.setSpeculativeDecodingGguf(false)
                LlmModelWorker.warmUpGguf()
            } catch (e: Exception) {
                android.util.Log.w("LLMModelVM", "Optimization wiring failed: ${e.message}")
            }

            // Update tool calling model state and sync tools
            val nativeSupports = LlmModelWorker.isToolCallingSupportedGguf()
            com.dark.tool_neuron.plugins.PluginManager.setToolCallingModelLoaded(nativeSupports)
            com.dark.tool_neuron.plugins.PluginManager.syncToolsWithLLM()
        } else {
            AppStateManager.setError("Failed to load GGUF model")
        }
    }

    private suspend fun loadDiffusionModel(model: Model, config: ModelConfig) {
        val diffusionConfig = DiffusionConfig.fromJson(config.modelLoadingParams)

        val success = LlmModelWorker.loadDiffusionModel(
            name = model.modelName,
            modelDir = model.modelPath,
            height = diffusionConfig.height,
            width = diffusionConfig.width,
            textEmbeddingSize = diffusionConfig.textEmbeddingSize,
            runOnCpu = diffusionConfig.runOnCpu,
            useCpuClip = diffusionConfig.useCpuClip,
            isPony = diffusionConfig.isPony,
            httpPort = diffusionConfig.httpPort,
            safetyMode = diffusionConfig.safetyMode
        )

        if (success) {
            LlmModelWorker.setCurrentDiffusionModelId(model.id)
            _currentModelID.value = model.id
            _currentModelType.value = ProviderType.DIFFUSION
            AppStateManager.setModelLoaded(model.modelName)
            appSettings.saveLastModelId(model.id)
        } else {
            AppStateManager.setError("Failed to load Diffusion model")
        }
    }


    private suspend fun unloadCurrentModel() {
        try {
            when (_currentModelType.value) {
                ProviderType.GGUF -> {
                    LlmModelWorker.unloadGgufModel()
                    LlmModelWorker.setCurrentGgufModelId(null)
                }
                ProviderType.DIFFUSION -> {
                    LlmModelWorker.stopDiffusionBackend()
                    LlmModelWorker.setCurrentDiffusionModelId(null)
                }
                else -> {}
            }

            _currentModelID.value = ""
            _currentModelType.value = null
            com.dark.tool_neuron.plugins.PluginManager.setToolCallingModelLoaded(false)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }


    fun unloadModel() {
        viewModelScope.launch {
            try {
                when (_currentModelType.value) {
                    ProviderType.GGUF -> {
                        LlmModelWorker.unloadGgufModel()
                        LlmModelWorker.setCurrentGgufModelId(null)
                    }
                    ProviderType.DIFFUSION -> {
                        LlmModelWorker.stopDiffusionBackend()
                        LlmModelWorker.setCurrentDiffusionModelId(null)
                    }
                    else -> {}
                }

                _currentModelID.value = ""
                _currentModelType.value = null
                AppStateManager.setModelUnloaded()
            } catch (e: Exception) {
                AppStateManager.setError(e.message ?: "Failed to unload model")
            }
        }
    }

    // ── Runtime Reload ──

    fun reloadCurrentModel() {
        val modelId = _currentModelID.value
        if (modelId.isEmpty()) return

        viewModelScope.launch(Dispatchers.IO) {
            val model = repository.getModelById(modelId) ?: return@launch
            loadModel(model)
        }
    }

    /**
     * Delete a model from the database and optionally from disk.
     * If the model is currently loaded, it will be unloaded first.
     */
    fun deleteModel(model: Model, deleteFile: Boolean = true) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // Unload if this is the currently loaded model
                if (_currentModelID.value == model.id) {
                    unloadCurrentModel()
                    delay(300)
                }

                // Clear LAST_MODEL_ID if this model is the saved one
                try {
                    val lastModelId = appSettings.lastModelId.first()
                    if (lastModelId == model.id) {
                        appSettings.saveLastModelId(null)
                    }
                } catch (e: Exception) {
                    Log.e("LLMModelVM", "Failed to clear last model ID: ${e.message}")
                }

                // Delete associated config
                val config = repository.getConfigByModelId(model.id)
                if (config != null) {
                    repository.deleteConfig(config)
                }

                // Delete model file from disk if requested
                if (deleteFile && model.pathType != PathType.CONTENT_URI) {
                    try {
                        val modelFile = java.io.File(model.modelPath)
                        if (modelFile.exists()) {
                            if (modelFile.isDirectory) {
                                modelFile.deleteRecursively()
                            } else {
                                modelFile.delete()
                            }
                            Log.d("LLMModelVM", "Deleted model file: ${model.modelPath}")
                        }
                    } catch (e: Exception) {
                        Log.e("LLMModelVM", "Failed to delete model file: ${e.message}")
                    }
                }

                // Delete from database
                repository.deleteModel(model)
                Log.d("LLMModelVM", "Model deleted: ${model.modelName}")
            } catch (e: Exception) {
                Log.e("LLMModelVM", "Failed to delete model: ${e.message}")
                AppStateManager.setError("Failed to delete model: ${e.message}")
            }
        }
    }
}