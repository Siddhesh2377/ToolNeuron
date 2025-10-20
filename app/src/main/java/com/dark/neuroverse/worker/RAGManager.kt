package com.dark.neuroverse.worker

import android.util.Log
import com.dark.ai_module.workers.ModelManager
import com.mp.ai_core.NativeLib
import com.mp.data_hub_lib.manager.DataHubManager
import com.mp.data_hub_lib.model.DataSetModel
import com.mp.data_hub_lib.model.RagResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.json.JSONObject
import kotlin.coroutines.resume

object RAGManager {

    private const val TAG = "RAGManager"
    private const val DEFAULT_TOP_K = 5
    private const val MAX_CONTEXT_LENGTH = 4096
    private const val MODEL_SWITCH_DELAY_MS = 500L

    /**
     * Initializes or reinitializes the embedding model for RAG operations.
     * Should be called before performing any RAG queries.
     *
     * @return JSONObject with "success" or "error" key
     */
    suspend fun initRAG(): JSONObject = withContext(Dispatchers.IO) {
        return@withContext try {
            Log.d(TAG, "Initializing embedding model...")

            val initResult = DataHubManager.reinitializeEmbeddingModel()

            if (initResult.isFailure) {
                val errorMsg = initResult.exceptionOrNull()?.message ?: "Unknown error"
                Log.e(TAG, "Embedding model initialization failed: $errorMsg")
                JSONObject().put("error", "Embedding model initialization failed: $errorMsg")
            } else {
                Log.d(TAG, "Embedding model initialized successfully")
                JSONObject().put("success", "Embedding model initialized")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception during embedding model initialization", e)
            JSONObject().put("error", "Initialization exception: ${e.message}")
        }
    }

    /**
     * Handles the complete RAG workflow:
     * 1. Validates dataset availability
     * 2. Performs semantic search
     * 3. Extracts relevant context
     * 4. Builds augmented prompt
     *
     * @param input User query
     * @param topK Number of top results to retrieve (default: 5)
     * @return JSONObject with "success" (augmented prompt) or "error"
     */
    suspend fun handleRAGRequest(
        input: String, topK: Int = DEFAULT_TOP_K
    ): JSONObject = withContext(Dispatchers.IO) {
        // Validate dataset
        val currentDataset = DataHubManager.currentDataSet.value
        if (currentDataset == null) {
            Log.w(TAG, "No dataset loaded for RAG query")
            return@withContext JSONObject().put("error", "No dataset loaded")
        }

        Log.d(TAG, "Processing RAG request for query: ${input.take(50)}...")

        return@withContext try {
            // Perform RAG query
            val (ragData, error) = performRAGQuery(input, topK)

            if (ragData != null && error == null) {
                // Extract and validate context
                val ragContext = extractRAGContext(ragData)

                if (ragContext.isBlank()) {
                    Log.w(TAG, "RAG returned empty context")
                    JSONObject().put("error", "No relevant context found")
                } else {
                    Log.d(TAG, "RAG context extracted: ${ragContext.length} chars")
                    val finalPrompt = buildRAGPrompt(ragContext, input)
                    JSONObject().put("success", finalPrompt)
                }
            } else {
                val errorMsg = error ?: "Unknown RAG error"
                Log.e(TAG, "RAG query failed: $errorMsg")
                JSONObject().put("error", "RAG failed: $errorMsg")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception during RAG processing", e)
            JSONObject().put("error", "RAG processing failed: ${e.message}")
        }
    }

    /**
     * Ensures the generation model is loaded and ready after RAG context retrieval.
     * Handles model switching between embedding and generation models.
     *
     * @param currentModel The model to load for generation
     * @param onStateUpdate Callback for loading state updates
     * @return JSONObject with "success" or "error"
     */
    suspend fun ensureGenerationModelReady(
        currentModel: com.dark.ai_module.model.ModelData,
        onStateUpdate: (com.dark.ai_module.model.LoadState) -> Unit = {}
    ): JSONObject = withContext(Dispatchers.IO) {
        return@withContext try {
            Log.d(TAG, "Ensuring generation model is ready...")

            // Release embedding model instance
            val generationLib = NativeLib.getGenerationInstance()
            generationLib.nativeRelease()

            // Brief delay to ensure clean state
            delay(MODEL_SWITCH_DELAY_MS)

            // Load generation model
            val result = ModelManager.loadModelAwait(currentModel) { loadState ->
                onStateUpdate(loadState)
                when (loadState) {
                    is com.dark.ai_module.model.LoadState.OnLoaded -> {
                        Log.d(TAG, "Generation model loaded: ${loadState.model.modelName}")
                    }

                    is com.dark.ai_module.model.LoadState.Error -> {
                        Log.e(TAG, "Generation model error: ${loadState.message}")
                    }

                    else -> {
                        Log.d(TAG, "Generation model loading: $loadState")
                    }
                }
            }

            if (result.isSuccess) {
                Log.d(TAG, "Generation model ready")
                JSONObject().put("success", "Model loaded successfully")
            } else {
                val errorMsg = result.exceptionOrNull()?.message ?: "Unknown error"
                Log.e(TAG, "Generation model load failed: $errorMsg")
                JSONObject().put("error", "Generation model load failed: $errorMsg")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception ensuring generation model ready", e)
            JSONObject().put("error", "Model loading exception: ${e.message}")
        }
    }

    /**
     * Performs the actual RAG query using suspendCancellableCoroutine
     * to bridge callback-based API to coroutine.
     */
    private suspend fun performRAGQuery(
        query: String, topK: Int
    ): Pair<RagResult?, String?> = suspendCancellableCoroutine { continuation ->
        try {
            DataHubManager.runRAG(query = query, topK = topK) { ragResult, ragError ->
                if (continuation.isActive) {
                    continuation.resume(ragResult to ragError)
                }
            }
        } catch (e: Exception) {
            if (continuation.isActive) {
                continuation.resume(null to e.message)
            }
        }
    }

    /**
     * Builds the final prompt with RAG context.
     * Format: Context block + User question
     */
    private fun buildRAGPrompt(context: String, input: String): String {
        val truncatedContext = if (context.length > MAX_CONTEXT_LENGTH) {
            Log.w(TAG, "Context truncated from ${context.length} to $MAX_CONTEXT_LENGTH chars")
            context.take(MAX_CONTEXT_LENGTH) + "\n[Context truncated...]"
        } else {
            context
        }

        return buildString {
            appendLine("Use the following context to answer the question. If the context doesn't contain relevant information, say so.")
            appendLine()
            appendLine("Context:")
            appendLine("---")
            appendLine(truncatedContext)
            appendLine("---")
            appendLine()
            appendLine("Question: $input")
            appendLine()
            append("Answer:")
        }
    }

    /**
     * Extracts text content from RAG results.
     * Handles multiple document chunks and concatenates them.
     */
    private fun extractRAGContext(ragData: RagResult): String {
        return try {
            if (ragData.docs.isEmpty()) {
                Log.w(TAG, "No documents in RAG result")
                return ""
            }

            val context = ragData.docs.filter { it.text.isNotBlank() }.mapIndexed { index, doc ->
                    "[Doc ${index + 1}] ${doc.text.trim()}"
                }.joinToString("\n\n")

            Log.d(TAG, "Extracted ${ragData.docs.size} documents, ${context.length} chars")
            context
        } catch (e: Exception) {
            Log.e(TAG, "Error extracting RAG context", e)
            ""
        }
    }

    /**
     * Validates if RAG is ready to be used.
     * Checks for dataset availability.
     */
    fun isRAGReady(): Boolean {
        val isReady = DataHubManager.currentDataSet.value != null
        Log.d(TAG, "RAG ready status: $isReady")
        return isReady
    }

    /**
     * Gets current dataset information for debugging/UI display.
     */
    fun getCurrentDatasetInfo(): DataSetModel? {
        return DataHubManager.currentDataSet.value
    }
}