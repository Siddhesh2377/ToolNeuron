package com.dark.neuroverse.viewModel

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dark.ai_module.model.ModelData
import com.dark.ai_module.model.ModelProvider
import com.dark.ai_module.model.OpenRouterModel
import com.dark.ai_module.model.toModelData
import com.dark.ai_module.workers.ModelManager
import com.dark.ai_module.workers.downloadFile
import com.dark.neuroverse.data.UserPrefs
import com.dark.neuroverse.model.DownloadState
import com.dark.neuroverse.util.initOpenRouterFromPrefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

class ModelScreenViewModel : ViewModel() {
    /* ----------------------------------------------------------- *//* GGUF + OpenRouter  – everything that is/was installed       *//* ----------------------------------------------------------- */
    private val _models = MutableStateFlow<List<ModelData>>(emptyList())
    val models: StateFlow<List<ModelData>> = _models

    /* ----------------------------------------------------------- *//* Local / Remote download state                               *//* ----------------------------------------------------------- */
    private val _downloadStates = MutableStateFlow<Map<String, DownloadState>>(emptyMap())
    val downloadStates: StateFlow<Map<String, DownloadState>> = _downloadStates

    private val downloadJobs = mutableMapOf<String, Job>()

    /* ----------------------------------------------------------- *//* OpenRouter – API key & base URL                            *//* ----------------------------------------------------------- */
    private val _openRouterApiKey = MutableStateFlow("")
    val openRouterApiKey: StateFlow<String> = _openRouterApiKey

    private val _openRouterBaseUrl = MutableStateFlow("https://openrouter.ai/api/v1")
    val openRouterBaseUrl: StateFlow<String> = _openRouterBaseUrl

    /* ----------------------------------------------------------- *//* OpenRouter – the models that are *already stored* in DB     *//* ----------------------------------------------------------- */
    private val _openRouterInstalledModels = MutableStateFlow<List<OpenRouterModel>>(emptyList())
    val openRouterInstalledModels: StateFlow<List<OpenRouterModel>> = _openRouterInstalledModels

    /* ----------------------------------------------------------- *//* OpenRouter – models that *can* be added (fetched from API) *//* ----------------------------------------------------------- */
    private val _availableModels = MutableStateFlow<List<OpenRouterModel>>(emptyList())
    val availableModels: StateFlow<List<OpenRouterModel>> = _availableModels

    init {
        observeModels()
    }

    /* --------------------------------------------------------------------------- *//* 1️⃣  Observe the DB – keep the whole list up‑to‑date                       *//* --------------------------------------------------------------------------- */
    private fun observeModels() = viewModelScope.launch(Dispatchers.IO) {
        ModelManager.observeModels().collectLatest { modelList ->
            _models.value = modelList

            // Derive the *installed* OpenRouter models from the DB
            _openRouterInstalledModels.value =
                modelList.filter { it.providerName == ModelProvider.OpenRouter.toString() }
                    .map { it.toOpenRouterModel() }
        }
    }

    /* ----------------------------------------------------------- *//* OpenRouter URL config (persisted by UserPrefs)             *//* ----------------------------------------------------------- */
    fun initOpenRouter(context: Context) {
        viewModelScope.launch { loadOpenRouterConfig(context) }

        viewModelScope.launch(Dispatchers.IO) {
            UserPrefs.getOpenRouterApiKey(context).collectLatest { key ->
                _openRouterApiKey.value = key
                if (key.isNotBlank() && _availableModels.value.isEmpty()) fetchAvailableModels()
            }
        }
    }

    private fun loadOpenRouterConfig(context: Context) = viewModelScope.launch {
        UserPrefs.getOpenRouterApiKey(context).collectLatest { _openRouterApiKey.value = it }
        UserPrefs.getOpenRouterBaseUrl(context).collectLatest { _openRouterBaseUrl.value = it }
    }

    fun saveOpenRouterApiKey(context: Context, key: String) {
        viewModelScope.launch {
            UserPrefs.setOpenRouterApiKey(context, key)
            _openRouterApiKey.value = key
            initOpenRouterFromPrefs(context)
            if (key.isNotBlank()) fetchAvailableModels()
        }
    }

    fun saveOpenRouterBaseUrl(context: Context, url: String) = viewModelScope.launch {
        UserPrefs.setOpenRouterBaseUrl(
            context,
            url
        ); _openRouterBaseUrl.value = url
    }

    /* ----------------------------------------------------------- *//* 2️⃣  OpenRouter – add/remove *installed* models              *//* ----------------------------------------------------------- */
    fun addOpenRouterModel(model: OpenRouterModel) {
        // Avoid duplicates in memory and DB
        if (model.id in _openRouterInstalledModels.value.map { it.id }) return

        _openRouterInstalledModels.update { it + model }

        viewModelScope.launch {
            try {
                ModelManager.addModel(model.toModelData())
            } catch (e: Exception) {
                Log.e("OpenRouter", "Persisting $model failed: ${e.message}")
            }
        }
    }

    fun removeOpenRouterModel(modelId: String) {
        _openRouterInstalledModels.update { it -> it.filter { it.id != modelId } }

        viewModelScope.launch {
            try {
                ModelManager.removeModel(modelId)
            } catch (e: Exception) {
                Log.e("OpenRouter", "Removing $modelId failed: ${e.message}")
            }
        }
    }

    /* ----------------------------------------------------------- *//* 3️⃣  Fetch *available* OpenRouter models from API           *//* ----------------------------------------------------------- */
    fun fetchAvailableModels() = viewModelScope.launch(Dispatchers.IO) {
        try {
            val apiKey = _openRouterApiKey.value
            if (apiKey.isBlank()) {
                Log.w("OpenRouter", "API key missing")
                return@launch
            }

            val url = "${_openRouterBaseUrl.value}/models"
            val conn = URL(url).openConnection() as HttpURLConnection
            conn.apply {
                requestMethod = "GET"
                setRequestProperty("Authorization", "Bearer $apiKey")
                setRequestProperty("Content-Type", "application/json")
                connectTimeout = 15000
                readTimeout = 15000
            }

            if (conn.responseCode != 200) {
                val err = conn.errorStream?.bufferedReader()?.readText() ?: "Unknown error"
                Log.e("OpenRouter", "Fetch error ${conn.responseCode}: $err")
                return@launch
            }

            val resp = conn.inputStream.bufferedReader().use { it.readText() }
            conn.disconnect()

            val data = JSONObject(resp).getJSONArray("data")
            val models = (0 until data.length()).mapNotNull { i ->
                try {
                    val m = data.getJSONObject(i)
                    val default = m.optJSONObject("default_parameters") ?: JSONObject()
                    OpenRouterModel(
                        id = m.getString("id"),
                        name = m.getString("name"),
                        ctxSize = m.getInt("context_length"),
                        temperature = default.optDouble("temperature", 0.7).toFloat(),
                        topP = default.optDouble("top_p", 0.9).toFloat()
                    )
                } catch (e: Exception) {
                    Log.w("OpenRouter", "Skipping bad entry $i – ${e.message}")
                    null
                }
            }.distinctBy { it.id }

            _availableModels.value = models
            Log.i("OpenRouter", "Fetched ${models.size} models")
        } catch (e: Exception) {
            Log.e("OpenRouter", "Exception while fetching: ${e.message}")
        }
    }

    /* ----------------------------------------------------------- *//* 4️⃣  GGUF download helpers                                 *//* ----------------------------------------------------------- */
    fun startDownload(modelData: ModelData, context: Context) {
        if (downloadJobs.containsKey(modelData.modelName)) return

        _downloadStates.update { it + (modelData.modelUrl!! to DownloadState(isDownloading = true)) }

        val job = viewModelScope.launch(Dispatchers.IO) {
            try {
                val dir = File(context.filesDir, "models").also { it.mkdirs() }
                downloadFile(
                    fileUrl = modelData.modelUrl!!,
                    outputFile = File(modelData.modelPath),
                    onProgress = { p ->
                        _downloadStates.update {
                            it + (modelData.modelUrl!! to it.getValue(modelData.modelUrl!!)
                                .copy(progress = p))
                        }
                    },
                    onComplete = {
                        val local = modelData.copy(
                            modelUrl = dir.resolve(modelData.modelName).absolutePath,
                            isImported = false
                        )
                        addModel(local)          // persist model to DB
                        _downloadStates.update {
                            it + (modelData.modelUrl!! to it.getValue(modelData.modelUrl!!)
                                .copy(isDownloading = false, isComplete = true))
                        }
                        downloadJobs.remove(modelData.modelName)
                    },
                    onError = { e ->
                        _downloadStates.update {
                            it + (modelData.modelUrl!! to it.getValue(modelData.modelUrl!!)
                                .copy(isDownloading = false, errorMessage = e.message))
                        }
                        downloadJobs.remove(modelData.modelName)
                    })
            } catch (e: Exception) {
                _downloadStates.update {
                    it + (modelData.modelUrl!! to it.getValue(modelData.modelUrl!!)
                        .copy(isDownloading = false, errorMessage = e.message))
                }
                downloadJobs.remove(modelData.modelName)
            }
        }
        downloadJobs[modelData.modelName] = job
    }

    fun cancelDownload(name: String, url: String) {
        downloadJobs[name]?.cancel()
        downloadJobs.remove(name)
        File(url).delete()
        _downloadStates.update { it + (url to DownloadState(isDownloading = false)) }
    }

    /* ----------------------------------------------------------- *//* 5️⃣  Persist a ModelData to DB (used for local GGUF)      *//* ----------------------------------------------------------- */
    fun addModel(model: ModelData) = viewModelScope.launch { ModelManager.addModel(model) }
    fun removeModel(name: String) = viewModelScope.launch {
        val model = ModelManager.getModel(name)

        if (model != null) {
            val isLocalGGUF = model.providerName == ModelProvider.LocalGGUF.toString()
            val isImported = model.isImported

            // Delete only if: LocalGGUF + not imported
            if (isLocalGGUF && !isImported) {
                model.modelUrl?.let { path ->
                    try {
                        val file = File(path)
                        if (file.exists()) {
                            file.delete()
                            Log.i("ModelRemove", "Deleted file for model: ${model.modelName}")
                        } else {
                            Log.w("ModelRemove", "File missing for ${model.modelName}")
                        }
                    } catch (e: Exception) {
                        Log.e("ModelRemove", "File deletion failed: ${e.message}")
                    }
                } ?: Log.w("ModelRemove", "Null modelUrl for ${model.modelName}")
            } else {
                Log.i("ModelRemove", "Skipping delete for imported or non-local model: ${model.modelName}")
            }

            ModelManager.removeModel(name)
        } else {
            Log.w("ModelRemove", "Model not found in DB: $name")
        }
    }


    /* --------------------------------------------------------------------------- *//* Helper – convert a ModelData that already holds an OpenRouter entry to an      *//*        OpenRouterModel object that's suitable for the UI.                     *//* --------------------------------------------------------------------------- */
    private fun ModelData.toOpenRouterModel() = OpenRouterModel(id, modelName, ctxSize, temp, topP)

    // ---------------------------------------------------------------------------
// 6️⃣  Helper – check if a model is persisted in the DB
// ---------------------------------------------------------------------------
    /**
     * Queries the database to see whether a model with the supplied [modelName] already
     * exists in the Room table.
     *
     * The result is returned asynchronously via the [onResult] callback,
     * which the UI layer uses to update the “isInstalled” state.
     */
    fun checkIfInstalled(modelName: String, onResult: (Boolean) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            // `ModelManager.checkIfInstalled` performs the necessary DAO query.
            val exists = ModelManager.checkIfInstalled(modelName)
            onResult(exists)
        }
    }
}