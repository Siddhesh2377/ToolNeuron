package com.dark.tool_neuron.billing

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
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

/**
 * Central feature gate with scattered validation.
 *
 * Instead of a single boolean, premium status is verified through multiple
 * independent checks with different obfuscated names. A patcher would need
 * to find and patch ALL of them, and the integrity guard invalidates everything
 * if the APK is re-signed.
 */
@Singleton
class FeatureGateManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val billingManager: BillingManager,
    private val licenseManager: LicenseManager
) {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // Primary state — visible to UI for reactive updates
    private val _isPro = MutableStateFlow(false)
    val isPro: StateFlow<Boolean> = _isPro.asStateFlow()

    // Scattered internal validators — each checks independently.
    // R8 will obfuscate these names, so a patcher can't search for "isPro".
    @Volatile private var _cfgTier = 0         // 0=free, 1=pro
    @Volatile private var _renderQuality = 0   // 0=standard, 1=enhanced
    @Volatile private var _accessLevel = 0     // 0=basic, 1=full

    init {
        scope.launch {
            billingManager.isProUnlocked
                .combine(licenseManager.isLicenseValid) { billing, license ->
                    billing || license
                }
                .collect { proStatus ->
                    updateAllGates(proStatus)
                }
        }
    }

    /**
     * Update all scattered gates atomically.
     * An attacker would need to patch this method AND all individual checks.
     */
    private fun updateAllGates(pro: Boolean) {
        val verified = pro && IntegrityGuard.isIntact(context)
        _isPro.value = verified
        _cfgTier = if (verified) 1 else 0
        _renderQuality = if (verified) 1 else 0
        _accessLevel = if (verified) 1 else 0
    }

    /**
     * Internal check — uses scattered validators + integrity.
     * Even if someone patches _isPro.value, these independent checks catch it.
     */
    private fun isUnlocked(): Boolean {
        // All three must agree AND integrity must pass
        return _cfgTier == 1
                && _renderQuality == 1
                && _accessLevel == 1
                && IntegrityGuard.isIntact(context)
    }

    // -- Pro-only features ------------------------------------------------

    fun canUseCharacterIntelligence(): Boolean = isUnlocked()

    fun canUseAdaptiveLearning(): Boolean = isUnlocked()

    fun canUseThinkingVisualization(): Boolean = isUnlocked()

    fun canUseMultimodal(): Boolean = isUnlocked()

    fun canUseInpainting(): Boolean = isUnlocked()

    fun canImportExportPersonas(): Boolean = isUnlocked()

    fun canUseKnowledgeGraph(): Boolean = isUnlocked()

    fun canUseEncryptedRag(): Boolean = isUnlocked()

    // -- Limit-gated features ---------------------------------------------

    fun canCreatePersona(currentCount: Int): Boolean {
        return isUnlocked() || currentCount < FreeLimits.MAX_PERSONA_CARDS
    }

    fun canAddMemory(currentCount: Int): Boolean {
        return isUnlocked() || currentCount < FreeLimits.MAX_AI_MEMORIES
    }

    fun canAddRag(currentCount: Int): Boolean {
        return isUnlocked() || currentCount < FreeLimits.MAX_RAG_BASES
    }

    fun canUseTtsVoice(voiceId: String): Boolean {
        return isUnlocked() || voiceId in FreeLimits.FREE_TTS_VOICES
    }

    fun canUsePlugin(pluginName: String): Boolean {
        return isUnlocked() || pluginName in FreeLimits.FREE_PLUGINS
    }

    fun canUseImageSize(size: Int): Boolean {
        return isUnlocked() || size <= FreeLimits.MAX_IMAGE_SIZE
    }
}
