package com.dark.tool_neuron.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dark.tool_neuron.models.enums.ProviderType
import com.dark.tool_neuron.models.table_schema.Model
import com.dark.tool_neuron.models.table_schema.ModelConfig
import com.dark.tool_neuron.repo.ModelRepository
import com.dark.tool_neuron.state.AppStateManager
import com.dark.tool_neuron.worker.DiffusionConfig
import com.dark.tool_neuron.worker.LlmModelWorker
import dagger.hilt.android.lifecycle.HiltViewModel
import jakarta.inject.Inject
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class LLMModelViewModel @Inject constructor(
    private val repository: ModelRepository
) : ViewModel() {

    val installedModels: Flow<List<Model>> = repository.getAllModels()

    private val _currentModelID = MutableStateFlow("")
    val currentModelID: StateFlow<String> = _currentModelID.asStateFlow()

    private val _currentModelType = MutableStateFlow<ProviderType?>(null)
    val currentModelType: StateFlow<ProviderType?> = _currentModelType.asStateFlow()

    // Model loading states
    val isGgufModelLoaded = LlmModelWorker.isGgufModelLoaded
    val isDiffusionModelLoaded = LlmModelWorker.isDiffusionModelLoaded
    val diffusionBackendState = LlmModelWorker.diffusionBackendState

    suspend fun getModelConfig(modelId: String): ModelConfig? {
        return repository.getConfigByModelId(modelId)
    }

    fun loadModel(model: Model) {
        viewModelScope.launch {
            try {
                AppStateManager.setLoadingModel(model.modelName, 0f)

                val config = getModelConfig(model.id)
                if (config == null) {
                    AppStateManager.setError("Model configuration not found")
                    return@launch
                }

                simulateLoadingProgress()

                when (model.providerType) {
                    ProviderType.GGUF -> loadGgufModel(model, config)
                    ProviderType.DIFFUSION -> loadDiffusionModel(model, config)
                    else -> {
                        AppStateManager.setError("Unsupported model type: ${model.providerType}")
                    }
                }
            } catch (e: Exception) {
                AppStateManager.setError(e.message ?: "Unknown error")
            }
        }
    }

    private suspend fun loadGgufModel(model: Model, config: ModelConfig) {
        val success = LlmModelWorker.loadGgufModel(model, config)

        if (success) {
            AppStateManager.updateLoadingProgress(1.0f)
            delay(200) // Brief pause to show 100%

            _currentModelID.value = model.id
            _currentModelType.value = ProviderType.GGUF
            AppStateManager.setModelLoaded(model.modelName)
        } else {
            AppStateManager.setError("Failed to load GGUF model")
        }
    }

    private suspend fun loadDiffusionModel(model: Model, config: ModelConfig) {
        // Parse diffusion config
        val diffusionConfig = parseDiffusionConfig(config)

        val success = LlmModelWorker.loadDiffusionModel(
            name = model.modelName,
            modelDir = model.modelPath,
            textEmbeddingSize = diffusionConfig.textEmbeddingSize,
            runOnCpu = diffusionConfig.runOnCpu,
            useCpuClip = diffusionConfig.useCpuClip,
            isPony = diffusionConfig.isPony,
            httpPort = diffusionConfig.httpPort,
            safetyMode = diffusionConfig.safetyMode
        )

        if (success) {
            AppStateManager.updateLoadingProgress(1.0f)
            delay(200) // Brief pause to show 100%

            _currentModelID.value = model.id
            _currentModelType.value = ProviderType.DIFFUSION
            AppStateManager.setModelLoaded(model.modelName)
        } else {
            AppStateManager.setError("Failed to load Diffusion model")
        }
    }

    /**
     * Simulates model loading progress through different stages
     * In a real implementation, this would be replaced with actual loading callbacks
     */
    private suspend fun simulateLoadingProgress() {
        // Stage 1: Initialization (0-20%)
        for (i in 0..20 step 5) {
            AppStateManager.updateLoadingProgress(i / 100f)
            delay(50)
        }

        // Stage 2: Loading weights (20-60%)
        for (i in 20..60 step 4) {
            AppStateManager.updateLoadingProgress(i / 100f)
            delay(80)
        }

        // Stage 3: Optimization (60-90%)
        for (i in 60..90 step 3) {
            AppStateManager.updateLoadingProgress(i / 100f)
            delay(60)
        }

        // Stage 4: Finalization (90-95%)
        for (i in 90..95) {
            AppStateManager.updateLoadingProgress(i / 100f)
            delay(100)
        }
    }

    private fun parseDiffusionConfig(config: ModelConfig): DiffusionConfig {
        if (config.modelLoadingParams == null) {
            return DiffusionConfig()
        }

        return try {
            val json = org.json.JSONObject(config.modelLoadingParams)
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
            DiffusionConfig()
        }
    }

    fun unloadModel() {
        viewModelScope.launch {
            try {
                when (_currentModelType.value) {
                    ProviderType.GGUF -> {
                        LlmModelWorker.unloadGgufModel()
                    }
                    ProviderType.DIFFUSION -> {
                        LlmModelWorker.stopDiffusionBackend()
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

    fun restartDiffusionBackend() {
        if (_currentModelType.value != ProviderType.DIFFUSION) {
            return
        }

        viewModelScope.launch {
            try {
                val success = LlmModelWorker.restartDiffusionBackend()
                if (success) {
                    AppStateManager.setModelLoaded("Diffusion backend restarted")
                } else {
                    AppStateManager.setError("Failed to restart diffusion backend")
                }
            } catch (e: Exception) {
                AppStateManager.setError(e.message ?: "Restart failed")
            }
        }
    }
}