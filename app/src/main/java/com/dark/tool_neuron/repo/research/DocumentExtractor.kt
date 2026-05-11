package com.dark.tool_neuron.repo.research

import com.dark.gguf_lib.RAGEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal object DocumentExtractor {
    suspend fun extract(
        bytes: ByteArray,
        mimeHint: String? = null,
        nameHint: String? = null,
    ): String? = withContext(Dispatchers.IO) {
        if (bytes.isEmpty()) return@withContext null
        try {
            RAGEngine().extractText(bytes, mimeHint, nameHint)?.takeIf { it.isNotBlank() }
        } catch (_: Exception) {
            null
        }
    }
}
