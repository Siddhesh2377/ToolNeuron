package com.mp.data_hub_lib.manager

import android.content.Context
import android.util.Log
import com.mp.ai_core.EmbeddingManager
import com.mp.ai_core.NativeLib
import com.mp.data_hub_lib.worker.BrainDecoder
import com.mp.data_hub_lib.worker.DataHubWorker
import com.mp.data_hub_lib.worker.Doc
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * DataHubManager – Single entry point to manage DataHubWorker, BrainDecoder, and Embedding.
 * Keeps everything initialized once and shared across the app.
 */
object DataHubManager {
    private var dataHubWorker: DataHubWorker? = null
    private var embeddingManager: EmbeddingManager? = null
    private var native: NativeLib? = null

    val appStatus = MutableStateFlow(Status.IDLE)

    enum class Status {
        IDLE, LOADING, READY, ERROR
    }

    fun init(context: Context) {
        if (dataHubWorker == null) {
            dataHubWorker = DataHubWorker(context.applicationContext)
        }
        if (native == null) {
            native = NativeLib()
        }
        if (embeddingManager == null && native != null) {
            embeddingManager = EmbeddingManager(native!!)
        }
        Log.i("DataHubManager", "Initialized core components")
    }

    fun loadPack(context: Context, packPath: String, password: String, onResult: (Boolean, String?) -> Unit) {
        val worker = dataHubWorker ?: DataHubWorker(context).also { dataHubWorker = it }
        appStatus.value = Status.LOADING
        worker.loadPack(packPath, password) { success, modelName ->
            if (success && modelName != null) {
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        val mJson = worker.dataNativeLib.getEntity("D")
                        val root = BrainDecoder.loadBrain(mJson)
                        if (root != null) {
                            appStatus.value = Status.READY
                            onResult(true, modelName)
                        } else {
                            appStatus.value = Status.ERROR
                            onResult(false, null)
                        }
                    } catch (e: Exception) {
                        Log.e("DataHubManager", "Error loading brain: ${e.message}")
                        appStatus.value = Status.ERROR
                        onResult(false, null)
                    }
                }
            } else {
                appStatus.value = Status.ERROR
                onResult(false, null)
            }
        }
    }

    suspend fun getQueryEmbedding(query: String, modelPath: String): FloatArray? {
        val manager = embeddingManager ?: return null
        val result = manager.initializeEmbedding(modelPath = modelPath)
        result.onFailure {
            Log.e("DataHubManager", "Embedding init failed: ${it.message}")
            return null
        }
        return manager.getEmbedding(query).getOrNull()
    }

    fun search(queryEmbedding: FloatArray, topK: Int = 5): List<Doc> {
        return BrainDecoder.search(queryEmbedding, topK)
    }

    fun getNative(): NativeLib? = native
    fun getWorker(): DataHubWorker? = dataHubWorker
    fun getEmbeddingManager(): EmbeddingManager? = embeddingManager
}
