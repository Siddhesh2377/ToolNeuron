package com.dark.tool_neuron.repo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelStoreRepositoryTest {

    @Test
    fun supportedGgufFileAcceptsUppercaseExtension() {
        assertTrue(ModelStoreRepository.isSupportedGgufFile("models/Whisper-EN-Small.Q5_K_M.GGUF"))
    }

    @Test
    fun supportedGgufFileAcceptsMixedCaseExtension() {
        assertTrue(ModelStoreRepository.isSupportedGgufFile("models/Whisper-EN-Small.Q5_K_M.GgUf"))
        assertTrue(ModelStoreRepository.isSupportedGgufFile("models/whisper-en-small.q5_k_m.gguf"))
    }

    @Test
    fun supportedGgufFileRejectsProjectionArtifacts() {
        assertFalse(ModelStoreRepository.isSupportedGgufFile("models/whisper-mmproj.Q4_K_M.GGUF"))
        assertFalse(ModelStoreRepository.isSupportedGgufFile("models/whisper-vision-adapter.gguf"))
        assertFalse(ModelStoreRepository.isSupportedGgufFile("models/whisper-projector.gguf"))
    }

    @Test
    fun stripGgufSuffixRemovesExtensionCaseInsensitively() {
        assertEquals(
            "Whisper-EN-Small.Q5_K_M",
            ModelStoreRepository.stripGgufSuffix("Whisper-EN-Small.Q5_K_M.GGUF")
        )
    }

    @Test
    fun extractQuantTypeStripsSuffixBeforeReadingLastSegment() {
        assertEquals("Q5_K_M", ModelStoreRepository.extractQuantType("Whisper-EN-Small.Q5_K_M.GGUF"))
        assertEquals("Q5_K_M", ModelStoreRepository.extractQuantType("whisper-en-small.q5_k_m.gguf"))
        assertEquals("MODEL", ModelStoreRepository.extractQuantType("model.GGUF"))
    }

    @Test
    fun extractQuantTypeIgnoresTrailingDescriptorSuffixes() {
        assertEquals(
            "Q4_K_M",
            ModelStoreRepository.extractQuantType("Whisper-EN-Small-Q4_K_M-hip-optimized.gguf")
        )
    }

    @Test
    fun extractModelFamilyKeyRemovesTrailingQuantizationDetails() {
        assertEquals(
            "whisper-en-small",
            ModelStoreRepository.extractModelFamilyKey("Whisper-EN-Small-Q4_K_M-hip-optimized.gguf")
        )
    }
}
