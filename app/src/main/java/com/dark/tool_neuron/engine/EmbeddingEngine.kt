package com.dark.tool_neuron.engine

import android.content.Context
import android.util.Log
import com.mp.ai_gguf.GGUFNativeLib
import com.mp.ai_gguf.models.EmbeddingCallback
import com.mp.ai_gguf.models.EmbeddingResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

data class EmbeddingConfig(
    val modelPath: String,
    val threads: Int = 0,
    val contextSize: Int = 512,  // all-MiniLM-L6-v2 has max_position_embeddings=512
    val normalize: Boolean = true
)

class EmbeddingEngine {
    private val nativeLib = GGUFNativeLib()
    private var config: EmbeddingConfig? = null
    private var dimension: Int = 0
    private val initMutex = Mutex()

    companion object {
        private const val TAG = "EmbeddingEngine"
        private const val EMBED_TIMEOUT_MS = 15000L

        fun getModelPath(context: Context): File {
            return File(context.filesDir, "embedding_model/all-MiniLM-L6-v2-Q5_K_M.gguf")
        }

        fun isModelDownloaded(context: Context): Boolean {
            return getModelPath(context).exists()
        }
    }

    suspend fun initialize(config: EmbeddingConfig): Result<Unit> = initMutex.withLock {
        withContext(Dispatchers.IO) {
            try {
                // Already initialized with same config
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

                val success = nativeLib.nativeLoadEmbeddingModel(
                    path = config.modelPath,
                    threads = config.threads,
                    contextSize = config.contextSize
                )

                if (!success) {
                    Log.e(TAG, "Native nativeLoadEmbeddingModel returned false")
                    return@withContext Result.failure(Exception("Failed to load embedding model: native library returned false"))
                }

                Log.d(TAG, "Model loaded, running test embedding...")

                val testResult = embed("test")
                if (testResult == null || testResult.isEmpty()) {
                    Log.e(TAG, "Test embedding returned null/empty")
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

    suspend fun embed(text: String): FloatArray? = withContext(Dispatchers.IO) {
        val result = withTimeoutOrNull(EMBED_TIMEOUT_MS) {
            suspendCancellableCoroutine { continuation ->
                val resumed = AtomicBoolean(false)

                val callback = object : EmbeddingCallback {
                    override fun onComplete(result: EmbeddingResult) {
                        if (resumed.compareAndSet(false, true)) {
                            continuation.resume(result.embeddings)
                        } else {
                            Log.w(TAG, "Callback fired after continuation already resumed")
                        }
                    }

                    override fun onError(message: String) {
                        if (resumed.compareAndSet(false, true)) {
                            continuation.resumeWithException(Exception(message))
                        } else {
                            Log.w(TAG, "Error callback fired after continuation already resumed: $message")
                        }
                    }
                }

                val success = nativeLib.nativeEncodeText(
                    text = text,
                    normalize = config?.normalize ?: true,
                    callback = callback
                )

                if (!success) {
                    if (resumed.compareAndSet(false, true)) {
                        continuation.resumeWithException(Exception("Failed to start encoding - native call returned false"))
                    }
                }
            }
        }

        if (result == null) {
            Log.e(TAG, "Embedding timed out after ${EMBED_TIMEOUT_MS}ms for text: ${text.take(50)}")
        }
        result
    }

    suspend fun embedBatch(texts: List<String>): List<FloatArray?> = withContext(Dispatchers.IO) {
        texts.map { embed(it) }
    }

    fun isInitialized(): Boolean = config != null && dimension > 0

    fun getDimension(): Int = dimension

    fun getModelName(): String = config?.modelPath?.substringAfterLast("/") ?: "unknown"

    fun close() {
        nativeLib.nativeReleaseEmbeddingModel()
        config = null
        dimension = 0
    }

}
