package com.dark.tool_neuron.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dark.tool_neuron.models.engine_schema.GgufEngineSchema
import com.dark.tool_neuron.models.enums.ProviderType
import com.dark.tool_neuron.data.VaultManager
import com.dark.tool_neuron.di.AppContainer
import com.dark.tool_neuron.models.table_schema.Model
import com.dark.tool_neuron.models.table_schema.ModelConfig
import com.dark.tool_neuron.worker.DiffusionConfig
import com.dark.tool_neuron.worker.DiffusionInferenceParams
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class ModelConfigEditorViewModel @Inject constructor() : ViewModel() {

    // Deferred until vault is ready
    private val repository get() = AppContainer.getModelRepository()

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val models: Flow<List<Model>> = VaultManager.isReady
        .flatMapLatest { ready ->
            if (ready) repository.getAllModels()
            else flowOf(emptyList())
        }

    private val _selectedModel = MutableStateFlow<Model?>(null)
    val selectedModel: StateFlow<Model?> = _selectedModel.asStateFlow()

    private val _ggufConfig = MutableStateFlow(GgufEngineSchema())
    val ggufConfig: StateFlow<GgufEngineSchema> = _ggufConfig.asStateFlow()

    private val _diffusionConfig = MutableStateFlow(DiffusionConfig())
    val diffusionConfig: StateFlow<DiffusionConfig> = _diffusionConfig.asStateFlow()

    private val _diffusionInferenceParams = MutableStateFlow(DiffusionInferenceParams())
    val diffusionInferenceParams: StateFlow<DiffusionInferenceParams> =
        _diffusionInferenceParams.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _saveSuccess = MutableStateFlow(false)
    val saveSuccess: StateFlow<Boolean> = _saveSuccess.asStateFlow()

    fun selectModel(model: Model) {
        _selectedModel.value = model
        loadConfigForModel(model)
    }

    private fun loadConfigForModel(model: Model) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val config = repository.getConfigByModelId(model.id)

                when (model.providerType) {
                    ProviderType.GGUF -> {
                        _ggufConfig.value = if (config != null) {
                            GgufEngineSchema.fromJson(
                                config.modelLoadingParams, config.modelInferenceParams
                            )
                        } else {
                            GgufEngineSchema()
                        }
                    }

                    ProviderType.DIFFUSION -> {
                        _diffusionConfig.value = if (config != null) {
                            DiffusionConfig.fromJson(config.modelLoadingParams)
                        } else {
                            DiffusionConfig()
                        }

                        _diffusionInferenceParams.value = if (config != null) {
                            DiffusionInferenceParams.fromJson(config.modelInferenceParams)
                        } else {
                            DiffusionInferenceParams()
                        }
                    }

                    ProviderType.TTS -> {
                        // TTS config managed via TTSDataStore
                    }
                }
            } catch (_: Exception) {
                // Handle error
            } finally {
                _isLoading.value = false
            }
        }
    }


    fun saveConfiguration() {
        val model = _selectedModel.value ?: return

        viewModelScope.launch {
            _isLoading.value = true
            try {
                val existingConfig = repository.getConfigByModelId(model.id)

                val config = when (model.providerType) {
                    ProviderType.GGUF -> {
                        ModelConfig(
                            id = existingConfig?.id ?: "",
                            modelId = model.id,
                            modelLoadingParams = _ggufConfig.value.toLoadingJson(),
                            modelInferenceParams = _ggufConfig.value.toInferenceJson()
                        )
                    }

                    ProviderType.DIFFUSION -> {
                        ModelConfig(
                            id = existingConfig?.id ?: "",
                            modelId = model.id,
                            modelLoadingParams = _diffusionConfig.value.toJson(),
                            modelInferenceParams = _diffusionInferenceParams.value.toJson()
                        )
                    }

                    ProviderType.TTS -> {
                        ModelConfig(
                            id = existingConfig?.id ?: "",
                            modelId = model.id,
                            modelLoadingParams = existingConfig?.modelLoadingParams ?: """{"type":"tts","useNNAPI":false}""",
                            modelInferenceParams = existingConfig?.modelInferenceParams ?: """{"voice":"F1","speed":1.05,"steps":2,"language":"en"}"""
                        )
                    }
                }

                if (existingConfig != null) {
                    repository.updateConfig(config)
                } else {
                    repository.insertConfig(config)
                }

                _saveSuccess.value = true
                delay(2000)
                _saveSuccess.value = false
            } catch (_: Exception) {
                // Handle error
            } finally {
                _isLoading.value = false
            }
        }
    }

    // ==================== GGUF Config Updates ====================

    fun updateGgufThreads(value: Int) {
        _ggufConfig.update {
            it.copy(loadingParams = it.loadingParams.copy(threads = value))
        }
    }

    fun updateGgufContextSize(value: Int) {
        _ggufConfig.update {
            it.copy(loadingParams = it.loadingParams.copy(ctxSize = value))
        }
    }

    fun updateGgufUseMmap(value: Boolean) {
        _ggufConfig.update {
            it.copy(loadingParams = it.loadingParams.copy(useMmap = value))
        }
    }

    fun updateGgufUseMlock(value: Boolean) {
        _ggufConfig.update {
            it.copy(loadingParams = it.loadingParams.copy(useMlock = value))
        }
    }

    fun updateGgufTemperature(value: Float) {
        _ggufConfig.update {
            it.copy(inferenceParams = it.inferenceParams.copy(temperature = value))
        }
    }

    fun updateGgufTopK(value: Int) {
        _ggufConfig.update {
            it.copy(inferenceParams = it.inferenceParams.copy(topK = value))
        }
    }

    fun updateGgufTopP(value: Float) {
        _ggufConfig.update {
            it.copy(inferenceParams = it.inferenceParams.copy(topP = value))
        }
    }

    fun updateGgufMinP(value: Float) {
        _ggufConfig.update {
            it.copy(inferenceParams = it.inferenceParams.copy(minP = value))
        }
    }

    fun updateGgufMaxTokens(value: Int) {
        _ggufConfig.update {
            it.copy(inferenceParams = it.inferenceParams.copy(maxTokens = value))
        }
    }

    fun updateGgufMirostat(value: Int) {
        _ggufConfig.update {
            it.copy(inferenceParams = it.inferenceParams.copy(mirostat = value))
        }
    }

    fun updateGgufMirostatTau(value: Float) {
        _ggufConfig.update {
            it.copy(inferenceParams = it.inferenceParams.copy(mirostatTau = value))
        }
    }

    fun updateGgufMirostatEta(value: Float) {
        _ggufConfig.update {
            it.copy(inferenceParams = it.inferenceParams.copy(mirostatEta = value))
        }
    }

    fun updateGgufSystemPrompt(value: String) {
        _ggufConfig.update {
            it.copy(inferenceParams = it.inferenceParams.copy(systemPrompt = value))
        }
    }

    // ==================== Diffusion Config Updates ====================

    fun updateDiffusionEmbeddingSize(value: Int) {
        _diffusionConfig.update {
            it.copy(textEmbeddingSize = value)
        }
    }

    fun updateDiffusionRunOnCpu(value: Boolean) {
        _diffusionConfig.update {
            it.copy(runOnCpu = value)
        }
    }

    fun updateDiffusionUseCpuClip(value: Boolean) {
        _diffusionConfig.update {
            it.copy(useCpuClip = value)
        }
    }

    fun updateDiffusionIsPony(value: Boolean) {
        _diffusionConfig.update {
            it.copy(isPony = value)
        }
    }

    fun updateDiffusionSafetyMode(value: Boolean) {
        _diffusionConfig.update {
            it.copy(safetyMode = value)
        }
    }

    // ==================== Diffusion Inference Params Updates ====================

    fun updateDiffusionNegativePrompt(value: String) {
        _diffusionInferenceParams.update {
            it.copy(negativePrompt = value)
        }
    }

    fun updateDiffusionSteps(value: Int) {
        _diffusionInferenceParams.update {
            it.copy(steps = value)
        }
    }

    fun updateDiffusionCfgScale(value: Float) {
        _diffusionInferenceParams.update {
            it.copy(cfgScale = value)
        }
    }

    fun updateDiffusionScheduler(value: String) {
        _diffusionInferenceParams.update {
            it.copy(scheduler = value)
        }
    }

    fun updateDiffusionUseOpenCL(value: Boolean) {
        _diffusionInferenceParams.update {
            it.copy(useOpenCL = value)
        }
    }

    fun updateDiffusionDenoiseStrength(value: Float) {
        _diffusionInferenceParams.update {
            it.copy(denoiseStrength = value)
        }
    }

    fun updateDiffusionShowProcess(value: Boolean) {
        _diffusionInferenceParams.update {
            it.copy(showDiffusionProcess = value)
        }
    }

    fun updateDiffusionShowStride(value: Int) {
        _diffusionInferenceParams.update {
            it.copy(showDiffusionStride = value)
        }
    }
}