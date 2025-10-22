package com.dark.neuroverse.viewModel.setupScreen

import android.app.Application
import androidx.datastore.core.use
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.dark.ai_module.workers.downloadFile
import com.dark.ai_module.model.ModelData
import com.dark.ai_module.model.ModelType
import com.dark.ai_module.model.ModelProvider
import com.dark.ai_module.workers.ModelManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.util.zip.ZipInputStream
import kotlin.io.use
import kotlin.use

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

    private fun updateExtractionProgress(index: Int, progress: Float) {
        _state.update { currentState ->
            val updatedModels = currentState.models.toMutableList()
            updatedModels[index] = updatedModels[index].copy(extractionProgress = progress)
            currentState.copy(models = updatedModels)
        }
    }

    private fun setupModelsForOption(option: Int) {
        val models = when (option) {
            0 -> {
                // Text only
                listOf(
                    ModelDownloadState(
                        name = "Lucy-LLM :: 128K",
                        description = "Quick To Reply, But Less Accurate",
                        downloadUrl = "https://huggingface.co/Menlo/Lucy-128k-gguf/resolve/main/lucy_128k-Q3_K_S.gguf?download=true",
                        fileName = "lucy_128k-Q3_K_S.gguf",
                        modelType = ModelType.TEXT
                    )
                )
            }
            1 -> {
                // Text + STT
                listOf(
                    ModelDownloadState(
                        name = "Lucy-LLM :: 128K",
                        description = "Quick To Reply, But Less Accurate",
                        downloadUrl = "https://huggingface.co/Menlo/Lucy-128k-gguf/resolve/main/lucy_128k-Q3_K_S.gguf?download=true",
                        fileName = "lucy_128k-Q3_K_S.gguf",
                        modelType = ModelType.TEXT
                    ),
                    ModelDownloadState(
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
                        name = "Lucy-LLM :: 128K",
                        description = "Quick To Reply, But Less Accurate",
                        downloadUrl = "https://huggingface.co/Menlo/Lucy-128k-gguf/resolve/main/lucy_128k-Q3_K_S.gguf?download=true",
                        fileName = "lucy_128k-Q3_K_S.gguf",
                        modelType = ModelType.TEXT
                    ),
                    ModelDownloadState(
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
                        name = "Lucy-LLM :: 128K",
                        description = "Quick To Reply, But Less Accurate",
                        downloadUrl = "https://huggingface.co/Menlo/Lucy-128k-gguf/resolve/main/lucy_128k-Q3_K_S.gguf?download=true",
                        fileName = "lucy_128k-Q3_K_S.gguf",
                        modelType = ModelType.TEXT
                    ),
                    ModelDownloadState(
                        name = "Whisper-EN-Small",
                        description = "A STT Model With 90% Accuracy",
                        downloadUrl = "https://github.com/Siddhesh2377/ToolNeuron/releases/download/Beta-4.5/sherpa-onnx-whisper-tiny.zip",
                        fileName = "whisper-en-smallzip",
                        modelType = ModelType.STT
                    ),
                    ModelDownloadState(
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
                    val outputFile = File(modelsDirectory, model.fileName)
                    downloadModel(index, model, outputFile)
                }
            }

            // Check if all downloads are complete
            checkAllDownloadsComplete()
        }
    }

    private suspend fun downloadModel(index: Int, model: ModelDownloadState, outputFile: File) {
        // Update status to downloading
        updateModelStatus(index, DownloadStatus.Downloading, 0f)

        downloadFile(
            fileUrl = model.downloadUrl,
            outputFile = outputFile,
            onProgress = { progress ->
                updateModelProgress(index, progress)
            },
            onComplete = {
                viewModelScope.launch {
                    // Handle extraction for TTS/STT models
                    if (model.modelType == ModelType.TTS || model.modelType == ModelType.STT) {
                        updateModelStatus(index, DownloadStatus.Extracting, 1f)
                        extractAndSaveModel(index, model, outputFile)
                    } else {
                        // For TEXT models, save directly
                        saveModelToDatabase(model, outputFile.absolutePath)
                        updateModelStatus(index, DownloadStatus.Completed, 1f)
                    }
                    checkAllDownloadsComplete()
                }
            },
            onError = { exception ->
                updateModelStatus(
                    index,
                    DownloadStatus.Failed(exception.message ?: "Unknown error"),
                    0f,
                    exception.message
                )
                checkAllDownloadsComplete()
            }
        )
    }

    private suspend fun extractAndSaveModel(
        index: Int,
        model: ModelDownloadState,
        archiveFile: File
    ) = withContext(Dispatchers.IO) {
        try {
            val extractDir = when (model.modelType) {
                ModelType.TTS -> File(ttsDirectory, model.name.replace(" ", "_").replace("::", ""))
                ModelType.STT -> File(sttDirectory, model.name.replace(" ", "_").replace("::", ""))
                else -> modelsDirectory
            }

            if (!extractDir.exists()) extractDir.mkdirs()

            // Get total size for progress calculation
            val totalSize = archiveFile.length()
            var processedBytes = 0L
            var lastProgressUpdate = 0L

            // Extract zip archive with optimizations
            ZipInputStream(BufferedInputStream(FileInputStream(archiveFile), 65536)).use { zipIn ->
                var entry = zipIn.nextEntry
                while (entry != null) {
                    val outputFile = File(extractDir, entry.name)

                    if (entry.isDirectory) {
                        outputFile.mkdirs()
                    } else {
                        outputFile.parentFile?.mkdirs()
                        BufferedOutputStream(FileOutputStream(outputFile), 65536).use { fos ->
                            val buffer = ByteArray(65536) // Increased buffer size
                            var bytesRead: Int
                            while (zipIn.read(buffer).also { bytesRead = it } != -1) {
                                fos.write(buffer, 0, bytesRead)
                                processedBytes += bytesRead

                                // Throttle UI updates - only update every 100KB or 5%
                                val shouldUpdate = processedBytes - lastProgressUpdate > 102400 || // 100KB
                                        (processedBytes.toFloat() / totalSize - lastProgressUpdate.toFloat() / totalSize) > 0.05f // 5%

                                if (shouldUpdate) {
                                    lastProgressUpdate = processedBytes
                                    val progress = (processedBytes.toFloat() / totalSize).coerceIn(0f, 1f)
                                    withContext(Dispatchers.Main) {
                                        updateExtractionProgress(index, progress)
                                    }
                                }
                            }
                        }
                    }
                    zipIn.closeEntry()
                    entry = zipIn.nextEntry
                }
            }

            // Final progress update
            withContext(Dispatchers.Main) {
                updateExtractionProgress(index, 1f)
            }

            // Delete the archive file after extraction
            archiveFile.delete()

            // Save model to database with extracted directory path
            saveModelToDatabase(model, extractDir.absolutePath)

            withContext(Dispatchers.Main) {
                updateModelStatus(index, DownloadStatus.Completed, 1f)
            }
        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                updateModelStatus(
                    index,
                    DownloadStatus.Failed(e.message ?: "Extraction failed"),
                    0f,
                    e.message
                )
            }
        }
    }

    private suspend fun saveModelToDatabase(model: ModelDownloadState, filePath: String) {
        val modelData = ModelData(
            modelName = model.name,
            providerName = ModelProvider.LocalGGUF.toString(),
            modelType = model.modelType,
            modelPath = filePath,
            modelUrl = model.downloadUrl,
            isImported = true
        )

        ModelManager.addModel(modelData)
    }

    private fun updateModelStatus(
        index: Int,
        status: DownloadStatus,
        progress: Float,
        error: String? = null
    ) {
        _state.update { currentState ->
            val updatedModels = currentState.models.toMutableList()
            updatedModels[index] = updatedModels[index].copy(
                status = status,
                progress = progress,
                error = error
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
                isDownloading = !allComplete,
                allDownloadsComplete = allComplete && !hasFailures
            )
        }
    }

    fun retryFailedDownloads() {
        viewModelScope.launch {
            val models = _state.value.models

            models.forEachIndexed { index, model ->
                if (model.status is DownloadStatus.Failed) {
                    val outputFile = File(modelsDirectory, model.fileName)
                    downloadModel(index, model, outputFile)
                }
            }
        }
    }

    fun retryDownload(index: Int) {
        viewModelScope.launch {
            val model = _state.value.models[index]
            val outputFile = File(modelsDirectory, model.fileName)
            downloadModel(index, model, outputFile)
        }
    }

    fun cancelDownloads() {
        // Cancel all ongoing downloads
        _state.update { it.copy(isDownloading = false) }
    }

    fun getModelFilePath(fileName: String): String {
        return File(modelsDirectory, fileName).absolutePath
    }
}