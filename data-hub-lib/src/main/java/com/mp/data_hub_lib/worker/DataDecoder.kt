package com.mp.data_hub_lib.worker

import android.util.Log
import com.mp.data_hub_lib.model.BrainDoc
import com.mp.data_hub_lib.model.BrainRoot
import com.mp.data_hub_lib.model.Doc
import kotlinx.serialization.json.Json
import kotlin.math.sqrt


// --- Brain Decoder and Vector Store ---
object BrainDecoder {
    private var docs: List<BrainDoc> = emptyList()
    private var isLoaded = false

    fun loadBrain(decoded: String): BrainRoot? {
        return try {
            // If the input is an array, wrap it in an object with a "docs" field
            val json = if (decoded.startsWith("[")) {
                """{"docs": $decoded}"""
            } else {
                decoded
            }
            val brainRoot = Json.decodeFromString<BrainRoot>(json)
            docs = brainRoot.docs
            isLoaded = true
            Log.i("BrainDecoder", "Loaded ${docs.size} documents")
            brainRoot
        } catch (e: Exception) {
            Log.e("BrainDecoder", "Failed to load brain: ${e.message}")
            e.printStackTrace()
            null
        }
    }


    fun search(queryEmbedding: FloatArray, topK: Int = 5): List<Doc> {
        if (!isLoaded || docs.isEmpty()) {
            Log.w("BrainDecoder", "Brain not loaded or empty")
            return emptyList()
        }
        return docs.map { doc ->
            Log.d("BrainDecoder", "doc0 sample: ${docs[0].embedding.take(5)}")
            Log.d("RAG", "query embedding sample: ${queryEmbedding.take(5).joinToString()}")

            val similarity = cosineSimilarity(doc.embedding.toFloatArray(), queryEmbedding)
            Doc(doc.text, similarity)
        }.sortedByDescending { it.similarity }.take(topK)
    }

    private fun cosineSimilarity(a: FloatArray, b: FloatArray): Double {
        if (a.size != b.size) return 0.0
        val dot = a.zip(b).sumOf { (x, y) -> (x * y).toDouble() }
        val normA = sqrt(a.sumOf { (it * it).toDouble() })
        val normB = sqrt(b.sumOf { (it * it).toDouble() })
        return if (normA != 0.0 && normB != 0.0) dot / (normA * normB) else 0.0
    }
}
