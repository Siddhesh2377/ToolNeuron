package com.dark.plugins.expense

import com.dark.plugin_api.api.OnnxApi
import com.dark.plugin_api.api.OnnxOptions
import com.dark.plugin_api.api.OnnxSession
import com.dark.plugin_api.api.OnnxTensor
import kotlin.math.sqrt

internal class Embedder(
    private val session: OnnxSession,
    private val tokenizer: WordPieceTokenizer,
    private val maxLen: Int = 64,
) {

    val dimension: Int = 384

    private val hasTokenTypeIds: Boolean = session.inputNames.any { it == "token_type_ids" }

    suspend fun embed(text: String): FloatArray {
        val encoded = tokenizer.encode(text, maxLen)
        val shape = longArrayOf(1, maxLen.toLong())
        val inputs = HashMap<String, OnnxTensor>(3)
        inputs["input_ids"] = OnnxTensor.I64(encoded.inputIds, shape)
        inputs["attention_mask"] = OnnxTensor.I64(encoded.attentionMask, shape)
        if (hasTokenTypeIds) {
            inputs["token_type_ids"] = OnnxTensor.I64(encoded.tokenTypeIds, shape)
        }
        val outputs = session.run(inputs)
        val hidden = outputs.values.firstOrNull { it is OnnxTensor.F32 } as? OnnxTensor.F32
            ?: error("model output missing float tensor")
        return meanPoolAndNormalize(hidden.data, encoded.attentionMask)
    }

    private fun meanPoolAndNormalize(hidden: FloatArray, mask: LongArray): FloatArray {
        val dim = dimension
        val pooled = FloatArray(dim)
        var count = 0f
        for (t in mask.indices) {
            if (mask[t] == 0L) continue
            val base = t * dim
            for (d in 0 until dim) pooled[d] += hidden[base + d]
            count += 1f
        }
        if (count > 0f) {
            for (d in 0 until dim) pooled[d] /= count
        }
        var sumSq = 0f
        for (d in 0 until dim) sumSq += pooled[d] * pooled[d]
        val norm = sqrt(sumSq.toDouble()).toFloat()
        if (norm > 0f) {
            for (d in 0 until dim) pooled[d] /= norm
        }
        return pooled
    }

    fun close() {
        runCatching { session.close() }
    }

    companion object {
        suspend fun create(
            onnx: OnnxApi,
            modelPath: String,
            tokenizer: WordPieceTokenizer,
        ): Embedder {
            val session = onnx.loadSession(modelPath, OnnxOptions(useXnnpack = true))
            return Embedder(session, tokenizer)
        }
    }
}

internal fun cosineSimilarity(a: FloatArray, b: FloatArray): Float {
    var dot = 0f
    val n = minOf(a.size, b.size)
    for (i in 0 until n) dot += a[i] * b[i]
    return dot
}
