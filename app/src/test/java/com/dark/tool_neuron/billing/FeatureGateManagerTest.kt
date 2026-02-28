package com.dark.tool_neuron.billing

import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.lang.reflect.Field

/**
 * Unit tests for [FeatureGateManager].
 *
 * We bypass Hilt by constructing the manager with mock billing/license managers
 * that are not initialized (their flows default to false). We then use reflection
 * to set the internal `_isPro` state for deterministic testing.
 */
class FeatureGateManagerTest {

    private lateinit var gateManager: FeatureGateManager
    private lateinit var isProField: Field

    /** Helper: set the pro state via reflection on the internal [MutableStateFlow]. */
    @Suppress("UNCHECKED_CAST")
    private fun setPro(value: Boolean) {
        val flow = isProField.get(gateManager) as MutableStateFlow<Boolean>
        flow.value = value
    }

    @Before
    fun setUp() {
        // We cannot easily instantiate BillingManager / LicenseManager without
        // a real Android Context, so we create them via Unsafe-style tricks.
        // Instead we use a simpler approach: create a subclass-free test by
        // constructing via the primary constructor with null-context managers
        // and immediately overriding _isPro.  Since BillingManager and
        // LicenseManager both require @ApplicationContext, we use a minimal
        // approach with a custom test helper.
        gateManager = createTestGateManager()
        isProField = FeatureGateManager::class.java.getDeclaredField("_isPro")
        isProField.isAccessible = true
    }

    /**
     * Creates a [FeatureGateManager] for testing without real Android dependencies.
     * Uses sun.misc.Unsafe to allocate the instance without invoking the constructor,
     * then initializes the _isPro field manually.
     */
    private fun createTestGateManager(): FeatureGateManager {
        val unsafe = sun.misc.Unsafe::class.java
            .getDeclaredMethod("getUnsafe")
            .let { method ->
                // getUnsafe is restricted, use theUnsafe field instead
                val field = sun.misc.Unsafe::class.java.getDeclaredField("theUnsafe")
                field.isAccessible = true
                field.get(null) as sun.misc.Unsafe
            }

        val instance = unsafe.allocateInstance(FeatureGateManager::class.java) as FeatureGateManager

        // Initialize the _isPro field
        val isProField = FeatureGateManager::class.java.getDeclaredField("_isPro")
        isProField.isAccessible = true
        isProField.set(instance, MutableStateFlow(false))

        return instance
    }

    // ---------------------------------------------------------------
    // Pro-only features: should return true ONLY when pro is active
    // ---------------------------------------------------------------

    @Test
    fun `canUseCharacterIntelligence returns false for free`() {
        setPro(false)
        assertFalse(gateManager.canUseCharacterIntelligence())
    }

    @Test
    fun `canUseCharacterIntelligence returns true for pro`() {
        setPro(true)
        assertTrue(gateManager.canUseCharacterIntelligence())
    }

    @Test
    fun `canUseAdaptiveLearning returns false for free`() {
        setPro(false)
        assertFalse(gateManager.canUseAdaptiveLearning())
    }

    @Test
    fun `canUseAdaptiveLearning returns true for pro`() {
        setPro(true)
        assertTrue(gateManager.canUseAdaptiveLearning())
    }

    @Test
    fun `canUseThinkingVisualization returns false for free`() {
        setPro(false)
        assertFalse(gateManager.canUseThinkingVisualization())
    }

    @Test
    fun `canUseThinkingVisualization returns true for pro`() {
        setPro(true)
        assertTrue(gateManager.canUseThinkingVisualization())
    }

    @Test
    fun `canUseMultimodal returns false for free`() {
        setPro(false)
        assertFalse(gateManager.canUseMultimodal())
    }

    @Test
    fun `canUseMultimodal returns true for pro`() {
        setPro(true)
        assertTrue(gateManager.canUseMultimodal())
    }

    @Test
    fun `canUseInpainting returns false for free`() {
        setPro(false)
        assertFalse(gateManager.canUseInpainting())
    }

    @Test
    fun `canUseInpainting returns true for pro`() {
        setPro(true)
        assertTrue(gateManager.canUseInpainting())
    }

    @Test
    fun `canImportExportPersonas returns false for free`() {
        setPro(false)
        assertFalse(gateManager.canImportExportPersonas())
    }

    @Test
    fun `canImportExportPersonas returns true for pro`() {
        setPro(true)
        assertTrue(gateManager.canImportExportPersonas())
    }

    @Test
    fun `canUseKnowledgeGraph returns false for free`() {
        setPro(false)
        assertFalse(gateManager.canUseKnowledgeGraph())
    }

    @Test
    fun `canUseKnowledgeGraph returns true for pro`() {
        setPro(true)
        assertTrue(gateManager.canUseKnowledgeGraph())
    }

    @Test
    fun `canUseEncryptedRag returns false for free`() {
        setPro(false)
        assertFalse(gateManager.canUseEncryptedRag())
    }

    @Test
    fun `canUseEncryptedRag returns true for pro`() {
        setPro(true)
        assertTrue(gateManager.canUseEncryptedRag())
    }

    // ---------------------------------------------------------------
    // Limit-gated features: free users have capped access
    // ---------------------------------------------------------------

    @Test
    fun `canCreatePersona allows up to limit for free`() {
        setPro(false)
        assertTrue(gateManager.canCreatePersona(0))
        assertTrue(gateManager.canCreatePersona(2))
        assertFalse(gateManager.canCreatePersona(3))
        assertFalse(gateManager.canCreatePersona(10))
    }

    @Test
    fun `canCreatePersona unlimited for pro`() {
        setPro(true)
        assertTrue(gateManager.canCreatePersona(0))
        assertTrue(gateManager.canCreatePersona(100))
    }

    @Test
    fun `canAddMemory allows up to limit for free`() {
        setPro(false)
        assertTrue(gateManager.canAddMemory(0))
        assertTrue(gateManager.canAddMemory(4))
        assertFalse(gateManager.canAddMemory(5))
        assertFalse(gateManager.canAddMemory(50))
    }

    @Test
    fun `canAddMemory unlimited for pro`() {
        setPro(true)
        assertTrue(gateManager.canAddMemory(0))
        assertTrue(gateManager.canAddMemory(500))
    }

    @Test
    fun `canAddRag allows up to limit for free`() {
        setPro(false)
        assertTrue(gateManager.canAddRag(0))
        assertFalse(gateManager.canAddRag(1))
        assertFalse(gateManager.canAddRag(5))
    }

    @Test
    fun `canAddRag unlimited for pro`() {
        setPro(true)
        assertTrue(gateManager.canAddRag(0))
        assertTrue(gateManager.canAddRag(100))
    }

    @Test
    fun `canUseTtsVoice free voices allowed for free tier`() {
        setPro(false)
        assertTrue(gateManager.canUseTtsVoice("F1"))
        assertTrue(gateManager.canUseTtsVoice("M1"))
        assertFalse(gateManager.canUseTtsVoice("F2"))
        assertFalse(gateManager.canUseTtsVoice("M3"))
    }

    @Test
    fun `canUseTtsVoice all voices allowed for pro`() {
        setPro(true)
        assertTrue(gateManager.canUseTtsVoice("F1"))
        assertTrue(gateManager.canUseTtsVoice("F2"))
        assertTrue(gateManager.canUseTtsVoice("M3"))
        assertTrue(gateManager.canUseTtsVoice("Custom"))
    }

    @Test
    fun `canUsePlugin free plugins allowed for free tier`() {
        setPro(false)
        assertTrue(gateManager.canUsePlugin("Calculator"))
        assertTrue(gateManager.canUsePlugin("Clipboard"))
        assertTrue(gateManager.canUsePlugin("Date & Time"))
        assertFalse(gateManager.canUsePlugin("Web Search"))
        assertFalse(gateManager.canUsePlugin("Code Executor"))
    }

    @Test
    fun `canUsePlugin all plugins allowed for pro`() {
        setPro(true)
        assertTrue(gateManager.canUsePlugin("Calculator"))
        assertTrue(gateManager.canUsePlugin("Web Search"))
        assertTrue(gateManager.canUsePlugin("Code Executor"))
        assertTrue(gateManager.canUsePlugin("Anything"))
    }

    @Test
    fun `canUseImageSize capped at 512 for free`() {
        setPro(false)
        assertTrue(gateManager.canUseImageSize(256))
        assertTrue(gateManager.canUseImageSize(512))
        assertFalse(gateManager.canUseImageSize(768))
        assertFalse(gateManager.canUseImageSize(1024))
    }

    @Test
    fun `canUseImageSize unlimited for pro`() {
        setPro(true)
        assertTrue(gateManager.canUseImageSize(512))
        assertTrue(gateManager.canUseImageSize(1024))
        assertTrue(gateManager.canUseImageSize(2048))
    }

    // ---------------------------------------------------------------
    // FreeLimits constants validation
    // ---------------------------------------------------------------

    @Test
    fun `FreeLimits constants have expected values`() {
        assertEquals(3, FreeLimits.MAX_PERSONA_CARDS)
        assertEquals(5, FreeLimits.MAX_AI_MEMORIES)
        assertEquals(1, FreeLimits.MAX_RAG_BASES)
        assertEquals(512, FreeLimits.MAX_IMAGE_SIZE)
        assertEquals(setOf("F1", "M1"), FreeLimits.FREE_TTS_VOICES)
        assertEquals(setOf("Calculator", "Clipboard", "Date & Time"), FreeLimits.FREE_PLUGINS)
    }
}
