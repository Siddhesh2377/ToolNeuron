package com.dark.tool_neuron.engine

import android.content.Context
import android.util.Log
import com.dark.gguf_lib.EmbeddingEngine as LibEmbeddingEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import com.dark.tool_neuron.global.AppPaths
import java.io.File

data class EmbeddingConfig(
    val modelPath: String,
    val threads: Int = 0,
    val contextSize: Int = 512,
    val normalize: Boolean = true
)

class EmbeddingEngine {
    private val libEngine = LibEmbeddingEngine()
    private var config: EmbeddingConfig? = null
    private var dimension: Int = 0
    private val initMutex = Mutex()

    companion object {
        private const val TAG = "EmbeddingEngine"

        fun getModelPath(context: Context): File {
            return AppPaths.embeddingModel(context)
        }

        fun isModelDownloaded(context: Context): Boolean {
            return getModelPath(context).exists()
        }
    }

    suspend fun initialize(config: EmbeddingConfig): Result<Unit> = initMutex.withLock {
        withContext(Dispatchers.IO) {
            try {
                if (isInitialized() && this@EmbeddingEngine.config?.modelPath == config.modelPath) {
                    Log.d(TAG, "Already initialized with same model")
                    return@withContext Result.success(Unit)
                }

                val modelFile = File(config.modelPath)
                if (!modelFile.exists()) {
                    Log.e(TAG, "Model file not found: ${config.modelPath}")
                    return@withContext Result.failure(Exception("Model file not found: ${config.modelPath}"))
                }

                if (!modelFile.canRead()) {
                    Log.e(TAG, "Model file not readable: ${config.modelPath}")
                    return@withContext Result.failure(Exception("Model file not readable: ${config.modelPath}"))
                }

                Log.d(TAG, "Loading embedding model: ${config.modelPath} (${modelFile.length() / 1024}KB)")

                val success = libEngine.load(
                    path = config.modelPath,
                    threads = config.threads,
                    contextSize = config.contextSize
                )

                if (!success) {
                    Log.e(TAG, "Native load returned false")
                    return@withContext Result.failure(Exception("Failed to load embedding model: native library returned false"))
                }

                Log.d(TAG, "Model loaded, running test embedding...")

                val testResult = embed("test")
                if (testResult == null || testResult.isEmpty()) {
                    Log.e(TAG, "Test embedding returned null/empty — releasing native handle")
                    libEngine.close()
                    return@withContext Result.failure(Exception("Test embedding generation failed or returned empty"))
                }
                dimension = testResult.size
                Log.d(TAG, "Embedding engine initialized: dimension=$dimension")

                this@EmbeddingEngine.config = config
                Result.success(Unit)
            } catch (e: Exception) {
                Log.e(TAG, "Initialization failed: ${e.message}", e)
                Result.failure(e)
            }
        }
    }

    suspend fun embed(text: String): FloatArray? {
        if (!isInitialized()) {
            Log.w(TAG, "embed() called before initialization")
            return null
        }
        return libEngine.embed(text, config?.normalize ?: true)
    }

    suspend fun embedBatch(texts: List<String>): List<FloatArray?> =
        libEngine.embedBatch(texts, config?.normalize ?: true)

    fun isInitialized(): Boolean = config != null && dimension > 0

    fun getDimension(): Int = dimension

    fun getModelName(): String = config?.modelPath?.substringAfterLast("/") ?: "unknown"

    fun close() {
        libEngine.close()
        config = null
        dimension = 0
    }
}
