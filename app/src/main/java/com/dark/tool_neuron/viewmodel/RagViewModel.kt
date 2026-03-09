package com.dark.tool_neuron.viewmodel

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.dark.tool_neuron.engine.EmbeddingConfig
import com.dark.tool_neuron.engine.EmbeddingEngine
import com.dark.tool_neuron.models.table_schema.InstalledRag
import com.dark.tool_neuron.models.table_schema.RagStatus
import com.dark.tool_neuron.neuron_example.GraphSettings
import com.dark.tool_neuron.neuron_example.NeuronGraph
import com.dark.tool_neuron.neuron_example.RetrievalConfidence
import com.dark.tool_neuron.repo.RagRepository
import com.dark.tool_neuron.worker.LlmModelWorker
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File

// Data class for displaying RAG query results in UI
data class RagQueryDisplayResult(
    val ragName: String,
    val content: String,
    val score: Float,
    val nodeId: String
)

@HiltViewModel
class RagViewModel @Inject constructor(
    private val ragRepository: RagRepository,
    private val embeddingEngine: EmbeddingEngine,
    @param:ApplicationContext private val context: Context
) : ViewModel() {

    // UI State
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    private val _embeddingStatus = MutableStateFlow("Not Initialized")
    val embeddingStatus: StateFlow<String> = _embeddingStatus

    private val _isEmbeddingInitialized = MutableStateFlow(false)
    val isEmbeddingInitialized: StateFlow<Boolean> = _isEmbeddingInitialized

    private val _isEmbeddingModelDownloading = MutableStateFlow(false)
    val isEmbeddingModelDownloading: StateFlow<Boolean> = _isEmbeddingModelDownloading

    private val _isEmbeddingModelDownloaded = MutableStateFlow(false)
    val isEmbeddingModelDownloaded: StateFlow<Boolean> = _isEmbeddingModelDownloaded

    private val _embeddingDownloadProgress = MutableStateFlow(0f)
    val embeddingDownloadProgress: StateFlow<Float> = _embeddingDownloadProgress

    // RAG Lists
    val installedRags = ragRepository.getAllRags()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val loadedRags = ragRepository.getLoadedRags()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Counts
    private val _installedCount = MutableStateFlow(0)
    val installedCount: StateFlow<Int> = _installedCount

    private val _loadedCount = MutableStateFlow(0)
    val loadedCount: StateFlow<Int> = _loadedCount

    // Embedding state
    val isEmbeddingReady: Boolean get() = embeddingEngine.isInitialized()

    // RAG enabled for chat
    private val _isRagEnabledForChat = MutableStateFlow(false)
    val isRagEnabledForChat: StateFlow<Boolean> = _isRagEnabledForChat

    // Last RAG query results for display
    private val _lastRagResults = MutableStateFlow<List<RagQueryDisplayResult>>(emptyList())
    val lastRagResults: StateFlow<List<RagQueryDisplayResult>> = _lastRagResults

    init {
        // Sync database state with in-memory state on startup
        // Since loadedGraphs is empty on app restart, mark all RAGs as unloaded
        viewModelScope.launch(Dispatchers.IO) {
            ragRepository.syncLoadedStateOnStartup()
        }

        refreshCounts()
        _isEmbeddingModelDownloaded.value = EmbeddingEngine.isModelDownloaded(context)
        _isEmbeddingInitialized.value = embeddingEngine.isInitialized()
        if (embeddingEngine.isInitialized()) {
            _embeddingStatus.value = "Ready (dim: ${embeddingEngine.getDimension()})"
        }

        // Monitor embedding model download status
        checkEmbeddingDownloadStatus()
    }

    private fun checkEmbeddingDownloadStatus() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val workManager = WorkManager.getInstance(context)
                val workInfos = workManager.getWorkInfosByTag(
                    com.dark.tool_neuron.worker.EmbeddingModelDownloadWorker.TAG
                ).get()

                val activeWork = workInfos.any {
                    it.state == WorkInfo.State.RUNNING || it.state == WorkInfo.State.ENQUEUED
                }

                if (activeWork) {
                    _isEmbeddingModelDownloading.value = true
                    _embeddingStatus.value = "Downloading embedding model..."
                    // Start polling for completion
                    pollDownloadCompletion()
                } else {
                    _isEmbeddingModelDownloading.value = false
                }
            } catch (e: Exception) {
                Log.e("RagViewModel", "Failed to check download status", e)
            }
        }
    }

    fun startEmbeddingDownload() {
        viewModelScope.launch(Dispatchers.IO) {
            if (_isEmbeddingModelDownloading.value) return@launch
            if (EmbeddingEngine.isModelDownloaded(context)) {
                _isEmbeddingModelDownloaded.value = true
                initializeEmbeddingFromFiles()
                return@launch
            }

            _isEmbeddingModelDownloading.value = true
            _embeddingDownloadProgress.value = 0f
            _embeddingStatus.value = "Starting download..."

            LlmModelWorker.startEmbeddingModelDownload(context)
            pollDownloadCompletion()
        }
    }

    private fun pollDownloadCompletion() {
        viewModelScope.launch(Dispatchers.IO) {
            val modelFile = EmbeddingEngine.getModelPath(context)

            while (_isEmbeddingModelDownloading.value) {
                delay(1500)

                // Check if file appeared (download complete)
                if (modelFile.exists() && modelFile.length() > 0) {
                    _isEmbeddingModelDownloaded.value = true
                    _isEmbeddingModelDownloading.value = false
                    _embeddingDownloadProgress.value = 1f
                    _embeddingStatus.value = "Download complete"
                    // Auto-initialize after download
                    initializeEmbeddingFromFiles()
                    return@launch
                }

                // Check WorkManager state
                try {
                    val workManager = WorkManager.getInstance(context)
                    val workInfos = workManager.getWorkInfosByTag(
                        com.dark.tool_neuron.worker.EmbeddingModelDownloadWorker.TAG
                    ).get()

                    val running = workInfos.any {
                        it.state == WorkInfo.State.RUNNING || it.state == WorkInfo.State.ENQUEUED
                    }
                    val failed = workInfos.any { it.state == WorkInfo.State.FAILED }

                    if (failed) {
                        _isEmbeddingModelDownloading.value = false
                        _embeddingDownloadProgress.value = 0f
                        _embeddingStatus.value = "Download failed"
                        _error.value = "Embedding model download failed. Tap to retry."
                        return@launch
                    }

                    if (!running) {
                        // Work finished — check if file exists
                        if (modelFile.exists() && modelFile.length() > 0) {
                            _isEmbeddingModelDownloaded.value = true
                            _isEmbeddingModelDownloading.value = false
                            _embeddingDownloadProgress.value = 1f
                            _embeddingStatus.value = "Download complete"
                            initializeEmbeddingFromFiles()
                        } else {
                            _isEmbeddingModelDownloading.value = false
                            _embeddingDownloadProgress.value = 0f
                            _embeddingStatus.value = "Download failed"
                        }
                        return@launch
                    }

                    // Estimate progress from partial file size (~23MB model)
                    val partialFile = modelFile
                    if (partialFile.exists()) {
                        val estimatedSize = 23_000_000L
                        val progress = (partialFile.length().toFloat() / estimatedSize).coerceIn(0f, 0.99f)
                        _embeddingDownloadProgress.value = progress
                        _embeddingStatus.value = "Downloading... ${(progress * 100).toInt()}%"
                    }
                } catch (e: Exception) {
                    Log.e("RagViewModel", "Error polling download status", e)
                }
            }
        }
    }

    private fun refreshCounts() {
        viewModelScope.launch(Dispatchers.IO) {
            _installedCount.value = ragRepository.getRagCount()
            _loadedCount.value = ragRepository.getLoadedRagCount()
        }
    }

    // ==================== UI Controls ====================

    fun clearError() {
        _error.value = null
    }

    // ==================== RAG Operations ====================

    fun toggleRagEnabled(ragId: String, isEnabled: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            ragRepository.updateRagEnabled(ragId, isEnabled)
        }
    }

    fun loadRag(ragId: String, password: String? = null) {
        viewModelScope.launch(Dispatchers.IO) {
            _isLoading.value = true
            _error.value = null

            try {
                // Initialize embedding engine if not already initialized
                if (!embeddingEngine.isInitialized()) {
                    Log.d("RagViewModel", "Embedding engine not initialized, initializing now...")
                    val modelFile = com.dark.tool_neuron.engine.EmbeddingEngine.getModelPath(context)

                    if (!modelFile.exists()) {
                        checkEmbeddingDownloadStatus()
                        if (!_isEmbeddingModelDownloading.value) {
                            // Auto-start download if not already downloading
                            Log.d("RagViewModel", "Embedding model missing, auto-starting download")
                            startEmbeddingDownload()
                        }
                        _error.value = "Embedding model is downloading. RAG will be available once complete."
                        _isLoading.value = false
                        ragRepository.updateRagStatus(ragId, RagStatus.INSTALLED)
                        return@launch
                    }

                    val config = EmbeddingConfig(modelPath = modelFile.absolutePath)
                    val initResult = embeddingEngine.initialize(config)

                    if (initResult.isFailure) {
                        _error.value = "Failed to initialize embedding engine: ${initResult.exceptionOrNull()?.message}"
                        _isLoading.value = false
                        ragRepository.updateRagStatus(ragId, RagStatus.ERROR)
                        return@launch
                    }

                    Log.d("RagViewModel", "Embedding engine initialized successfully")
                }

                ragRepository.updateRagStatus(ragId, RagStatus.LOADING)

                val graph = NeuronGraph(embeddingEngine, GraphSettings.DEFAULT)
                val result = ragRepository.loadGraph(ragId, graph, password)

                if (result.isSuccess) {
                    _loadedCount.value = ragRepository.getLoadedRagCount()
                    _isRagEnabledForChat.value = true
                    Log.d("RagViewModel", "RAG loaded successfully, total loaded: ${_loadedCount.value}")
                } else {
                    Log.e("RagViewModel", "Error loading RAG: ${result.exceptionOrNull()?.message}")
                    ragRepository.updateRagStatus(ragId, RagStatus.ERROR)
                    _error.value = result.exceptionOrNull()?.message ?: "Failed to load RAG"
                }
            } catch (e: Exception) {
                ragRepository.updateRagStatus(ragId, RagStatus.ERROR)
                Log.e("RagViewModel", "Error loading RAG: ${e.message}")
                _error.value = e.message
            }

            _isLoading.value = false
        }
    }

    fun unloadRag(ragId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            ragRepository.unloadGraph(ragId)
            val remaining = ragRepository.getLoadedRagCount()
            _loadedCount.value = remaining
            if (remaining == 0) _isRagEnabledForChat.value = false
        }
    }

    fun deleteRag(ragId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            ragRepository.deleteRag(ragId)
            refreshCounts()
        }
    }

    fun installRagFromUri(uri: Uri, name: String? = null) {
        viewModelScope.launch(Dispatchers.IO) {
            _isLoading.value = true
            _error.value = null

            val result = ragRepository.installRagFromUri(uri, name)
            if (result.isFailure) {
                _error.value = result.exceptionOrNull()?.message ?: "Failed to install RAG"
            } else {
                refreshCounts()
            }

            _isLoading.value = false
        }
    }

    // ==================== Creation Operations ====================

    fun createRagFromText(
        name: String,
        description: String,
        text: String,
        domain: String = "general",
        tags: List<String> = emptyList(),
        onComplete: (Result<InstalledRag>) -> Unit
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            _isLoading.value = true
            _error.value = null

            // Auto-initialize embedding engine if needed
            if (!embeddingEngine.isInitialized()) {
                val modelFile = EmbeddingEngine.getModelPath(context)
                if (!modelFile.exists()) {
                    _error.value = "Embedding model not found"
                    _isLoading.value = false
                    onComplete(Result.failure(Exception("Embedding model not found")))
                    return@launch
                }

                val config = EmbeddingConfig(modelPath = modelFile.absolutePath)
                val initResult = embeddingEngine.initialize(config)
                if (initResult.isFailure) {
                    _error.value = "Failed to initialize embedding engine"
                    _isLoading.value = false
                    onComplete(Result.failure(initResult.exceptionOrNull() ?: Exception("Failed to initialize embedding engine")))
                    return@launch
                }
            }

            val graph = NeuronGraph(embeddingEngine, GraphSettings.DEFAULT)
            val result = ragRepository.createRagFromText(name, description, text, graph, domain, tags)

            if (result.isFailure) {
                _error.value = result.exceptionOrNull()?.message ?: "Failed to create RAG"
            } else {
                refreshCounts()
            }

            _isLoading.value = false
            onComplete(result)
        }
    }

    fun createRagFromFile(
        name: String,
        description: String,
        fileUri: Uri,
        domain: String = "general",
        tags: List<String> = emptyList(),
        onComplete: (Result<InstalledRag>) -> Unit
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            _isLoading.value = true
            _error.value = null

            // Auto-initialize embedding engine if needed
            if (!embeddingEngine.isInitialized()) {
                val modelFile = EmbeddingEngine.getModelPath(context)
                if (!modelFile.exists()) {
                    _error.value = "Embedding model not found"
                    _isLoading.value = false
                    onComplete(Result.failure(Exception("Embedding model not found")))
                    return@launch
                }

                val config = EmbeddingConfig(modelPath = modelFile.absolutePath)
                val initResult = embeddingEngine.initialize(config)
                if (initResult.isFailure) {
                    _error.value = "Failed to initialize embedding engine"
                    _isLoading.value = false
                    onComplete(Result.failure(initResult.exceptionOrNull() ?: Exception("Failed to initialize embedding engine")))
                    return@launch
                }
            }

            val graph = NeuronGraph(embeddingEngine, GraphSettings.DEFAULT)
            val result = ragRepository.createRagFromFile(name, description, fileUri, graph, domain, tags)

            if (result.isFailure) {
                _error.value = result.exceptionOrNull()?.message ?: "Failed to create RAG"
            } else {
                refreshCounts()
            }

            _isLoading.value = false
            onComplete(result)
        }
    }

    // ==================== Embedding Initialization ====================

    fun initializeEmbeddingFromFiles() {
        viewModelScope.launch(Dispatchers.IO) {
            _embeddingStatus.value = "Checking models..."
            _isLoading.value = true

            val modelFile = EmbeddingEngine.getModelPath(context)

            if (modelFile.exists()) {
                val config = EmbeddingConfig(
                    modelPath = modelFile.absolutePath
                )

                val result = embeddingEngine.initialize(config)
                if (result.isSuccess) {
                    _isEmbeddingInitialized.value = true
                    _embeddingStatus.value = "Ready (dim: ${embeddingEngine.getDimension()})"
                } else {
                    _isEmbeddingInitialized.value = false
                    _embeddingStatus.value = "Error: ${result.exceptionOrNull()?.message}"
                    _error.value = result.exceptionOrNull()?.message
                }
            } else {
                _embeddingStatus.value = "Model not found - Please install embedding model"
                _error.value = "Embedding model files not found."
            }

            _isLoading.value = false
        }
    }

    // ==================== RAG for Chat Toggle ====================

    fun toggleRagForChat(enabled: Boolean) {
        _isRagEnabledForChat.value = enabled
    }

    // ==================== Query with Display Results ====================

    suspend fun queryAndStoreResults(query: String, topK: Int = 5): String {
        // Use the advanced retrieval pipeline
        val aggregated = ragRepository.queryAllLoadedGraphsWithPipeline(query, topK)

        if (aggregated.ragResults.isEmpty()) {
            Log.w("RagViewModel", "No RAG results found for query: $query")
            _lastRagResults.value = emptyList()
            return ""
        }

        // Store results for UI display
        val displayResults = mutableListOf<RagQueryDisplayResult>()
        for ((rag, retrievalResult) in aggregated.ragResults) {
            for (result in retrievalResult.results) {
                displayResults.add(
                    RagQueryDisplayResult(
                        ragName = rag.name,
                        content = result.node.content,
                        score = result.score,
                        nodeId = result.node.id
                    )
                )
            }
        }
        _lastRagResults.value = displayResults.sortedByDescending { it.score }

        // Build context with confidence-aware prefix
        val contextBuilder = StringBuilder()
        when (aggregated.overallConfidence) {
            RetrievalConfidence.HIGH -> {
                contextBuilder.append("### Relevant Knowledge:\n")
            }
            RetrievalConfidence.MEDIUM -> {
                contextBuilder.append("### Relevant Knowledge:\n")
            }
            RetrievalConfidence.LOW -> {
                contextBuilder.append("### Relevant Knowledge (uncertain — retrieved context may not fully answer the question):\n")
            }
        }
        contextBuilder.append(aggregated.combinedContext)

        Log.d("RagViewModel", "RAG context: ${contextBuilder.length} chars, ${displayResults.size} results, confidence=${aggregated.overallConfidence}")
        return contextBuilder.toString()
    }

    // ==================== Secure RAG Creation ====================

    fun createSecureRagFromText(
        name: String,
        description: String,
        text: String,
        domain: String = "general",
        tags: List<String> = emptyList(),
        adminPassword: String,
        readOnlyUsers: List<com.neuronpacket.UserCredentials> = emptyList(),
        loadingMode: com.neuronpacket.LoadingMode = com.neuronpacket.LoadingMode.EMBEDDED,
        onProgress: (Float, String) -> Unit,
        onComplete: (Result<InstalledRag>) -> Unit
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            _isLoading.value = true
            _error.value = null

            // Auto-initialize embedding engine if needed
            if (!embeddingEngine.isInitialized()) {
                onProgress(0.05f, "Initializing embedding engine...")
                val modelFile = EmbeddingEngine.getModelPath(context)

                if (!modelFile.exists()) {
                    _error.value = "Embedding model not found"
                    _isLoading.value = false
                    onComplete(Result.failure(Exception("Embedding model not found")))
                    return@launch
                }

                val config = EmbeddingConfig(modelPath = modelFile.absolutePath)
                val initResult = embeddingEngine.initialize(config)

                if (initResult.isFailure) {
                    _error.value = "Failed to initialize embedding engine"
                    _isLoading.value = false
                    onComplete(Result.failure(initResult.exceptionOrNull() ?: Exception("Failed to initialize embedding engine")))
                    return@launch
                }
            }

            val graph = NeuronGraph(embeddingEngine, GraphSettings.DEFAULT)
            val result = ragRepository.createSecureRagFromText(
                name, description, text, graph, domain, tags,
                adminPassword, readOnlyUsers, loadingMode, onProgress
            )

            if (result.isFailure) {
                _error.value = result.exceptionOrNull()?.message ?: "Failed to create secure RAG"
            } else {
                refreshCounts()
            }

            _isLoading.value = false
            onComplete(result)
        }
    }

    fun createSecureRagFromFile(
        name: String,
        description: String,
        fileUri: Uri,
        domain: String = "general",
        tags: List<String> = emptyList(),
        adminPassword: String,
        readOnlyUsers: List<com.neuronpacket.UserCredentials> = emptyList(),
        loadingMode: com.neuronpacket.LoadingMode = com.neuronpacket.LoadingMode.EMBEDDED,
        onProgress: (Float, String) -> Unit,
        onComplete: (Result<InstalledRag>) -> Unit
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            _isLoading.value = true
            _error.value = null

            // Auto-initialize embedding engine if needed
            if (!embeddingEngine.isInitialized()) {
                onProgress(0.05f, "Initializing embedding engine...")
                val modelFile = EmbeddingEngine.getModelPath(context)

                if (!modelFile.exists()) {
                    _error.value = "Embedding model not found"
                    _isLoading.value = false
                    onComplete(Result.failure(Exception("Embedding model not found")))
                    return@launch
                }

                val config = EmbeddingConfig(modelPath = modelFile.absolutePath)
                val initResult = embeddingEngine.initialize(config)

                if (initResult.isFailure) {
                    _error.value = "Failed to initialize embedding engine"
                    _isLoading.value = false
                    onComplete(Result.failure(initResult.exceptionOrNull() ?: Exception("Failed to initialize embedding engine")))
                    return@launch
                }
            }

            val graph = NeuronGraph(embeddingEngine, GraphSettings.DEFAULT)
            val result = ragRepository.createSecureRagFromFile(
                name, description, fileUri, graph, domain, tags,
                adminPassword, readOnlyUsers, loadingMode, onProgress
            )

            if (result.isFailure) {
                _error.value = result.exceptionOrNull()?.message ?: "Failed to create secure RAG"
            } else {
                refreshCounts()
            }

            _isLoading.value = false
            onComplete(result)
        }
    }

}