package com.dark.tool_neuron.billing

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

object FreeLimits {
    const val MAX_PERSONA_CARDS = 3
    const val MAX_AI_MEMORIES = 5
    const val MAX_RAG_BASES = 1
    const val MAX_IMAGE_SIZE = 512
    val FREE_TTS_VOICES = setOf("F1", "M1")
    val FREE_PLUGINS = setOf("Calculator", "Clipboard", "Date & Time")
}

@Singleton
class FeatureGateManager @Inject constructor(
    private val billingManager: BillingManager,
    private val licenseManager: LicenseManager
) {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val _isPro = MutableStateFlow(false)
    val isPro: StateFlow<Boolean> = _isPro.asStateFlow()

    init {
        scope.launch {
            billingManager.isProUnlocked
                .combine(licenseManager.isLicenseValid) { billing, license ->
                    billing || license
                }
                .collect { proStatus ->
                    _isPro.value = proStatus
                }
        }
    }

    // -- Pro-only features ------------------------------------------------

    fun canUseCharacterIntelligence(): Boolean = _isPro.value

    fun canUseAdaptiveLearning(): Boolean = _isPro.value

    fun canUseThinkingVisualization(): Boolean = _isPro.value

    fun canUseMultimodal(): Boolean = _isPro.value

    fun canUseInpainting(): Boolean = _isPro.value

    fun canImportExportPersonas(): Boolean = _isPro.value

    fun canUseKnowledgeGraph(): Boolean = _isPro.value

    fun canUseEncryptedRag(): Boolean = _isPro.value

    // -- Limit-gated features ---------------------------------------------

    fun canCreatePersona(currentCount: Int): Boolean {
        return _isPro.value || currentCount < FreeLimits.MAX_PERSONA_CARDS
    }

    fun canAddMemory(currentCount: Int): Boolean {
        return _isPro.value || currentCount < FreeLimits.MAX_AI_MEMORIES
    }

    fun canAddRag(currentCount: Int): Boolean {
        return _isPro.value || currentCount < FreeLimits.MAX_RAG_BASES
    }

    fun canUseTtsVoice(voiceId: String): Boolean {
        return _isPro.value || voiceId in FreeLimits.FREE_TTS_VOICES
    }

    fun canUsePlugin(pluginName: String): Boolean {
        return _isPro.value || pluginName in FreeLimits.FREE_PLUGINS
    }

    fun canUseImageSize(size: Int): Boolean {
        return _isPro.value || size <= FreeLimits.MAX_IMAGE_SIZE
    }
}
