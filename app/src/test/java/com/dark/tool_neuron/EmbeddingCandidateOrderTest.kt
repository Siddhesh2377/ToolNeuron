package com.dark.tool_neuron

import com.dark.tool_neuron.model.ModelInfo
import com.dark.tool_neuron.model.enums.PathType
import com.dark.tool_neuron.model.enums.ProviderType
import com.dark.tool_neuron.repo.orderEmbeddingCandidates
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EmbeddingCandidateOrderTest {

    private fun model(id: String, type: ProviderType, pathType: PathType = PathType.FILE) =
        ModelInfo(id = id, name = id, path = "/m/$id.gguf", pathType = pathType, providerType = type)

    private val models = listOf(
        model("chat", ProviderType.GGUF),
        model("nomic", ProviderType.EMBEDDING),
        model("bge", ProviderType.EMBEDDING, PathType.CONTENT_URI),
    )

    @Test
    fun preferredModelIsTriedFirstThenTheRest() {
        assertEquals(listOf("bge", "nomic"), orderEmbeddingCandidates(models, "bge").map { it.id })
    }

    @Test
    fun fallsBackToInstallOrderWithoutAPreference() {
        assertEquals(listOf("nomic", "bge"), orderEmbeddingCandidates(models, null).map { it.id })
        assertEquals(listOf("nomic", "bge"), orderEmbeddingCandidates(models, "deleted").map { it.id })
    }

    @Test
    fun ignoresChatModels() {
        assertTrue(orderEmbeddingCandidates(listOf(model("chat", ProviderType.GGUF)), "chat").isEmpty())
    }
}
