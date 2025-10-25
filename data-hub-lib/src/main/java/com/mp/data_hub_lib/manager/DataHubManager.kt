package com.mp.data_hub_lib.manager

import android.adservices.ondevicepersonalization.ModelManager
import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
import com.mp.ai_core.EmbeddingManager
import com.mp.ai_core.NativeLib
import com.mp.data_hub_lib.BuildConfig
import com.mp.data_hub_lib.model.DataSetModel
import com.mp.data_hub_lib.model.Doc
import com.mp.data_hub_lib.model.GenerationStats
import com.mp.data_hub_lib.model.RagResult
import com.mp.data_hub_lib.worker.BrainDecoder
import com.mp.data_hub_lib.worker.DataHubWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean

/**
 * DataHubManager with proper model instance separation and thread safety
 */
@SuppressLint("StaticFieldLeak")
object DataHubManager {
    private const val TAG = "DataHubManager"

    // Configuration
    var embeddingModelPath = ""

    // Core components - properly separated
    private var dataHubWorker: DataHubWorker? = null
    private val embeddingManager: EmbeddingManager = EmbeddingManager.getInstance()

    // Thread safety
    private val initMutex = Mutex()
    private val ragMutex = Mutex()
    private val isInitialized = AtomicBoolean(false)
    private val isEmbeddingInitialized = AtomicBoolean(false)

    // Coroutine management
    private val supervisorJob = SupervisorJob()
    val scope: CoroutineScope = CoroutineScope(Dispatchers.IO + supervisorJob)

    // State management
    private val _installedDataSets = MutableStateFlow<List<DataSetModel>>(emptyList())
    val installedDataSets: StateFlow<List<DataSetModel>> = _installedDataSets.asStateFlow()

    private val _currentDataSet = MutableStateFlow<DataSetModel?>(null)
    val currentDataSet: StateFlow<DataSetModel?> = _currentDataSet.asStateFlow()

    private val _ragStatus = MutableStateFlow<RAGStatus>(RAGStatus.IDLE)
    val ragStatus: StateFlow<RAGStatus> = _ragStatus.asStateFlow()

    enum class RAGStatus {
        IDLE, INITIALIZING, READY, SEARCHING, ERROR
    }

    /**
     * Initialize DataHubManager with proper resource management
     */
    fun init(context: Context) {
        val file = File(context.filesDir, "embedding.gguf")
        embeddingModelPath = file.absolutePath

        if (!file.exists()) {
            installEmbeddingModelFromAssets(context, "embedding.gguf", file)
        }

        if (isInitialized.getAndSet(true)) {
            Log.d(TAG, "Already initialized")
            return
        }

        scope.launch {
            initMutex.withLock {
                try {
                    Log.d(TAG, "Initializing DataHubManager...")

                    // Initialize worker
                    dataHubWorker = DataHubWorker(context.applicationContext)

                    // Load installed datasets
                    loadInstalledDatasets()

                    Log.i(TAG, "DataHubManager initialized successfully")

                } catch (e: Exception) {
                    Log.e(TAG, "Failed to initialize DataHubManager", e)
                    isInitialized.set(false)
                }
            }
        }
    }

    /**
     * Initialize the embedding model with dedicated instance
     */
    private suspend fun initializeEmbeddingModel() {
        if (isEmbeddingInitialized.get()) {
            Log.d(TAG, "Embedding model already initialized")
            return
        }

        try {
            _ragStatus.value = RAGStatus.INITIALIZING
            Log.d(TAG, "Initializing embedding model...")

            embeddingManager.initializeEmbedding(embeddingModelPath).fold(
                onSuccess = {
                    isEmbeddingInitialized.set(true)
                    _ragStatus.value = RAGStatus.READY
                    Log.i(TAG, "Embedding model initialized successfully")
                },
                onFailure = { error ->
                    _ragStatus.value = RAGStatus.ERROR
                    Log.e(TAG, "Failed to initialize embedding model: ${error.message}")
                    throw error
                }
            )
        } catch (e: Exception) {
            _ragStatus.value = RAGStatus.ERROR
            isEmbeddingInitialized.set(false)
            Log.e(TAG, "Embedding model initialization failed", e)
        }
    }

    /**
     * Load installed datasets from database
     */
    private suspend fun loadInstalledDatasets() {
        try {
            dataHubWorker?.getAllModels()?.collect { models ->
                _installedDataSets.value = models
                Log.d(TAG, "Loaded ${models.size} datasets")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load installed datasets", e)
        }
    }

    /**
     * Install a dataset pack
     */
    fun installPack(packFile: File, password: String, onResult: (Boolean) -> Unit) {
        val worker = dataHubWorker
        if (worker == null) {
            Log.e(TAG, "DataHubWorker not initialized")
            onResult(false)
            return
        }

        scope.launch {
            try {
                worker.installDataPack(packFile, password) { success ->
                    if (success) {
                        // Refresh dataset list
                        scope.launch { loadInstalledDatasets() }
                    }
                    onResult(success)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to install pack", e)
                onResult(false)
            }
        }
    }

    /**
     * Load a dataset pack
     */
    fun loadPack(model: DataSetModel, password: String, onResult: (Boolean, String?) -> Unit) {
        val worker = dataHubWorker
        if (worker == null) {
            Log.e(TAG, "DataHubWorker not initialized")
            onResult(false, "DataHubWorker not initialized")
            return
        }

        scope.launch {
            try {
                worker.loadPack(model.modelPath, password) { success, error ->
                    if (success) {
                        try {
                            // Load brain data
                            val jsonData = worker.dataNativeLib?.getEntity("D")
                            if (!jsonData.isNullOrBlank()) {
                                BrainDecoder.loadBrain(jsonData)
                                Log.d(TAG, "Brain data loaded successfully")
                            } else {
                                Log.w(TAG, "No brain data found for dataset")
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed to load brain data", e)
                        }
                    }
                    onResult(success, error)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load pack", e)
                onResult(false, "Exception: ${e.message}")
            }
        }
    }

    /**
     * Set current dataset with proper initialization
     */
    fun setCurrentDataSet(model: DataSetModel, onResult: (Boolean) -> Unit) {
        Log.i(TAG, "Switching dataset → ${model.modelName}")

        scope.launch {
            try {
                loadPack(model, BuildConfig.ALIAS) { success, error ->
                    if (success) {
                        _currentDataSet.value = model
                        Log.d(TAG, "Dataset switched successfully")
                    } else {
                        Log.e(TAG, "Failed to switch dataset: $error")
                    }
                    onResult(success)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error switching dataset", e)
                onResult(false)
            }
        }
    }

    /**
     * Clear current dataset
     */
    fun clearCurrentDataSet() {
        _currentDataSet.value = null
        Log.d(TAG, "Current dataset cleared")
    }

    /**
     * Generate embedding for query using dedicated embedding instance
     */
    private suspend fun generateQueryEmbedding(query: String): Result<FloatArray> {
        if (!isEmbeddingInitialized.get()) {
            Log.w(TAG, "Embedding model not initialized, attempting initialization...")
            initializeEmbeddingModel()

            if (!isEmbeddingInitialized.get()) {
                return Result.failure(Exception("Failed to initialize embedding model"))
            }
        }

        return try {
            embeddingManager.getEmbedding(query).also { result ->
                result.onSuccess {
                    Log.d(TAG, "Query embedding generated successfully (${it.size} dimensions)")
                }.onFailure { error ->
                    Log.e(TAG, "Failed to generate query embedding: ${error.message}")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception generating query embedding", e)
            Result.failure(e)
        }
    }


    /**
     * Search documents using embedding
     */
    private suspend fun searchDocuments(queryEmbedding: FloatArray, topK: Int): List<Doc> {
        return try {
            val results = BrainDecoder.search(queryEmbedding, topK)
            Log.d(TAG, "Document search completed: ${results.size} results")
            results
        } catch (e: Exception) {
            Log.e(TAG, "Document search failed", e)
            emptyList()
        }
    }

    /**
     * Uninstall a dataset by name
     */
    fun uninstallPack(modelName: String, onResult: (Boolean) -> Unit = {}) {
        val worker = dataHubWorker
        if (worker == null) {
            Log.e(TAG, "DataHubWorker not initialized")
            onResult(false)
            return
        }

        scope.launch {
            try {
                worker.deleteModel(modelName) { success ->
                    if (success) {
                        // Refresh dataset list from DB
                        scope.launch { loadInstalledDatasets() }

                        // Clear current dataset if it matches
                        _currentDataSet.value?.let { current ->
                            if (current.modelName == modelName) {
                                clearCurrentDataSet()
                            }
                        }

                        Log.i(TAG, "Uninstalled dataset: $modelName")
                    } else {
                        Log.w(TAG, "No dataset found to uninstall: $modelName")
                    }
                    onResult(success)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to uninstall pack $modelName", e)
                onResult(false)
            }
        }
    }


    /**
     * Main RAG execution with proper error handling and thread safety
     */
    fun runRAG(
        query: String,
        topK: Int = 5,
        callback: (RagResult?, String?) -> Unit
    ) {
        if (query.isBlank()) {
            Log.w(TAG, "RAG aborted: Empty query")
            callback(null, "Empty query")
            return
        }

        scope.launch(Dispatchers.IO) {
            ragMutex.withLock {
                try {

                    Log.w("RAG", "RAG not ready, reinitializing embedding")
                    reinitializeEmbeddingModel()

                    _ragStatus.value = RAGStatus.SEARCHING
                    Log.i(TAG, "RAG started with query: '$query'")
                    val startTime = System.currentTimeMillis()

                    // Check if we have a current dataset
                    val currentDataset = _currentDataSet.value
                    if (currentDataset == null) {
                        Log.w(TAG, "No dataset selected for RAG")
                        withContext(Dispatchers.Main) {
                            callback(null, "No dataset selected")
                        }
                        return@withLock
                    }

                    // Step 1: Generate embedding
                    Log.d(TAG, "Generating embedding for query...")
                    val embeddingResult = generateQueryEmbedding(query)

                    val queryEmbedding = embeddingResult.getOrElse { error ->
                        Log.e(TAG, "Failed to generate embedding: ${error.message}")
                        _ragStatus.value = RAGStatus.ERROR
                        withContext(Dispatchers.Main) {
                            callback(null, "Failed to generate embedding: ${error.message}")
                        }
                        return@withLock
                    }

                    // Step 2: Search documents
                    Log.d(TAG, "Searching top $topK documents...")
                    val documents = searchDocuments(queryEmbedding, topK)

                    if (documents.isEmpty()) {
                        Log.w(TAG, "No documents found for query")
                        _ragStatus.value = RAGStatus.READY
                        withContext(Dispatchers.Main) {
                            callback(null, "No documents found")
                        }
                        return@withLock
                    }

                    // Step 3: Build results
                    val totalTime = System.currentTimeMillis() - startTime
                    val stats = GenerationStats(
                        tokenCount = documents.size,
                        totalTime = totalTime,
                        tokensPerSecond = if (totalTime > 0) {
                            documents.size * 1000f / totalTime
                        } else 0f
                    )

                    val ragResult = RagResult(docs = documents, stats = stats)

                    Log.i(
                        TAG,
                        "RAG completed in ${totalTime}ms (≈${"%.2f".format(stats.tokensPerSecond)} docs/s)"
                    )
                    _ragStatus.value = RAGStatus.READY

                    withContext(Dispatchers.Main) {
                        callback(ragResult, null)
                    }

                } catch (e: Exception) {
                    Log.e(TAG, "RAG execution failed", e)
                    _ragStatus.value = RAGStatus.ERROR
                    withContext(Dispatchers.Main) {
                        callback(null, "RAG execution failed: ${e.message}")
                    }
                } finally {
                    // Ensure we don't stay in SEARCHING state
                    if (_ragStatus.value == RAGStatus.SEARCHING) {
                        _ragStatus.value = if (isEmbeddingInitialized.get()) {
                            RAGStatus.READY
                        } else {
                            RAGStatus.ERROR
                        }
                    }
                }
            }
        }
    }

    /**
     * Check if RAG is ready for use
     */
    fun isRAGReady(): Boolean {
        return isInitialized.get() &&
                isEmbeddingInitialized.get() &&
                _currentDataSet.value != null &&
                _ragStatus.value == RAGStatus.READY
    }

    /**
     * Get embedding model status
     */
    fun getEmbeddingModelStatus(): String {
        return when {
            !isInitialized.get() -> "Not initialized"
            !isEmbeddingInitialized.get() -> "Embedding model not ready"
            _currentDataSet.value == null -> "No dataset selected"
            else -> "Ready"
        }
    }

    /**
     * Force reinitialize embedding model
     */
    suspend fun reinitializeEmbeddingModel(): Result<Unit> {
        return try {
            isEmbeddingInitialized.set(false)
            initializeEmbeddingModel()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    @Deprecated(
        "Use searchDocuments directly",
        ReplaceWith("searchDocuments(queryEmbedding, topK)")
    )
    fun search(queryEmbedding: FloatArray, topK: Int = 5): List<Doc> {
        return try {
            BrainDecoder.search(queryEmbedding, topK)
        } catch (e: Exception) {
            Log.e(TAG, "Legacy search method failed", e)
            emptyList()
        }
    }

    // Accessor methods - these now properly manage instance separation
    fun getNative(): NativeLib? {
        Log.w(
            TAG,
            "getNative() is deprecated - use separate instances for generation and embedding"
        )
        return null // Don't expose the native instance to prevent misuse
    }

    fun getWorker(): DataHubWorker? = dataHubWorker

    fun getEmbeddingManager(): EmbeddingManager = embeddingManager

    /**
     * Cleanup resources
     */
    fun shutdown() {
        try {
            supervisorJob.cancel()
            _currentDataSet.value = null
            _installedDataSets.value = emptyList()
            isInitialized.set(false)
            isEmbeddingInitialized.set(false)
            _ragStatus.value = RAGStatus.IDLE
            Log.d(TAG, "DataHubManager shutdown completed")
        } catch (e: Exception) {
            Log.e(TAG, "Error during shutdown", e)
        }
    }

    private fun installEmbeddingModelFromAssets(ctx: Context, assetName: String, out: File): File {
        return try {
            ctx.assets.open(assetName).use { input ->
                out.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            out
        } catch (t: Throwable) {
            // Clean partial files
            runCatching { out.delete() }
            throw IOException("Failed to copy asset $assetName", t)
        }
    }
}