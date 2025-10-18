package com.dark.neuroverse.viewModel.chatViewModel

import android.util.Log
import com.mp.data_hub_lib.manager.DataHubManager
import com.mp.data_hub_lib.model.RagResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.json.JSONObject

object RAGManager {

    private const val TAG = "RAGManager"

    suspend fun initRAG(): JSONObject = withContext(Dispatchers.IO) {
        val initResult = DataHubManager.reinitializeEmbeddingModel()
        if (initResult.isFailure) {
            JSONObject().put(
                "error",
                "Embedding model initialization failed: ${initResult.exceptionOrNull()?.message}"
            )
        } else {
            JSONObject().put("success", "Embedding model initialized")
        }
    }

    suspend fun handleRAGRequest(input: String): JSONObject = withContext(Dispatchers.IO) {
        val currentDataset = DataHubManager.currentDataSet.value
        if (currentDataset == null) {
            Log.d(TAG, "No dataset loaded, fallback to normal generation")
            return@withContext JSONObject().put("error", "No dataset loaded")
        }

        try {
            val (ragData, error) = suspendCancellableCoroutine { continuation ->
                DataHubManager.runRAG(query = input, topK = 5) { ragResult, ragError ->
                    continuation.resume(ragResult to ragError) { cause, _, _ ->  }
                }
            }

            if (ragData != null && error == null) {
                val ragContext = extractRAGContext(ragData)
                if (ragContext.isNotBlank()) {
                    val finalPrompt = buildRAGPrompt(ragContext, input)
                    JSONObject().put("success", finalPrompt)
                } else {
                    JSONObject().put("error", "RAG context is empty")
                }
            } else {
                JSONObject().put("error", "RAG failed: $error")
            }
        } catch (e: Exception) {
            JSONObject().put("error", "RAG processing failed: ${e.message}")
        }
    }

    private fun buildRAGPrompt(context: String, input: String): String = buildString {
        append("Use the following context to answer:\n\n")
        append("Context:\n")
        append(context)
        append("\n\nQuestion: ")
        append(input)
    }

    private fun extractRAGContext(ragData: RagResult): String {
        return try {
            ragData.docs.joinToString("\n") { it.text }
        } catch (e: Exception) {
            ""
        }
    }
}
