package com.dark.tool_neuron.viewmodel

import android.app.Application
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.dark.tool_neuron.data.SetupDataStore
import com.dark.tool_neuron.di.AppContainer
import com.dark.tool_neuron.models.data.HuggingFaceModel
import com.dark.tool_neuron.models.data.ModelType
import com.dark.tool_neuron.models.enums.ProviderType
import com.dark.tool_neuron.repo.ModelStoreRepository
import com.dark.tool_neuron.service.ModelDownloadService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

enum class SetupOption {
    TEXT,
    TEXT_UNCENSORED,
    TEXT_TTS,
    IMAGE_GEN,
    POWER_MODE
}

enum class SetupPhase {
    INTRO,
    SETUP
}

class SetupViewModel(application: Application) : AndroidViewModel(application) {

    private val setupDataStore = SetupDataStore(application)
    private val modelStoreRepository = ModelStoreRepository(application)
    private val modelRepository = AppContainer.getModelRepository()

    val downloadStates = ModelDownloadService.downloadStates

    private val _setupPhase = MutableStateFlow(SetupPhase.INTRO)
    val setupPhase: StateFlow<SetupPhase> = _setupPhase

    private val _selectedOption = MutableStateFlow<SetupOption?>(null)
    val selectedOption: StateFlow<SetupOption?> = _selectedOption

    private val _setupComplete = MutableStateFlow(false)
    val setupComplete: StateFlow<Boolean> = _setupComplete

    private val _downloadError = MutableStateFlow<String?>(null)
    val downloadError: StateFlow<String?> = _downloadError

    private val _primaryModelId = MutableStateFlow<String?>(null)
    val primaryModelId: StateFlow<String?> = _primaryModelId

    // ==================== Setup Model Definitions ====================

    private val textModel = HuggingFaceModel(
        id = "ruvltra-claude-code-0.5b",
        name = "Ruvltra Claude Code 0.5B",
        description = "Compact text generation model",
        fileUri = "ruv/ruvltra-claude-code/resolve/main/ruvltra-claude-code-0.5b-q4_k_m.gguf",
        approximateSize = "400 MB",
        modelType = ModelType.GGUF,
        isZip = false,
        tags = listOf("GGUF", "Q4_K_M"),
        requiresNPU = false,
        repositoryUrl = "ruv/ruvltra-claude-code"
    )

    private val textUncensoredModel = HuggingFaceModel(
        id = "gemma3-emotional-1b",
        name = "Gemma3 Emotional 1B",
        description = "Unrestricted text generation model",
        fileUri = "mradermacher/Gemma3-Emotional-1B-i1-GGUF/resolve/main/Gemma3-Emotional-1B.i1-Q4_K_M.gguf",
        approximateSize = "700 MB",
        modelType = ModelType.GGUF,
        isZip = false,
        tags = listOf("GGUF", "Q4_K_M"),
        requiresNPU = false,
        repositoryUrl = "mradermacher/Gemma3-Emotional-1B-i1-GGUF"
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
        val deviceInfo = modelStoreRepository.getDeviceInfo()
        val soc = deviceInfo["soc"] ?: ""
        val suffix = modelStoreRepository.getChipsetSuffix(soc)

        return if (isQualcomm && suffix != null) {
            HuggingFaceModel(
                id = "anythingv5-npu",
                name = "Anything V5.0",
                description = "Anime-style image generation optimized for NPU",
                fileUri = "xororz/sd-qnn/resolve/main/AnythingV5_qnn2.28_${suffix}.zip",
                approximateSize = "1.1GB",
                modelType = ModelType.SD,
                isZip = true,
                chipsetSuffix = suffix,
                runOnCpu = false,
                textEmbeddingSize = 768,
                tags = listOf("NPU", "Anime", "Art"),
                requiresNPU = true,
                repositoryUrl = "xororz/sd-qnn"
            )
        } else {
            HuggingFaceModel(
                id = "anythingv5-cpu",
                name = "Anything V5.0",
                description = "Anime-style image generation for CPU",
                fileUri = "xororz/sd-mnn/resolve/main/AnythingV5.zip",
                approximateSize = "1.2GB",
                modelType = ModelType.SD,
                isZip = true,
                runOnCpu = true,
                textEmbeddingSize = 768,
                tags = listOf("CPU", "Anime", "Art"),
                requiresNPU = false,
                repositoryUrl = "xororz/sd-mnn"
            )
        }
    }

    // ==================== Initialization ====================

    init {
        // Resume active setup downloads if any
        resumeActiveDownloads()

        // Watch for model installations to detect setup completion
        viewModelScope.launch {
            modelRepository.getAllModels().collect { models ->
                if (_selectedOption.value != null && _selectedOption.value != SetupOption.POWER_MODE) {
                    val hasTextOrImage = models.any {
                        it.providerType == ProviderType.GGUF || it.providerType == ProviderType.DIFFUSION
                    }
                    if (hasTextOrImage) {
                        _setupComplete.value = true
                    }
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

    private fun resumeActiveDownloads() {
        val currentStates = downloadStates.value
        val imageModelId = getImageModel().id

        when {
            currentStates.containsKey(textModel.id) && currentStates.containsKey(ttsModel.id) -> {
                _selectedOption.value = SetupOption.TEXT_TTS
                _primaryModelId.value = textModel.id
                _setupPhase.value = SetupPhase.SETUP
            }
            currentStates.containsKey(textModel.id) -> {
                _selectedOption.value = SetupOption.TEXT
                _primaryModelId.value = textModel.id
                _setupPhase.value = SetupPhase.SETUP
            }
            currentStates.containsKey(textUncensoredModel.id) -> {
                _selectedOption.value = SetupOption.TEXT_UNCENSORED
                _primaryModelId.value = textUncensoredModel.id
                _setupPhase.value = SetupPhase.SETUP
            }
            currentStates.containsKey(imageModelId) -> {
                _selectedOption.value = SetupOption.IMAGE_GEN
                _primaryModelId.value = imageModelId
                _setupPhase.value = SetupPhase.SETUP
            }
        }
    }

    // ==================== Actions ====================

    fun advanceFromIntro() {
        if (_setupPhase.value == SetupPhase.INTRO) {
            _setupPhase.value = SetupPhase.SETUP
        }
    }

    fun selectOption(option: SetupOption) {
        if (_selectedOption.value != null) return

        _selectedOption.value = option
        _downloadError.value = null

        when (option) {
            SetupOption.TEXT -> {
                _primaryModelId.value = textModel.id
                downloadModel(textModel)
            }
            SetupOption.TEXT_UNCENSORED -> {
                _primaryModelId.value = textUncensoredModel.id
                downloadModel(textUncensoredModel)
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

        context.startForegroundService(intent)
    }
}
