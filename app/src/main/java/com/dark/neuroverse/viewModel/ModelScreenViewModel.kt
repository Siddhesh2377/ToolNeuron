package com.dark.neuroverse.viewModel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dark.ai_module.model.ModelData
import com.dark.ai_module.model.ModelProvider
import com.dark.ai_module.workers.ModelManager
import com.dark.ai_module.workers.downloadFile
import com.dark.neuroverse.data.UserPrefs
import com.dark.neuroverse.model.DownloadState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

class ModelScreenViewModel : ViewModel() {

    // ═══════════════════════════════════════════════════════════════════════
    // GGUF Models State
    // ═══════════════════════════════════════════════════════════════════════
    private val _models = MutableStateFlow<List<ModelData>>(emptyList())
    val models: StateFlow<List<ModelData>> = _models

    private val _downloadStates = MutableStateFlow<Map<String, DownloadState>>(emptyMap())
    val downloadStates: StateFlow<Map<String, DownloadState>> = _downloadStates

    private val downloadJobs = mutableMapOf<String, Job>()

    // ═══════════════════════════════════════════════════════════════════════
    // OpenRouter State
    // ═══════════════════════════════════════════════════════════════════════
    private val _openRouterApiKey = MutableStateFlow("")
    val openRouterApiKey: StateFlow<String> = _openRouterApiKey

    private val _openRouterBaseUrl = MutableStateFlow("https://openrouter.ai/api/v1")
    val openRouterBaseUrl: StateFlow<String> = _openRouterBaseUrl

    private val _openRouterModels = MutableStateFlow<List<String>>(emptyList())
    val openRouterModels: StateFlow<List<String>> = _openRouterModels

    private val _availableModels = MutableStateFlow<List<String>>(emptyList())
    val availableModels: StateFlow<List<String>> = _availableModels

    init {
        observeModels()
    }

    private fun observeModels() {
        viewModelScope.launch {
            ModelManager.observeModels().collectLatest { modelList ->
                _models.value = modelList

                // Sync OpenRouter models list from DB
                val openRouterModelIds = modelList
                    .filter { it.providerName == ModelProvider.OpenRouter.toString() }
                    .map { it.modelUrl.toString() } // For OpenRouter, modelUrl.toString() = model ID

                _openRouterModels.value = openRouterModelIds
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // OpenRouter Methods
    // ═══════════════════════════════════════════════════════════════════════

    fun initOpenRouter(context: Context) {
        loadOpenRouterConfig(context)

        viewModelScope.launch(Dispatchers.IO) {
            UserPrefs.getOpenRouterApiKey(context).collectLatest { key ->
                _openRouterApiKey.value = key
                if (key.isNotBlank() && _availableModels.value.isEmpty()) {
                    fetchAvailableModels()
                }
            }
        }
    }

    private fun loadOpenRouterConfig(context: Context) {
        viewModelScope.launch {
            UserPrefs.getOpenRouterApiKey(context).collect { key ->
                _openRouterApiKey.value = key
            }
        }

        viewModelScope.launch {
            UserPrefs.getOpenRouterBaseUrl(context).collect { url ->
                _openRouterBaseUrl.value = url
            }
        }
    }

    fun saveOpenRouterApiKey(context: Context, key: String) {
        viewModelScope.launch {
            UserPrefs.setOpenRouterApiKey(context, key)
            _openRouterApiKey.value = key

            // Auto-fetch models when key is set
            if (key.isNotBlank()) {
                fetchAvailableModels()
            }
        }
    }

    fun saveOpenRouterBaseUrl(context: Context, url: String) {
        viewModelScope.launch {
            UserPrefs.setOpenRouterBaseUrl(context, url)
            _openRouterBaseUrl.value = url
        }
    }

    fun addOpenRouterModel(modelId: String) {
        if (modelId in _openRouterModels.value) return

        _openRouterModels.update { it + modelId }

        // Persist to Room DB
        viewModelScope.launch {
            ModelManager.addModel(
                ModelData(
                    modelName = modelId,
                    providerName = ModelProvider.OpenRouter.toString(),
                    modelUrl = modelId, // For OpenRouter, path = model ID
                    ctxSize = 0, // Context determined by API
                    isImported = false,
                    isToolCalling = true // Most OpenRouter models support tools
                )
            )
        }
    }

    fun removeOpenRouterModel(modelId: String) {
        _openRouterModels.update { it - modelId }

        // Remove from Room DB
        viewModelScope.launch {
            ModelManager.removeModel(modelId)
        }
    }

    fun fetchAvailableModels() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val apiKey = _openRouterApiKey.value
                if (apiKey.isBlank()) {
                    println("OpenRouter: API key not set")
                    return@launch
                }

                val url = "${_openRouterBaseUrl.value}/models"
                val connection = java.net.URL(url).openConnection() as java.net.HttpURLConnection

                connection.apply {
                    requestMethod = "GET"
                    setRequestProperty("Authorization", "Bearer $apiKey")
                    setRequestProperty("Content-Type", "application/json")
                    connectTimeout = 15000
                    readTimeout = 15000
                }

                val responseCode = connection.responseCode

                if (responseCode != 200) {
                    val errorStream = connection.errorStream
                    val errorMsg = errorStream?.bufferedReader()?.readText() ?: "Unknown error"
                    println("OpenRouter fetch error $responseCode: $errorMsg")
                    return@launch
                }

                val response = connection.inputStream.bufferedReader().use { it.readText() }
                connection.disconnect()

                // Parse JSON response
                val json = org.json.JSONObject(response)
                val dataArray = json.getJSONArray("data")

                val modelIds = (0 until dataArray.length()).mapNotNull { i ->
                    try {
                        dataArray.getJSONObject(i).getString("id")
                    } catch (e: Exception) {
                        null
                    }
                }.sorted() // Sort alphabetically for better UX

                _availableModels.value = modelIds
                println("OpenRouter: Fetched ${modelIds.size} models")

            } catch (e: Exception) {
                println("OpenRouter fetch exception: ${e.message}")
                e.printStackTrace()
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // GGUF Download Methods
    // ═══════════════════════════════════════════════════════════════════════

    fun startDownload(modelData: ModelData, context: Context) {
        if (downloadJobs.containsKey(modelData.modelName)) return

        _downloadStates.update {
            it + (modelData.modelUrl.toString() to DownloadState(isDownloading = true))
        }

        val job = viewModelScope.launch(Dispatchers.IO) {
            try {
                val modelDir = File(context.filesDir, "models")
                if (!modelDir.exists()) {
                    modelDir.mkdirs()
                }

                downloadFile(
                    fileUrl = modelData.modelUrl.toString(), // URL from marketplace
                    outputFile = File(modelData.modelPath),
                    onProgress = { progress ->
                        _downloadStates.update {
                            val current = it[modelData.modelUrl.toString()] ?: DownloadState()
                            it + (modelData.modelUrl.toString() to current.copy(progress = progress))
                        }
                    },
                    onComplete = {
                        // Update model path to local file
                        val localModel = modelData.copy(
                            modelUrl = modelData.modelPath,
                            isImported = false
                        )
                        addModel(localModel)

                        downloadJobs.remove(modelData.modelName)
                        _downloadStates.update {
                            val current = it[modelData.modelUrl.toString()] ?: DownloadState()
                            it + (modelData.modelUrl.toString() to current.copy(
                                isDownloading = false,
                                isComplete = true
                            ))
                        }
                    },
                    onError = { error ->
                        downloadJobs.remove(modelData.modelName)
                        _downloadStates.update {
                            val current = it[modelData.modelUrl.toString()] ?: DownloadState()
                            it + (modelData.modelUrl.toString() to current.copy(
                                isDownloading = false,
                                errorMessage = error.message
                            ))
                        }
                    }
                )
            } catch (e: Exception) {
                downloadJobs.remove(modelData.modelName)
                _downloadStates.update {
                    val current = it[modelData.modelUrl.toString()] ?: DownloadState()
                    it + (modelData.modelUrl.toString() to current.copy(
                        isDownloading = false,
                        errorMessage = e.message
                    ))
                }
            }
        }

        downloadJobs[modelData.modelName] = job
    }

    fun cancelDownload(modelName: String, modelUrl: String) {
        downloadJobs[modelName]?.cancel()
        downloadJobs.remove(modelName)

        // Delete partial file
        File(modelUrl.toString()).delete()

        _downloadStates.update {
            it + (modelUrl.toString() to DownloadState(isDownloading = false))
        }
    }

    fun addModel(model: ModelData) {
        viewModelScope.launch {
            ModelManager.addModel(model)
        }
    }

    fun checkIfInstalled(modelName: String, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            val exists = ModelManager.checkIfInstalled(modelName)
            onResult(exists)
        }
    }

    fun removeModel(modelName: String) {
        viewModelScope.launch {
            ModelManager.getModel(modelName)?.let { model ->
                // Only delete file for downloaded GGUF models (not imported or OpenRouter)
                if (!model.isImported && model.providerName == ModelProvider.LocalGGUF.toString()) {
                    File(model.modelUrl.toString()).delete()
                }
            }
            ModelManager.removeModel(modelName)
        }
    }
}