package com.dark.tool_neuron.viewModel.setupScreen

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.dark.ai_module.model.ModelProvider
import com.dark.ai_module.model.ModelType
import com.dark.ai_module.workers.ModelManager
import com.dark.tool_neuron.worker.ModelInstaller
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

data class ModelDownloadState(
    val name: String,
    val description: String,
    val downloadUrl: String,
    val fileName: String,
    val modelType: ModelType = ModelType.TEXT,
    val status: DownloadStatus = DownloadStatus.Pending,
    val progress: Float = 0f,
    val extractionProgress: Float = 0f,
    val error: String? = null
)

sealed class DownloadStatus {
    data object Pending : DownloadStatus()
    data object Downloading : DownloadStatus()
    data object Extracting : DownloadStatus()
    data object Completed : DownloadStatus()
    data class Failed(val message: String) : DownloadStatus()
}

data class SetupScreenState(
    val selectedOption: Int? = null,
    val models: List<ModelDownloadState> = emptyList(),
    val isDownloading: Boolean = false,
    val allDownloadsComplete: Boolean = false
)


class SetupViewModel(
    application: Application,
) : AndroidViewModel(application) {

    private val _state = MutableStateFlow(SetupScreenState())
    val state: StateFlow<SetupScreenState> = _state.asStateFlow()

    private val modelsDirectory = File(application.filesDir, "models")
    private val ttsDirectory = File(modelsDirectory, "tts")
    private val sttDirectory = File(modelsDirectory, "stt")

    init {
        if (!modelsDirectory.exists()) modelsDirectory.mkdirs()
        if (!ttsDirectory.exists()) ttsDirectory.mkdirs()
        if (!sttDirectory.exists()) sttDirectory.mkdirs()
    }

    fun selectOption(option: Int) {
        _state.update { it.copy(selectedOption = option) }
        setupModelsForOption(option)
    }

    private fun setupModelsForOption(option: Int) {
        val models = when (option) {
            0 -> {
                // Text only
                listOf(
                    ModelDownloadState(
                        name = "Qwen-LLM :: 0.5B",
                        description = "Quick To Reply, But Less Accurate",
                        downloadUrl = "https://huggingface.co/Qwen/Qwen2.5-0.5B-Instruct-GGUF/resolve/main/qwen2.5-0.5b-instruct-q4_0.gguf?download=true",
                        fileName = "qwen2.5-0.5b-Q3_K_S.gguf",
                        modelType = ModelType.TEXT
                    )
                )
            }

            1 -> {
                // Text + STT
                listOf(
                    ModelDownloadState(
                        name = "Qwen-LLM :: 0.5B",
                        description = "Quick To Reply, But Less Accurate",
                        downloadUrl = "https://huggingface.co/Qwen/Qwen2.5-0.5B-Instruct-GGUF/resolve/main/qwen2.5-0.5b-instruct-q4_0.gguf?download=true",
                        fileName = "qwen2.5-0.5b-Q3_K_S.gguf",
                        modelType = ModelType.TEXT
                    ), ModelDownloadState(
                        name = "Whisper-EN-Small",
                        description = "A STT Model With 90% Accuracy",
                        downloadUrl = "https://github.com/Siddhesh2377/ToolNeuron/releases/download/Beta-4.5/sherpa-onnx-whisper-tiny.zip",
                        fileName = "whisper-en-smallzip",
                        modelType = ModelType.STT
                    )
                )
            }

            2 -> {
                // Text + TTS
                listOf(
                    ModelDownloadState(
                        name = "Qwen-LLM :: 0.5B",
                        description = "Quick To Reply, But Less Accurate",
                        downloadUrl = "https://huggingface.co/Qwen/Qwen2.5-0.5B-Instruct-GGUF/resolve/main/qwen2.5-0.5b-instruct-q4_0.gguf?download=true",
                        fileName = "qwen2.5-0.5b-Q3_K_S.gguf",
                        modelType = ModelType.TEXT
                    ), ModelDownloadState(
                        name = "KOR0-TTS-0.19-M",
                        description = "Quick To Reply, But Less Accurate",
                        downloadUrl = "https://github.com/Siddhesh2377/ToolNeuron/releases/download/Beta-4.5/kokoro-en-v0_19.zip",
                        fileName = "kor0-tts-0.19-mzip",
                        modelType = ModelType.TTS
                    )
                )
            }

            3 -> {
                // Text + STT + TTS
                listOf(
                    ModelDownloadState(
                        name = "Qwen-LLM :: 0.5B",
                        description = "Quick To Reply, But Less Accurate",
                        downloadUrl = "https://huggingface.co/Qwen/Qwen2.5-0.5B-Instruct-GGUF/resolve/main/qwen2.5-0.5b-instruct-q4_0.gguf?download=true",
                        fileName = "qwen2.5-0.5b-Q3_K_S.gguf",
                        modelType = ModelType.TEXT
                    ), ModelDownloadState(
                        name = "Whisper-EN-Small",
                        description = "A STT Model With 90% Accuracy",
                        downloadUrl = "https://github.com/Siddhesh2377/ToolNeuron/releases/download/Beta-4.5/sherpa-onnx-whisper-tiny.zip",
                        fileName = "whisper-en-smallzip",
                        modelType = ModelType.STT
                    ), ModelDownloadState(
                        name = "KOR0-TTS-0.19-M",
                        description = "Quick To Reply, But Less Accurate",
                        downloadUrl = "https://github.com/Siddhesh2377/ToolNeuron/releases/download/Beta-4.5/kokoro-en-v0_19.zip",
                        fileName = "kor0-tts-0.19-mzip",
                        modelType = ModelType.TTS
                    )
                )
            }

            4 -> {
                // Text + STT + TTS
                listOf(
                    ModelDownloadState(
                        name = "Whisper-EN-Small",
                        description = "A STT Model With 90% Accuracy",
                        downloadUrl = "https://github.com/Siddhesh2377/ToolNeuron/releases/download/Beta-4.5/sherpa-onnx-whisper-tiny.zip",
                        fileName = "whisper-en-smallzip",
                        modelType = ModelType.STT
                    ), ModelDownloadState(
                        name = "KOR0-TTS-0.19-M",
                        description = "Quick To Reply, But Less Accurate",
                        downloadUrl = "https://github.com/Siddhesh2377/ToolNeuron/releases/download/Beta-4.5/kokoro-en-v0_19.zip",
                        fileName = "kor0-tts-0.19-mzip",
                        modelType = ModelType.TTS
                    )
                )
            }

            else -> emptyList() // Skip option
        }

        _state.update { it.copy(models = models) }

        // Start downloading automatically if there are models
        if (models.isNotEmpty()) {
            startDownloads()
        }
    }

    private fun startDownloads() {
        _state.update { it.copy(isDownloading = true) }

        viewModelScope.launch {
            val models = _state.value.models

            models.forEachIndexed { index, model ->
                // Check if model already exists in database
                val existingModel = ModelManager.checkIfInstalled(model.name)
                if (existingModel) {
                    updateModelStatus(index, DownloadStatus.Completed, 1f)
                } else {
                    downloadModel(index, model)
                }
            }

            // Check if all downloads are complete
            checkAllDownloadsComplete()
        }
    }

    private fun downloadModel(index: Int, model: ModelDownloadState) {
        updateModelStatus(index, DownloadStatus.Downloading, 0f)

        val provider = when {
            model.downloadUrl.endsWith(".zip") -> ModelProvider.SherpaONNX
            model.downloadUrl.contains(".gguf") -> ModelProvider.LocalGGUF
            else -> ModelProvider.OpenRouter
        }

        viewModelScope.launch(Dispatchers.IO) {
            ModelInstaller.installModel(
                context = getApplication(),
                name = model.name,
                url = model.downloadUrl,
                fileName = model.fileName,
                provider = provider,
                modelType = model.modelType,
                onProgress = { progress ->
                    updateModelProgress(index, progress)
                },
                onComplete = {

                    updateModelStatus(index, DownloadStatus.Completed, 1f)
                    checkAllDownloadsComplete()

                },
                onError = { exception ->

                    updateModelStatus(
                        index,
                        DownloadStatus.Failed(exception.message ?: "Unknown error"),
                        0f,
                        exception.message
                    )
                    checkAllDownloadsComplete()

                })
        }
    }

    private fun updateModelStatus(
        index: Int, status: DownloadStatus, progress: Float, error: String? = null
    ) {
        _state.update { currentState ->
            val updatedModels = currentState.models.toMutableList()
            updatedModels[index] = updatedModels[index].copy(
                status = status, progress = progress, error = error
            )
            currentState.copy(models = updatedModels)
        }
    }

    private fun updateModelProgress(index: Int, progress: Float) {
        _state.update { currentState ->
            val updatedModels = currentState.models.toMutableList()
            updatedModels[index] = updatedModels[index].copy(progress = progress)
            currentState.copy(models = updatedModels)
        }
    }

    private fun checkAllDownloadsComplete() {
        val models = _state.value.models
        val allComplete = models.all { it.status is DownloadStatus.Completed }
        val hasFailures = models.any { it.status is DownloadStatus.Failed }

        _state.update {
            it.copy(
                isDownloading = !allComplete, allDownloadsComplete = allComplete && !hasFailures
            )
        }
    }

    fun retryFailedDownloads() {
        viewModelScope.launch {
            val models = _state.value.models

            models.forEachIndexed { index, model ->
                if (model.status is DownloadStatus.Failed) {
                    downloadModel(index, model)
                }
            }
        }
    }

    fun retryDownload(index: Int) {
        viewModelScope.launch {
            val model = _state.value.models[index]
            downloadModel(index, model)
        }
    }
}