package com.dark.tool_neuron.viewModel

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dark.ai_module.model.ModelData
import com.dark.ai_module.model.OpenRouterModel
import com.dark.ai_module.model.toModelData
import com.dark.ai_module.workers.DownloadState
import com.dark.ai_module.workers.ModelInstallationManager
import com.dark.ai_module.workers.ModelManager
import com.dark.tool_neuron.data.UserPrefs
import com.dark.tool_neuron.service.ModelDownloadService
import com.dark.tool_neuron.util.initOpenRouterFromPrefs
import com.mp.ai_engine.models.llm_models.ModelProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * ViewModel for managing AI models across different providers (GGUF, OpenRouter, Sherpa ONNX).
 *
 * Features:
 * - Observes installed models from database
 * - Manages OpenRouter API configuration and model fetching
 * - Handles model downloads via ModelInstallationManager
 * - Provides real-time download progress tracking
 */
class ModelScreenViewModel : ViewModel() {

    /* ========================================================================= */
    /* STATE FLOWS - All UI-observable state                                    */
    /* ========================================================================= */

    // All installed models (GGUF + OpenRouter + Sherpa)
    private val _models = MutableStateFlow<List<ModelData>>(emptyList())
    val models: StateFlow<List<ModelData>> = _models.asStateFlow()

    // OpenRouter API configuration
    private val _openRouterApiKey = MutableStateFlow("")
    val openRouterApiKey: StateFlow<String> = _openRouterApiKey.asStateFlow()

    private val _openRouterBaseUrl = MutableStateFlow("https://openrouter.ai/api/v1")
    val openRouterBaseUrl: StateFlow<String> = _openRouterBaseUrl.asStateFlow()

    // OpenRouter installed models (derived from _models)
    private val _openRouterInstalledModels = MutableStateFlow<List<OpenRouterModel>>(emptyList())
    val openRouterInstalledModels: StateFlow<List<OpenRouterModel>> =
        _openRouterInstalledModels.asStateFlow()

    // Available models from OpenRouter API
    private val _availableModels = MutableStateFlow<List<OpenRouterModel>>(emptyList())
    val availableModels: StateFlow<List<OpenRouterModel>> = _availableModels.asStateFlow()

    // Loading states
    private val _isFetchingModels = MutableStateFlow(false)
    val isFetchingModels: StateFlow<Boolean> = _isFetchingModels.asStateFlow()

    private val _fetchError = MutableStateFlow<String?>(null)
    val fetchError: StateFlow<String?> = _fetchError.asStateFlow()

    // Download progress for UI
    val downloadProgress: StateFlow<Map<String, DownloadState>> =
        ModelInstallationManager.downloadProgress

    /* ========================================================================= */
    /* INITIALIZATION                                                            */
    /* ========================================================================= */

    init {
        observeModels()
    }

    /* ========================================================================= */
    /* MODEL OBSERVATION - Real-time database sync                              */
    /* ========================================================================= */

    /**
     * Observes the model database for real-time updates.
     * Updates both the full model list and OpenRouter-specific models.
     */
    private fun observeModels() = viewModelScope.launch(Dispatchers.IO) {
        ModelManager.observeModels()
            .catch { e ->
                Log.e(TAG, "Error observing models: ${e.message}", e)
            }
            .collectLatest { modelList ->
                _models.value = modelList

                // Derive OpenRouter installed models
                _openRouterInstalledModels.value = modelList
                    .filter { it.providerName == ModelProvider.OPEN_ROUTER.toString() }
                    .map { it.toOpenRouterModel() }
            }
    }

    /* ========================================================================= */
    /* OPENROUTER CONFIGURATION                                                  */
    /* ========================================================================= */

    /**
     * Initializes OpenRouter configuration from UserPrefs.
     * Auto-fetches available models if API key is present.
     */
    fun initOpenRouter(context: Context) {
        viewModelScope.launch {
            // Load base URL
            UserPrefs.getOpenRouterBaseUrl(context)
                .catch { e -> Log.e(TAG, "Error loading base URL: ${e.message}") }
                .collectLatest { url ->
                    _openRouterBaseUrl.value = url
                }
        }

        viewModelScope.launch {
            // Load API key and auto-fetch models
            UserPrefs.getOpenRouterApiKey(context)
                .catch { e -> Log.e(TAG, "Error loading API key: ${e.message}") }
                .collectLatest { key ->
                    _openRouterApiKey.value = key
                    if (key.isNotBlank() && _availableModels.value.isEmpty()) {
                        fetchAvailableModels()
                    }
                }
        }
    }

    /**
     * Saves OpenRouter API key to preferences and re-fetches available models.
     */
    fun saveOpenRouterApiKey(context: Context, key: String) {
        viewModelScope.launch {
            try {
                UserPrefs.setOpenRouterApiKey(context, key)
                _openRouterApiKey.value = key
                initOpenRouterFromPrefs(context)

                if (key.isNotBlank()) {
                    fetchAvailableModels()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error saving API key: ${e.message}", e)
                _fetchError.value = "Failed to save API key"
            }
        }
    }

    /**
     * Saves OpenRouter base URL to preferences.
     */
    fun saveOpenRouterBaseUrl(context: Context, url: String) {
        viewModelScope.launch {
            try {
                UserPrefs.setOpenRouterBaseUrl(context, url)
                _openRouterBaseUrl.value = url
            } catch (e: Exception) {
                Log.e(TAG, "Error saving base URL: ${e.message}", e)
            }
        }
    }

    /* ========================================================================= */
    /* OPENROUTER MODEL MANAGEMENT                                               */
    /* ========================================================================= */

    /**
     * Adds an OpenRouter model to the installed list and persists to database.
     */
    fun addOpenRouterModel(model: OpenRouterModel) {
        // Prevent duplicates
        if (_openRouterInstalledModels.value.any { it.id == model.id }) {
            Log.w(TAG, "Model ${model.id} already installed")
            return
        }

        viewModelScope.launch {
            try {
                val modelData = model.toModelData()
                val result = ModelInstallationManager.installModel(modelData)

                result.onSuccess {
                    Log.i(TAG, "Successfully added OpenRouter model: ${model.name}")
                }.onFailure { error ->
                    Log.e(TAG, "Failed to persist OpenRouter model: ${error.message}", error)
                    _fetchError.value = "Failed to add model: ${error.message}"
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to persist OpenRouter model: ${e.message}", e)
                _fetchError.value = "Failed to add model: ${e.message}"
            }
        }
    }

    /**
     * Fetches available models from OpenRouter API.
     * Updates _availableModels on success.
     */
    fun fetchAvailableModels() {
        val apiKey = _openRouterApiKey.value
        if (apiKey.isBlank()) {
            Log.w(TAG, "Cannot fetch models: API key is blank")
            _fetchError.value = "API key is required"
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            _isFetchingModels.value = true
            _fetchError.value = null

            try {
                val url = "${_openRouterBaseUrl.value}/models"
                val models = fetchModelsFromApi(url, apiKey)

                withContext(Dispatchers.Main) {
                    _availableModels.value = models
                    Log.i(TAG, "Successfully fetched ${models.size} models")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error fetching models: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    _fetchError.value = "Failed to fetch models: ${e.message}"
                }
            } finally {
                withContext(Dispatchers.Main) {
                    _isFetchingModels.value = false
                }
            }
        }
    }

    /**
     * Makes HTTP request to OpenRouter API and parses response.
     */
    private suspend fun fetchModelsFromApi(url: String, apiKey: String): List<OpenRouterModel> {
        return withContext(Dispatchers.IO) {
            val conn = URL(url).openConnection() as HttpURLConnection

            try {
                conn.apply {
                    requestMethod = "GET"
                    setRequestProperty("Authorization", "Bearer $apiKey")
                    setRequestProperty("Content-Type", "application/json")
                    setRequestProperty("HTTP-Referer", "https://github.com/dark-theme/tool-neuron")
                    connectTimeout = 15000
                    readTimeout = 15000
                }

                if (conn.responseCode != 200) {
                    val errorBody = conn.errorStream?.bufferedReader()?.readText()
                        ?: "Unknown error"
                    throw Exception("HTTP ${conn.responseCode}: $errorBody")
                }

                val response = conn.inputStream.bufferedReader().use { it.readText() }
                parseModelsResponse(response)
            } finally {
                conn.disconnect()
            }
        }
    }

    /**
     * Parses JSON response from OpenRouter API into OpenRouterModel list.
     */
    private fun parseModelsResponse(jsonResponse: String): List<OpenRouterModel> {
        val data = JSONObject(jsonResponse).getJSONArray("data")

        return (0 until data.length()).mapNotNull { index ->
            try {
                val modelJson = data.getJSONObject(index)
                val defaultParams = modelJson.optJSONObject("default_parameters") ?: JSONObject()

                val supportsTools = modelJson.optJSONArray("supported_parameters")?.let { arr ->
                    (0 until arr.length()).any { i -> arr.getString(i) == "tools" }
                } ?: false

                OpenRouterModel(
                    id = modelJson.getString("id"),
                    name = modelJson.optString("name", modelJson.getString("id")),
                    ctxSize = modelJson.optInt("context_length", 4096),
                    temperature = defaultParams.optDouble("temperature", 0.7).toFloat(),
                    topP = defaultParams.optDouble("top_p", 0.9).toFloat(),
                    supportsTools = supportsTools
                )
            } catch (e: Exception) {
                Log.w(TAG, "Failed to parse model at index $index: ${e.message}")
                null
            }
        }.distinctBy { it.id }
    }

    /* ========================================================================= */
    /* MODEL DOWNLOAD MANAGEMENT                                                 */
    /* ========================================================================= */

    /**
     * Starts downloading a model using ModelInstallationManager.
     * Can optionally use background service for notifications.
     */
    fun startDownload(modelData: ModelData, context: Context, useService: Boolean = true) {
        val url = modelData.modelUrl
        if (url.isNullOrBlank()) {
            Log.e(TAG, "Cannot start download: Model URL is null or blank")
            return
        }

        viewModelScope.launch {
            try {
                // Check if already downloading
                val currentState = ModelInstallationManager.downloadProgress.first()[url]
                if (currentState is DownloadState.Downloading) {
                    Log.w(TAG, "Download already in progress for: ${modelData.modelName}")
                    return@launch
                }

                if (useService) {
                    // Use foreground service with notifications
                    ModelDownloadService.startDownload(context, modelData)
                } else {
                    // Direct download without service
                    val result = ModelInstallationManager.downloadAndInstallModel(modelData) { progress ->
                        Log.d(TAG, "Download progress for ${modelData.modelName}: $progress%")
                    }

                    result.onSuccess { installedModel ->
                        Log.i(TAG, "Successfully downloaded and installed: ${installedModel.modelName}")
                    }.onFailure { error ->
                        Log.e(TAG, "Download failed for ${modelData.modelName}: ${error.message}", error)
                    }
                }

                Log.i(TAG, "Started download for: ${modelData.modelName}")
            } catch (e: Exception) {
                Log.e(TAG, "Error starting download: ${e.message}", e)
            }
        }
    }

    /**
     * Cancels an active download.
     */
    fun cancelDownload(modelName: String, url: String, context: Context) {
        viewModelScope.launch {
            try {
                // Cancel via service
                ModelDownloadService.cancelDownload(context, url)

                Log.i(TAG, "Cancelled download for: $modelName")
            } catch (e: Exception) {
                Log.e(TAG, "Error cancelling download: ${e.message}", e)
            }
        }
    }

    /* ========================================================================= */
    /* MODEL PERSISTENCE                                                         */
    /* ========================================================================= */

    /**
     * Adds a model to the database.
     */
    fun addModel(model: ModelData) {
        viewModelScope.launch {
            try {
                val result = ModelInstallationManager.installModel(model)

                result.onSuccess {
                    Log.i(TAG, "Successfully added model: ${model.modelName}")
                }.onFailure { error ->
                    Log.e(TAG, "Failed to add model: ${error.message}", error)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to add model: ${e.message}", e)
            }
        }
    }

    /**
     * Removes a model from the database and optionally deletes the file.
     * Only deletes files for non-imported LocalGGUF models.
     */
    fun removeModel(name: String) {
        viewModelScope.launch {
            try {
                val model = ModelManager.getModel(name)

                if (model == null) {
                    Log.w(TAG, "Model not found in database: $name")
                    return@launch
                }

                // Determine if file should be deleted
                val shouldDeleteFile = model.providerName == ModelProvider.GGUF.toString()
                        && !model.isImported

                if (shouldDeleteFile) {
                    // Delete model with file cleanup
                    val result = ModelInstallationManager.deleteModel(model.id, deleteFile = true)

                    result.onSuccess {
                        Log.i(TAG, "Successfully removed model and file: $name")
                    }.onFailure { error ->
                        Log.e(TAG, "Failed to remove model: ${error.message}", error)
                    }
                } else {
                    // Delete model but keep file
                    Log.i(TAG, "Skipping file deletion for: ${model.modelName} " +
                            "(imported=${model.isImported}, provider=${model.providerName})")

                    val result = ModelInstallationManager.deleteModel(model.id, deleteFile = false)

                    result.onSuccess {
                        Log.i(TAG, "Successfully removed model from database: $name")
                    }.onFailure { error ->
                        Log.e(TAG, "Failed to remove model: ${error.message}", error)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to remove model: ${e.message}", e)
            }
        }
    }

    /**
     * Checks if a model with the given name exists in the database.
     */
    fun checkIfInstalled(modelName: String, onResult: (Boolean) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val exists = ModelInstallationManager.isModelInstalled(modelName)
                withContext(Dispatchers.Main) {
                    onResult(exists)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error checking if model installed: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    onResult(false)
                }
            }
        }
    }

    fun clearFetchError() {
        _fetchError.value = null
    }

    private fun ModelData.toOpenRouterModel() = OpenRouterModel(
        id = id,
        name = modelName,
        ctxSize = ctxSize,
        temperature = temp,
        topP = topP
    )

    companion object {
        private const val TAG = "ModelScreenViewModel"
    }
}