package com.dark.tool_neuron

import com.dark.tool_neuron.util.looksLikeEmbeddingModel
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EmbeddingModelDetectorTest {

    @Test
    fun detectsReportedEmbeddingRepos() {
        assertTrue(looksLikeEmbeddingModel("Qwen/Qwen3-Embedding-0.6B-GGUF", "Qwen3-Embedding-0.6B-Q8_0.gguf"))
        assertTrue(looksLikeEmbeddingModel("Qwen/Qwen3-Embedding-4B-GGUF", "Qwen3-Embedding-4B-Q4_K_M.gguf"))
        assertTrue(looksLikeEmbeddingModel("gpustack/bge-m3-GGUF", "bge-m3-Q4_K_M.gguf"))
        assertTrue(looksLikeEmbeddingModel("unsloth/embeddinggemma-300m-GGUF", "embeddinggemma-300m-Q4_0.gguf"))
        assertTrue(looksLikeEmbeddingModel("nomic-ai/nomic-embed-text-v1.5-GGUF", "nomic-embed-text-v1.5.Q4_K_M.gguf"))
        assertTrue(looksLikeEmbeddingModel("x/multilingual-e5-small-GGUF", "multilingual-e5-small-q8_0.gguf"))
    }

    @Test
    fun detectsByPipelineTag() {
        assertTrue(looksLikeEmbeddingModel("acme/some-model-GGUF", "model-Q4_K_M.gguf", "sentence-similarity"))
        assertTrue(looksLikeEmbeddingModel("acme/some-model-GGUF", "model-Q4_K_M.gguf", "feature-extraction"))
    }

    @Test
    fun leavesChatModelsAlone() {
        assertFalse(looksLikeEmbeddingModel("unsloth/gemma-3-4b-it-GGUF", "gemma-3-4b-it-Q4_K_M.gguf", "text-generation"))
        assertFalse(looksLikeEmbeddingModel("Qwen/Qwen3-0.6B-GGUF", "Qwen3-0.6B-Q8_0.gguf"))
        assertFalse(looksLikeEmbeddingModel("LiquidAI/LFM2-350M-GGUF", "LFM2-350M-Q4_K_M.gguf"))
        assertFalse(looksLikeEmbeddingModel("bartowski/Llama-3.2-3B-Instruct-GGUF", "Llama-3.2-3B-Instruct-Q4_K_M.gguf"))
    }

    @Test
    fun neverTagsProjectorFiles() {
        assertFalse(looksLikeEmbeddingModel("x/vision-embed-GGUF", "mmproj-model-f16.gguf", "feature-extraction"))
    }
}
