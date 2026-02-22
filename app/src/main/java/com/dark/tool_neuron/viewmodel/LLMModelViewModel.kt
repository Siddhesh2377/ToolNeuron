package com.dark.tool_neuron.viewmodel

import android.app.Application
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.dark.tool_neuron.data.AppSettingsDataStore
import com.dark.tool_neuron.models.enums.PathType
import com.dark.tool_neuron.models.enums.ProviderType
import com.dark.tool_neuron.models.table_schema.Model
import com.dark.tool_neuron.models.table_schema.ModelConfig
import com.dark.tool_neuron.repo.ModelRepository
import com.dark.tool_neuron.state.AppStateManager
import com.dark.tool_neuron.worker.DiffusionConfig
import com.dark.tool_neuron.worker.DiffusionInferenceParams
import com.dark.tool_neuron.worker.LlmModelWorker
import dagger.hilt.android.lifecycle.HiltViewModel
import jakarta.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import androidx.core.net.toUri

@HiltViewModel
class LLMModelViewModel @Inject constructor(
    application: Application,
    private val repository: ModelRepository
) : AndroidViewModel(application) {

    private val appSettings = AppSettingsDataStore(application)

    val installedModels: Flow<List<Model>> = repository.getAllModels()
        .map { models -> models.filter { it.providerType != ProviderType.TTS } }

    private val _currentModelID = MutableStateFlow("")
    val currentModelID: StateFlow<String> = _currentModelID.asStateFlow()

    private val _currentModelType = MutableStateFlow<ProviderType?>(null)

    // Model loading states
    val isGgufModelLoaded = LlmModelWorker.isGgufModelLoaded
    val isDiffusionModelLoaded = LlmModelWorker.isDiffusionModelLoaded

    suspend fun getModelConfig(modelId: String): ModelConfig? {
        return repository.getConfigByModelId(modelId)
    }

    fun loadModel(model: Model) {
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
            LlmModelWorker._currentGgufModelId.value = model.id
            _currentModelID.value = model.id
            _currentModelType.value = ProviderType.GGUF
            AppStateManager.setModelLoaded(model.modelName)
            appSettings.saveLastModelId(model.id)

            // Update tool calling model state and sync tools
            val nativeSupports = LlmModelWorker.isToolCallingSupportedGguf()
            com.dark.tool_neuron.plugins.PluginManager.setToolCallingModelLoaded(nativeSupports)
            com.dark.tool_neuron.plugins.PluginManager.syncToolsWithLLM()
        } else {
            AppStateManager.setError("Failed to load GGUF model")
        }
    }

    private suspend fun loadDiffusionModel(model: Model, config: ModelConfig) {
        val diffusionConfig = parseDiffusionConfig(config)

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
            LlmModelWorker._currentDiffusionModelId.value = model.id
            _currentModelID.value = model.id
            _currentModelType.value = ProviderType.DIFFUSION
            AppStateManager.setModelLoaded(model.modelName)
            appSettings.saveLastModelId(model.id)
        } else {
            AppStateManager.setError("Failed to load Diffusion model")
        }
    }

    private fun parseDiffusionConfig(config: ModelConfig): DiffusionConfig {
        if (config.modelLoadingParams == null) {
            return DiffusionConfig()
        }

        return try {
            val json = org.json.JSONObject(config.modelLoadingParams)
            Log.d("DiffusionConfig", "JSON: $json")
            DiffusionConfig(
                textEmbeddingSize = json.optInt("text_embedding_size", 768),
                runOnCpu = json.optBoolean("run_on_cpu", false),
                useCpuClip = json.optBoolean("use_cpu_clip", true),
                isPony = json.optBoolean("is_pony", false),
                httpPort = json.optInt("http_port", 8081),
                safetyMode = json.optBoolean("safety_mode", false),
                width = json.optInt("width", 512),
                height = json.optInt("height", 512)
            )
        } catch (e: Exception) {
            Log.e("DiffusionConfig", "Error parsing JSON: ${e.message}")
            DiffusionConfig()
        }
    }

    private suspend fun unloadCurrentModel() {
        try {
            when (_currentModelType.value) {
                ProviderType.GGUF -> {
                    LlmModelWorker.unloadGgufModel()
                    LlmModelWorker._currentGgufModelId.value = null // ADD THIS
                }
                ProviderType.DIFFUSION -> {
                    LlmModelWorker.stopDiffusionBackend()
                    LlmModelWorker._currentDiffusionModelId.value = null // ADD THIS
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
                        LlmModelWorker._currentGgufModelId.value = null
                    }
                    ProviderType.DIFFUSION -> {
                        LlmModelWorker.stopDiffusionBackend()
                        LlmModelWorker._currentDiffusionModelId.value = null
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