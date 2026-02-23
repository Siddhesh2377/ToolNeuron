package com.dark.tool_neuron.engine

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Tracks the emotional state of a conversation across 6 axes using a 4-tier system:
 *
 *   Tier 0: Residual stream probing (~9μs, after each generation turn)
 *           Reads the model's internal emotional state via dot(activation, direction_vector).
 *           This is ground truth — it measures what the model IS expressing, not what
 *           the user typed or an external classifier thinks.
 *
 *   Tier 1: Keyword sentiment (<1ms, every user message)
 *   Tier 2: Embedding similarity (~50ms, after assistant response)
 *   Tier 3: LLM mood tagging (~2-5s, idle time only)
 *
 * Tier weights: Probe=50%, Keyword=10%, Embedding=25%, LLM=15%
 *
 * The tracker maintains an EmotionRegime state machine with transition costs
 * (e.g., WARMING→COOLING requires 3 consecutive cooling signals) for emotional
 * stability. A feedback loop compares the model's actual emotional state to the
 * persona target and computes correction signals that feed into all intervention systems.
 *
 * Emotional state uses dual-timescale EMA:
 *   - tokenAlpha=0.15 (fast adaptation within a turn)
 *   - turnAlpha=0.30 (slower drift across turns)
 *   - decay=0.90 toward neutral between turns
 */
class EmotionalStateTracker {

    companion object {
        private const val TAG = "EmotionalStateTracker"
        private const val DECAY_ALPHA = 0.90f
        private const val MAX_DYNAMIC_OFFSET = 0.3f
        private const val TURN_ALPHA = 0.30f

        // Tier weights (must sum to 1.0 when all tiers present)
        private const val WEIGHT_PROBE = 0.50f
        private const val WEIGHT_KEYWORD = 0.10f
        private const val WEIGHT_EMBEDDING = 0.25f
        private const val WEIGHT_LLM = 0.15f

        // Tier 2: Mood archetype phrases for embedding comparison
        val MOOD_ARCHETYPES = mapOf(
            "warmth_high" to "warm affectionate caring loving tender sweet gentle kind",
            "warmth_low" to "cold distant annoyed frustrated angry hostile bitter",
            "energy_high" to "excited energetic enthusiastic pumped amazing awesome",
            "energy_low" to "tired bored sleepy calm quiet subdued mellow",
            "humor_high" to "funny hilarious lol haha joke witty playful silly",
            "humor_low" to "serious somber grave important formal stern",
            "empathy_high" to "sad hurting lonely struggling worried scared afraid",
            "empathy_low" to "happy confident strong independent capable",
            "tension_high" to "angry conflict fight argue disagree upset annoyed",
            "tension_low" to "peaceful calm relaxed content comfortable safe",
            "intimacy_high" to "close personal private secret trust confide feelings",
            "intimacy_low" to "casual surface formal business general public"
        )
    }

    /**
     * Emotional regime — the dominant mode of the conversation.
     * Transitions have inertia: a regime change requires consecutive signals.
     */
    enum class EmotionRegime(val transitionCost: Int) {
        NEUTRAL(1),        // Default state, easy to leave
        WARMING(2),        // Building rapport/affection
        COOLING(3),        // Pulling back, de-escalating
        EXCITED(2),        // High energy, enthusiasm
        VULNERABLE(3),     // User expressing pain/fear, needs careful handling
        PLAYFUL(2),        // Humor/banter mode
        TENSE(3),          // Conflict, disagreement
        TRANSITIONING(1)   // Between regimes, unstable
    }

    data class EmotionalState(
        val warmth: Float = 0f,
        val energy: Float = 0f,
        val humor: Float = 0f,
        val empathy: Float = 0f,
        val tension: Float = 0f,
        val intimacy: Float = 0f
    ) {
        /** Clamp all axes to [-1, 1]. */
        fun clamped() = EmotionalState(
            warmth = warmth.coerceIn(-1f, 1f),
            energy = energy.coerceIn(-1f, 1f),
            humor = humor.coerceIn(-1f, 1f),
            empathy = empathy.coerceIn(-1f, 1f),
            tension = tension.coerceIn(-1f, 1f),
            intimacy = intimacy.coerceIn(-1f, 1f)
        )

        /** Apply exponential decay toward neutral. */
        fun decayed(alpha: Float = DECAY_ALPHA) = EmotionalState(
            warmth = warmth * alpha,
            energy = energy * alpha,
            humor = humor * alpha,
            empathy = empathy * alpha,
            tension = tension * alpha,
            intimacy = intimacy * alpha
        )

        /** Blend with another state (weighted average). */
        fun blend(other: EmotionalState, otherWeight: Float): EmotionalState {
            val w = otherWeight.coerceIn(0f, 1f)
            val s = 1f - w
            return EmotionalState(
                warmth = warmth * s + other.warmth * w,
                energy = energy * s + other.energy * w,
                humor = humor * s + other.humor * w,
                empathy = empathy * s + other.empathy * w,
                tension = tension * s + other.tension * w,
                intimacy = intimacy * s + other.intimacy * w
            ).clamped()
        }

        /** Convert to control vector offset map (clamped to ±MAX_DYNAMIC_OFFSET). */
        fun toVectorOffsets(): Map<String, Float> {
            return mapOf(
                "warmth" to warmth.coerceIn(-MAX_DYNAMIC_OFFSET, MAX_DYNAMIC_OFFSET),
                "energy" to energy.coerceIn(-MAX_DYNAMIC_OFFSET, MAX_DYNAMIC_OFFSET),
                "humor" to humor.coerceIn(-MAX_DYNAMIC_OFFSET, MAX_DYNAMIC_OFFSET),
                "emotion" to empathy.coerceIn(-MAX_DYNAMIC_OFFSET, MAX_DYNAMIC_OFFSET)
            )
        }

        /** L2 magnitude of the emotional state vector. */
        fun magnitude(): Float {
            return sqrt(warmth * warmth + energy * energy + humor * humor +
                    empathy * empathy + tension * tension + intimacy * intimacy)
        }

        /** Dominant axis name and value. */
        fun dominantAxis(): Pair<String, Float> {
            val axes = mapOf(
                "warmth" to warmth, "energy" to energy, "humor" to humor,
                "empathy" to empathy, "tension" to tension, "intimacy" to intimacy
            )
            return axes.maxByOrNull { abs(it.value) }?.toPair() ?: ("warmth" to 0f)
        }
    }

    private val _state = MutableStateFlow(EmotionalState())
    val state: StateFlow<EmotionalState> = _state.asStateFlow()

    private val _regime = MutableStateFlow(EmotionRegime.NEUTRAL)
    val regime: StateFlow<EmotionRegime> = _regime.asStateFlow()

    private var turnCount = 0
    private var regimeSignalCount = 0  // consecutive signals for pending regime
    private var pendingRegime = EmotionRegime.NEUTRAL

    // Velocity tracking for momentum
    private var velocity = EmotionalState()
    private val velocityDecay = 0.7f
    private val velocityInjection = 0.2f

    // Last probe result for feedback loop
    private var lastProbeState: EmotionalState? = null

    // ==================== Tier 0: Residual Stream Probe ====================

    /**
     * Parse the result of nativeProbeEmotionAxes() into an EmotionalState.
     *
     * The probe returns dot product scores for each personality axis at strategic
     * layers (40%, 60%, 80% depth). These represent the model's ACTUAL internal
     * emotional expression — the most reliable signal available.
     *
     * @param probeJson JSON from nativeProbeEmotionAxes: {"warmth": 0.35, "energy": -0.12, ...}
     * @return EmotionalState from probe, or null if probe failed/unavailable
     */
    fun parseProbeResult(probeJson: String): EmotionalState? {
        return try {
            val json = org.json.JSONObject(probeJson)
            if (json.has("error")) {
                Log.w(TAG, "Probe error: ${json.getString("error")}")
                return null
            }
            val state = EmotionalState(
                warmth = json.optDouble("warmth", 0.0).toFloat(),
                energy = json.optDouble("energy", 0.0).toFloat(),
                humor = json.optDouble("humor", 0.0).toFloat(),
                empathy = json.optDouble("emotion", 0.0).toFloat(), // "emotion" axis → empathy
                tension = 0f,  // not probed (no direction vector for tension)
                intimacy = 0f  // not probed (no direction vector for intimacy)
            )
            lastProbeState = state
            state
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse probe: ${e.message}")
            null
        }
    }

    /**
     * Compute feedback correction: compare model's actual state (from probe) to
     * the persona target, and return a correction signal that nudges interventions.
     *
     * Negative feedback loop: if the model is expressing MORE warmth than the target,
     * the correction is negative (reduce warmth interventions). If LESS, positive.
     *
     * @param personaTarget The desired emotional profile (from persona sliders)
     * @return Correction map to add to persona strengths, or empty if no probe data
     */
    fun computeFeedbackCorrection(personaTarget: Map<String, Float>): Map<String, Float> {
        val probe = lastProbeState ?: return emptyMap()
        val probeMap = mapOf(
            "warmth" to probe.warmth,
            "energy" to probe.energy,
            "humor" to probe.humor,
            "emotion" to probe.empathy
        )

        val correction = mutableMapOf<String, Float>()
        for ((axis, target) in personaTarget) {
            val actual = probeMap[axis] ?: continue
            // Correction = fraction of error to apply (0.3 = conservative)
            val error = target - actual
            correction[axis] = (error * 0.3f).coerceIn(-0.15f, 0.15f)
        }
        return correction
    }

    // ==================== Tier 1: Keyword Sentiment ====================

    /** Fast keyword-based sentiment analysis. Call after every user message. <1ms. */
    fun analyzeKeywords(userMessage: String): EmotionalState {
        val lower = userMessage.lowercase()
        var warmth = 0f; var energy = 0f; var humor = 0f
        var empathy = 0f; var tension = 0f; var intimacy = 0f

        // Warmth positive
        for (kw in WARMTH_POS) if (lower.contains(kw)) warmth += 0.1f
        // Warmth negative
        for (kw in WARMTH_NEG) if (lower.contains(kw)) warmth -= 0.15f
        // Energy
        for (kw in ENERGY_POS) if (lower.contains(kw)) energy += 0.1f
        if (lower.contains("!")) energy += 0.05f * lower.count { it == '!' }.coerceAtMost(3)
        for (kw in ENERGY_NEG) if (lower.contains(kw)) energy -= 0.1f
        // Humor
        for (kw in HUMOR_POS) if (lower.contains(kw)) humor += 0.12f
        // Empathy triggers (user expressing vulnerability)
        for (kw in EMPATHY_TRIGGERS) if (lower.contains(kw)) empathy += 0.1f
        // Tension
        for (kw in TENSION_POS) if (lower.contains(kw)) tension += 0.12f
        for (kw in TENSION_NEG) if (lower.contains(kw)) tension -= 0.1f
        // Intimacy
        for (kw in INTIMACY_POS) if (lower.contains(kw)) intimacy += 0.1f

        return EmotionalState(warmth, energy, humor, empathy, tension, intimacy).clamped()
    }

    // ==================== Tier 2: Embedding Similarity ====================

    /**
     * Embedding-based mood analysis. Call after assistant response. ~50ms.
     * Compares message embedding against pre-computed mood archetype embeddings.
     *
     * @param messageEmbedding Embedding of the last user+assistant exchange
     * @param archetypeEmbeddings Pre-computed embeddings for MOOD_ARCHETYPES keys
     */
    fun analyzeEmbedding(
        messageEmbedding: FloatArray,
        archetypeEmbeddings: Map<String, FloatArray>
    ): EmotionalState {
        fun score(highKey: String, lowKey: String): Float {
            val high = archetypeEmbeddings[highKey] ?: return 0f
            val low = archetypeEmbeddings[lowKey] ?: return 0f
            val simHigh = cosineSimilarity(messageEmbedding, high)
            val simLow = cosineSimilarity(messageEmbedding, low)
            return (simHigh - simLow).coerceIn(-1f, 1f)
        }

        return EmotionalState(
            warmth = score("warmth_high", "warmth_low"),
            energy = score("energy_high", "energy_low"),
            humor = score("humor_high", "humor_low"),
            empathy = score("empathy_high", "empathy_low"),
            tension = score("tension_high", "tension_low"),
            intimacy = score("intimacy_high", "intimacy_low")
        )
    }

    // ==================== Tier 3: LLM Mood Tagging ====================

    /**
     * Parse LLM mood analysis response.
     * Expected JSON: {"warmth": 0.5, "energy": 0.3, "humor": 0.1, ...}
     * Values are 0.0-1.0, mapped to -0.5 to +0.5 (centered on 0.5 = neutral).
     */
    fun parseLLMMoodResponse(jsonResponse: String): EmotionalState? {
        return try {
            val json = org.json.JSONObject(jsonResponse)
            EmotionalState(
                warmth = (json.optDouble("warmth", 0.5).toFloat() - 0.5f) * 2f,
                energy = (json.optDouble("energy", 0.5).toFloat() - 0.5f) * 2f,
                humor = (json.optDouble("humor", 0.5).toFloat() - 0.5f) * 2f,
                empathy = (json.optDouble("empathy", 0.5).toFloat() - 0.5f) * 2f,
                tension = (json.optDouble("tension", 0.5).toFloat() - 0.5f) * 2f,
                intimacy = (json.optDouble("intimacy", 0.5).toFloat() - 0.5f) * 2f
            )
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse LLM mood: ${e.message}")
            null
        }
    }

    /** Build the LLM prompt for Tier 3 mood analysis. */
    fun buildMoodAnalysisPrompt(recentMessages: List<String>): String {
        val messagesText = recentMessages.takeLast(6).joinToString("\n")
        return """Analyze the emotional tone of this conversation. Output ONLY a JSON object with scores from 0.0 (low) to 1.0 (high):
{"warmth": 0.0, "energy": 0.0, "humor": 0.0, "empathy": 0.0, "tension": 0.0, "intimacy": 0.0}

Conversation:
$messagesText

JSON:"""
    }

    // ==================== State Management ====================

    /**
     * Update the emotional state by blending all tier results.
     *
     * Tier weights (when all present):
     *   Probe=50%, Keyword=10%, Embedding=25%, LLM=15%
     *
     * When some tiers are absent, remaining weights are renormalized.
     * The probe (Tier 0) gets highest weight because it measures the model's
     * actual internal state rather than inferring from text.
     *
     * Also updates the EmotionRegime state machine and velocity tracking.
     */
    fun update(
        tier0: EmotionalState? = null,
        tier1: EmotionalState? = null,
        tier2: EmotionalState? = null,
        tier3: EmotionalState? = null
    ) {
        turnCount++

        // Decay current state
        var newState = _state.value.decayed()

        // Collect available tiers with their weights
        val tiers = listOfNotNull(
            tier0?.let { it to WEIGHT_PROBE },
            tier1?.let { it to WEIGHT_KEYWORD },
            tier2?.let { it to WEIGHT_EMBEDDING },
            tier3?.let { it to WEIGHT_LLM }
        )

        if (tiers.isNotEmpty()) {
            val totalWeight = tiers.sumOf { it.second.toDouble() }.toFloat()
            var avgWarmth = 0f; var avgEnergy = 0f; var avgHumor = 0f
            var avgEmpathy = 0f; var avgTension = 0f; var avgIntimacy = 0f
            for ((tierState, weight) in tiers) {
                val w = weight / totalWeight
                avgWarmth += tierState.warmth * w
                avgEnergy += tierState.energy * w
                avgHumor += tierState.humor * w
                avgEmpathy += tierState.empathy * w
                avgTension += tierState.tension * w
                avgIntimacy += tierState.intimacy * w
            }
            val tierAvg = EmotionalState(avgWarmth, avgEnergy, avgHumor, avgEmpathy, avgTension, avgIntimacy)

            // Blend with turn-level alpha (slower adaptation across turns)
            newState = newState.blend(tierAvg, TURN_ALPHA)
        }

        // Apply velocity (momentum) — smooth transitions
        val prev = _state.value
        velocity = EmotionalState(
            warmth = velocity.warmth * velocityDecay + (newState.warmth - prev.warmth) * velocityInjection,
            energy = velocity.energy * velocityDecay + (newState.energy - prev.energy) * velocityInjection,
            humor = velocity.humor * velocityDecay + (newState.humor - prev.humor) * velocityInjection,
            empathy = velocity.empathy * velocityDecay + (newState.empathy - prev.empathy) * velocityInjection,
            tension = velocity.tension * velocityDecay + (newState.tension - prev.tension) * velocityInjection,
            intimacy = velocity.intimacy * velocityDecay + (newState.intimacy - prev.intimacy) * velocityInjection
        )
        newState = EmotionalState(
            warmth = newState.warmth + velocity.warmth,
            energy = newState.energy + velocity.energy,
            humor = newState.humor + velocity.humor,
            empathy = newState.empathy + velocity.empathy,
            tension = newState.tension + velocity.tension,
            intimacy = newState.intimacy + velocity.intimacy
        ).clamped()

        _state.value = newState

        // Update regime state machine
        updateRegime(newState)

        Log.d(TAG, "Emotional state updated (turn $turnCount, regime=${_regime.value}): $newState")
    }

    /**
     * Update the emotion regime based on the dominant axis.
     * Regime changes require consecutive signals (transition cost).
     */
    private fun updateRegime(state: EmotionalState) {
        val (axis, value) = state.dominantAxis()

        val targetRegime = when {
            state.magnitude() < 0.15f -> EmotionRegime.NEUTRAL
            axis == "warmth" && value > 0.2f -> EmotionRegime.WARMING
            axis == "warmth" && value < -0.2f -> EmotionRegime.COOLING
            axis == "energy" && abs(value) > 0.2f -> EmotionRegime.EXCITED
            axis == "empathy" && value > 0.2f -> EmotionRegime.VULNERABLE
            axis == "humor" && value > 0.2f -> EmotionRegime.PLAYFUL
            axis == "tension" && value > 0.2f -> EmotionRegime.TENSE
            else -> EmotionRegime.NEUTRAL
        }

        if (targetRegime == _regime.value) {
            regimeSignalCount = 0
            return
        }

        if (targetRegime == pendingRegime) {
            regimeSignalCount++
            if (regimeSignalCount >= _regime.value.transitionCost) {
                Log.d(TAG, "Regime transition: ${_regime.value} -> $targetRegime (after $regimeSignalCount signals)")
                _regime.value = targetRegime
                regimeSignalCount = 0
                pendingRegime = EmotionRegime.NEUTRAL
            }
        } else {
            pendingRegime = targetRegime
            regimeSignalCount = 1
        }
    }

    /** Reset emotional state (new conversation). */
    fun reset() {
        _state.value = EmotionalState()
        _regime.value = EmotionRegime.NEUTRAL
        turnCount = 0
        regimeSignalCount = 0
        pendingRegime = EmotionRegime.NEUTRAL
        velocity = EmotionalState()
        lastProbeState = null
    }

    /**
     * Compute final control vector strengths: persona baseline + dynamic mood offset.
     * @param baselineAxes Persona's slider values (e.g., {"warmth": 0.7, "energy": 0.3})
     * @return Adjusted axis map with mood offset applied, clamped to [-1, 1]
     */
    fun adjustVectorsForMood(baselineAxes: Map<String, Float>): Map<String, Float> {
        val offsets = _state.value.toVectorOffsets()
        val result = baselineAxes.toMutableMap()
        for ((axis, offset) in offsets) {
            result[axis] = ((result[axis] ?: 0f) + offset).coerceIn(-1f, 1f)
        }
        return result
    }

    // ==================== Utilities ====================

    private fun cosineSimilarity(a: FloatArray, b: FloatArray): Float {
        if (a.size != b.size) return 0f
        var dot = 0f; var normA = 0f; var normB = 0f
        for (i in a.indices) {
            dot += a[i] * b[i]
            normA += a[i] * a[i]
            normB += b[i] * b[i]
        }
        val denom = sqrt(normA) * sqrt(normB)
        return if (denom > 0f) dot / denom else 0f
    }

    // ==================== Keyword Dictionaries ====================

    private val WARMTH_POS = listOf(
        "love", "adore", "miss you", "thank", "thanks", "cute", "sweet",
        "appreciate", "care", "hug", "kiss", "darling", "dear", "honey",
        "beautiful", "wonderful", "amazing", "awesome", "kind"
    )
    private val WARMTH_NEG = listOf(
        "hate", "angry", "mad", "leave me", "shut up", "go away", "annoying",
        "stupid", "idiot", "boring", "whatever", "don't care", "ugh"
    )
    private val ENERGY_POS = listOf(
        "excited", "amazing", "awesome", "let's go", "can't wait", "omg",
        "yes", "yay", "woah", "wow", "incredible", "fantastic"
    )
    private val ENERGY_NEG = listOf(
        "tired", "exhausted", "sleepy", "bored", "meh", "whatever",
        "don't feel like", "lazy", "drained"
    )
    private val HUMOR_POS = listOf(
        "lol", "haha", "lmao", "rofl", "joke", "funny", "hilarious",
        "comedy", "laugh", "hehe", "xd", "bruh"
    )
    private val EMPATHY_TRIGGERS = listOf(
        "sad", "depressed", "lonely", "struggling", "worried", "scared",
        "afraid", "anxious", "hurt", "pain", "crying", "lost", "confused",
        "overwhelmed", "stressed", "help me", "don't know what to do"
    )
    private val TENSION_POS = listOf(
        "angry", "fight", "argue", "disagree", "wrong", "unfair",
        "frustrated", "annoyed", "upset", "conflict", "problem"
    )
    private val TENSION_NEG = listOf(
        "peace", "calm", "relax", "fine", "okay", "no worries",
        "all good", "resolved", "better now"
    )
    private val INTIMACY_POS = listOf(
        "tell me about you", "how do you feel", "between us", "secret",
        "personal", "private", "trust you", "close to you", "feelings",
        "open up", "vulnerable", "honestly"
    )
}
