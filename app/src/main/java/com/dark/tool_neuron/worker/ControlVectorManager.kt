package com.dark.tool_neuron.worker

import android.content.Context
import android.util.Log
import com.dark.tool_neuron.engine.EmotionalStateTracker
import com.mp.ai_gguf.GGUFNativeLib
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Orchestrates all 6 runtime behavior intervention systems from a single .personality file.
 *
 * Systems:
 *   A. Control Vectors — per-layer activation steering computed from contrastive prompts
 *   B. Logit Bias — token-level probability adjustment (boost/suppress specific words)
 *   C. Attention Bias — boost attention to persona/system prompt tokens (API ready, graph TBD)
 *   D. Head Rescaling — per-head scalar multiplier on attention output, probed from direction vectors
 *   E. Attention Temperature — per-head softmax sharpness profile
 *   F. Fast Weight Memory — Hopfield-style associative memory that updates every token
 *  P5. Dynamic Sparse Masks — per-layer FFN neuron masking based on activation magnitudes
 *  P6. KAN-lite — learnable activation overlay (piecewise-linear spline residual in FFN)
 *  P7. Forward-only Learning — SPSA perturbation tunes KAN coefficients between turns
 *
 * The .personality file is universal — works with any GGUF model. All numerical
 * values are computed at runtime from the loaded model. Results are cached per model hash.
 */
class ControlVectorManager(private val context: Context) {

    companion object {
        private const val TAG = "ControlVectorManager"
        private const val CACHE_DIR = "personality_cache"

        /** Default fast weight parameters — tuned for mobile. */
        private const val FAST_WEIGHT_DIM = 128    // reduced dimension (128² × 4 = 64KB)
        private const val FAST_WEIGHT_GAMMA = 0.995f  // ~200-token memory horizon
        private const val FAST_WEIGHT_ETA = 0.01f
        private const val FAST_WEIGHT_INJECT = 0.1f

        /** KAN-lite initial alpha — gentle overlay until tuned by forward-only learning. */
        private const val KAN_INITIAL_ALPHA = 0.1f

        /** Hypernetwork defaults — rank-4 LoRA, moderate strength. */
        private const val HYPER_RANK = 4
        private const val HYPER_STRENGTH = 0.3f

        /** Sparse mask defaults — keep 90% of neurons, update every 64 tokens. */
        private const val SPARSE_KEEP_RATIO = 0.90f
        private const val SPARSE_MOMENTUM = 0.95f
        private const val SPARSE_UPDATE_INTERVAL = 64

        /** LayerNorm shift scale — direction vectors scaled down for norm use.
         *  0.02 means norm offsets are ~2% of control vector magnitude. */
        private const val NORM_OFFSET_SCALE = 0.02f

        /** Emotion-conditioned dimensional gating — sigmoid sharpness.
         *  3.0 = moderate (smooth dimension selection).
         *  5.0 = sharp (near-binary dimension selection). */
        private const val EMOTION_GATE_SCALE = 3.0f

        /** The 6 standard personality axes. */
        val PERSONALITY_AXES = listOf(
            AxisInfo("warmth", "Warmth", "cold", "warm"),
            AxisInfo("energy", "Energy", "calm", "energetic"),
            AxisInfo("humor", "Humor", "serious", "playful"),
            AxisInfo("formality", "Formality", "casual", "formal"),
            AxisInfo("verbosity", "Verbosity", "verbose", "concise"),
            AxisInfo("emotion", "Emotion", "stoic", "expressive")
        )
    }

    data class AxisInfo(
        val id: String,
        val label: String,
        val negativePole: String,
        val positivePole: String
    )

    private val nativeLib = GGUFNativeLib()
    private var personalityConfig: JSONObject? = null
    private var lastAppliedStrengths: Map<String, Float> = emptyMap()
    private var fastWeightsInitialized = false
    private var kanInitialized = false
    private var hypernetworkInitialized = false
    private var sparseMasksInitialized = false
    private var tokensSinceLastMaskUpdate = 0

    /** Current emotional state for dimensional gating. Updated by EmotionalStateTracker. */
    private var currentEmotionStrengths: Map<String, Float> = emptyMap()

    /** Emotional state tracker for the probe → update → feedback loop. Set by caller. */
    var emotionalStateTracker: EmotionalStateTracker? = null

    /** Whether activation capture is enabled for emotion probing. */
    private var captureEnabled = false

    /** Cache directory for computed direction vectors (per model hash). */
    private val cacheDir: File
        get() = File(context.filesDir, CACHE_DIR).also { it.mkdirs() }

    /** Path to the intervention state file for the current model. */
    private var interventionStatePath: String? = null

    /**
     * Set the model hash for intervention state persistence.
     * Call after model load with the model's unique identifier.
     * Automatically loads any previously saved intervention state (KAN, sparse masks).
     */
    fun setModelHash(modelHash: String) {
        interventionStatePath = File(cacheDir, "${modelHash}_intervention.bin").absolutePath
        loadInterventionState()
    }

    /**
     * Save learnable intervention state (KAN coefficients, sparse masks) to disk.
     * Call after each generation turn or on app pause.
     */
    fun saveInterventionState() {
        val path = interventionStatePath ?: return
        try {
            val success = nativeLib.nativeSaveInterventionState(path)
            if (success) {
                Log.d(TAG, "Intervention state saved to $path")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save intervention state: ${e.message}")
        }
    }

    /**
     * Load previously saved intervention state from disk.
     * Called automatically by setModelHash(). Restores KAN coefficients and sparse masks
     * so P7 learning progress survives app restarts.
     */
    private fun loadInterventionState() {
        val path = interventionStatePath ?: return
        try {
            val success = nativeLib.nativeLoadInterventionState(path)
            if (success) {
                kanInitialized = true
                sparseMasksInitialized = true
                hypernetworkInitialized = true
                Log.i(TAG, "Intervention state restored from $path")
            } else {
                Log.d(TAG, "No saved intervention state (first run for this model)")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load intervention state: ${e.message}")
        }
    }

    /**
     * Load the personality config from the bundled asset file.
     * Call this once at app startup or model load.
     */
    fun loadPersonalityConfig() {
        try {
            val json = context.assets.open("default_personality.json")
                .bufferedReader().use { it.readText() }
            personalityConfig = JSONObject(json)
            Log.i(TAG, "Personality config loaded (version ${personalityConfig?.optInt("version", 0)})")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load personality config: ${e.message}")
        }
    }

    /**
     * Apply all personality interventions for the given axis strengths.
     * This is the main entry point — call after model load or when sliders change.
     *
     * Applies all systems in order:
     *   A. Control vectors (runtime compute + cache)
     *   B. Logit biases (token-level)
     *   D. Head rescaling (probed from cached direction vectors)
     *   E. Attention temperature profile
     *   F. Fast weight memory init (if not already initialized)
     *   G. LayerNorm affine shift (cheapest personality mod, zero flash-attn penalty)
     *  P5. Sparse mask init (all neurons active, updated periodically)
     *  P6. KAN-lite init (coefficients start at 0, tuned by P7 forward-only learning)
     *
     * @param axisStrengths Map of axis id → strength (-1.0 to +1.0), 0 = neutral
     */
    fun applyPersonality(axisStrengths: Map<String, Float>) {
        val config = personalityConfig
        if (config == null) {
            Log.w(TAG, "No personality config loaded, loading now...")
            loadPersonalityConfig()
        }

        lastAppliedStrengths = axisStrengths
        val anyActive = axisStrengths.any { it.value != 0f }

        // System A: Control Vectors (also caches direction vectors for head probing)
        applyControlVectors(axisStrengths)

        // System B: Logit Biases
        applyLogitBiases(axisStrengths)

        // System D: Head Rescaling (uses cached direction vectors from System A)
        applyHeadScaling(axisStrengths)

        // System E: Attention Temperature Profile
        applyAttentionTemperature(axisStrengths)

        // Gated Residual: Per-layer scalar gates on attention and FFN outputs
        applyResidualGates(axisStrengths)

        // System F: Fast Weight Memory (init once, persists across calls)
        initFastWeightsIfNeeded()

        // System G: LayerNorm Affine Shift (uses cached direction vectors from System A)
        applyNormOffsets(axisStrengths)

        // System P4: Hypernetwork FFN LoRA (init once — uses direction vectors for warm-start)
        initHypernetworkIfNeeded(axisStrengths)

        // System P5: Dynamic sparse masks (init once — all neurons active initially)
        initSparseMasksIfNeeded()

        // System P6: KAN-lite (init once — coefficients start at zero, tuned by P7 learning)
        initKanIfNeeded()

        // Enable activation capture for Tier 0 emotion probing
        enableEmotionProbing(anyActive)

        if (!anyActive) {
            Log.i(TAG, "All axes neutral — interventions cleared")
        }
    }

    /**
     * System A: Compute and apply control vectors from contrastive prompts.
     * Vectors are computed from the loaded model's own hidden states and cached.
     *
     * If an emotion state is available (from EmotionalStateTracker), applies
     * emotion-conditioned dimensional gating: different emotions activate different
     * embedding dimensions of the control vector, not just scalar strength scaling.
     */
    private fun applyControlVectors(axisStrengths: Map<String, Float>) {
        val config = personalityConfig ?: return
        val prompts = config.optJSONObject("control_vector_prompts") ?: return

        // Build prompts JSON for JNI — ensure direction vectors are cached
        val promptsObj = JSONObject()
        for (axis in PERSONALITY_AXES) {
            val axisPrompts = prompts.optJSONObject(axis.id) ?: continue
            val posArray = axisPrompts.optJSONArray("positive") ?: continue
            val negArray = axisPrompts.optJSONArray("negative") ?: continue
            promptsObj.put(axis.id, JSONObject().apply {
                put("positive", posArray)
                put("negative", negArray)
            })
        }

        // Build strengths JSON
        val strengthsObj = JSONObject()
        for ((axis, strength) in axisStrengths) {
            strengthsObj.put(axis, strength.toDouble())
        }

        try {
            // Always run the base compute first to ensure direction vectors are cached
            val success = nativeLib.nativeComputePersonalityVectors(
                promptsObj.toString(),
                strengthsObj.toString(),
                cacheDir.absolutePath
            )

            if (!success) {
                Log.e(TAG, "Control vectors: FAILED")
                return
            }

            // If emotion state is available, re-apply with dimensional gating
            if (currentEmotionStrengths.isNotEmpty()) {
                val emotionObj = JSONObject()
                for ((axis, strength) in currentEmotionStrengths) {
                    emotionObj.put(axis, strength.toDouble())
                }

                val gatedSuccess = nativeLib.nativeApplyEmotionGatedVectors(
                    strengthsObj.toString(),
                    emotionObj.toString(),
                    cacheDir.absolutePath,
                    EMOTION_GATE_SCALE
                )
                Log.i(TAG, "Control vectors: applied with emotion gating (${if (gatedSuccess) "OK" else "FAILED"})")
            } else {
                Log.i(TAG, "Control vectors: applied (no emotion gating)")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Control vectors error: ${e.message}")
        }
    }

    /**
     * System B: Apply logit biases based on personality axes.
     * Resolves text tokens to IDs at runtime via the model's tokenizer.
     */
    private fun applyLogitBiases(axisStrengths: Map<String, Float>) {
        val config = personalityConfig ?: return
        val tokenBiases = config.optJSONObject("token_biases") ?: return

        val biasArray = JSONArray()

        for ((axis, strength) in axisStrengths) {
            if (strength == 0f) continue
            val axisBiases = tokenBiases.optJSONObject(axis) ?: continue

            // Boost tokens
            val boost = axisBiases.optJSONObject("boost")
            if (boost != null) {
                val keys = boost.keys()
                while (keys.hasNext()) {
                    val token = keys.next()
                    val baseBias = boost.getDouble(token).toFloat()
                    biasArray.put(JSONObject().apply {
                        put("token", token)
                        put("bias", (baseBias * strength).toDouble())
                    })
                }
            }

            // Suppress tokens (negative biases, also scaled by strength)
            val suppress = axisBiases.optJSONObject("suppress")
            if (suppress != null) {
                val keys = suppress.keys()
                while (keys.hasNext()) {
                    val token = keys.next()
                    val baseBias = suppress.getDouble(token).toFloat()
                    biasArray.put(JSONObject().apply {
                        put("token", token)
                        put("bias", (baseBias * strength).toDouble())
                    })
                }
            }
        }

        try {
            val json = if (biasArray.length() > 0) biasArray.toString() else "[]"
            nativeLib.nativeSetLogitBias(json)
            Log.d(TAG, "Logit biases: ${biasArray.length()} entries")
        } catch (e: Exception) {
            Log.e(TAG, "Logit biases error: ${e.message}")
        }
    }

    /**
     * System D: Probe head importance from cached direction vectors and apply head scales.
     *
     * This reads the per-axis direction vectors cached by System A, computes per-layer
     * importance (L2 norm of the direction), and scales attention heads accordingly:
     *   - High-importance layers (>60%): heads boosted up to 1.5x
     *   - Low-importance layers (<25%): heads gently suppressed down to 0.7x
     *   - Middle layers: untouched (preserves flash attention for zero perf overhead)
     */
    private fun applyHeadScaling(axisStrengths: Map<String, Float>) {
        val anyActive = axisStrengths.any { kotlin.math.abs(it.value) > 0.01f }

        if (!anyActive) {
            try { nativeLib.nativeResetHeadScales() } catch (_: Exception) {}
            return
        }

        val strengthsObj = JSONObject()
        for ((axis, strength) in axisStrengths) {
            strengthsObj.put(axis, strength.toDouble())
        }

        try {
            val resultJson = nativeLib.nativeProbeAndSetHeadScales(
                strengthsObj.toString(),
                cacheDir.absolutePath
            )
            val result = JSONObject(resultJson)
            if (result.optBoolean("success", false)) {
                val nScaled = result.optInt("n_scaled", 0)
                val nTotal = result.optInt("n_total", 0)
                Log.i(TAG, "Head scaling: $nScaled/$nTotal layers scaled")
            } else {
                Log.w(TAG, "Head scaling failed: ${result.optString("error", "unknown")}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Head scaling error: ${e.message}")
        }
    }

    /**
     * System E: Apply attention temperature profile.
     * Personality strength scales the temperature deviation from 1.0.
     * Strong personality → more pronounced temperature profile.
     */
    private fun applyAttentionTemperature(axisStrengths: Map<String, Float>) {
        val config = personalityConfig ?: return
        val profile = config.optJSONObject("attention_profile") ?: return

        // Compute overall personality strength (average of absolute axis strengths)
        val avgStrength = axisStrengths.values.map { kotlin.math.abs(it) }
            .average().toFloat()

        if (avgStrength < 0.05f) {
            // Too weak — reset temperatures
            try { nativeLib.nativeResetAttentionTemperatures() } catch (_: Exception) {}
            return
        }

        // Scale temperature deviations by personality strength
        val baseEarly = profile.optDouble("early_layers_temperature", 1.0).toFloat()
        val baseMid = profile.optDouble("mid_layers_temperature", 1.0).toFloat()
        val baseLate = profile.optDouble("late_layers_temperature", 1.0).toFloat()

        // Interpolate toward baseline (1.0) based on 1 - avgStrength
        val t = avgStrength.coerceIn(0f, 1f)
        val early = 1.0f + (baseEarly - 1.0f) * t
        val mid = 1.0f + (baseMid - 1.0f) * t
        val late = 1.0f + (baseLate - 1.0f) * t

        try {
            val profileJson = JSONObject().apply {
                put("early", early.toDouble())
                put("mid", mid.toDouble())
                put("late", late.toDouble())
            }
            nativeLib.nativeSetAttentionTemperatureProfile(profileJson.toString())
            Log.d(TAG, "Attn temperature: early=%.2f mid=%.2f late=%.2f (strength=%.2f)".format(early, mid, late, avgStrength))
        } catch (e: Exception) {
            Log.e(TAG, "Attention temperature error: ${e.message}")
        }
    }

    /**
     * Gated Residual: Apply per-layer scalar gates on attention and FFN outputs.
     *
     * Strategy based on direction vector importance (same data as head probing):
     *   - High-importance layers (>60%): FFN gate boosted (up to 1.4x) to amplify personality
     *   - Low-importance layers (<25%): FFN gate suppressed (down to 0.5) to skip redundant compute
     *   - Middle layers: gate = 1.0 (no change)
     *   - Attn gates are more conservative: only boost high-importance layers (up to 1.2x)
     *
     * Cost: ~0.064ms/token for 32 layers (one ggml_scale per layer per sub-block).
     * Memory: 256 bytes for 32 layers (2 × 32 × 4 bytes).
     * Flash attention: fully compatible (gate is outside the attention kernel).
     */
    private fun applyResidualGates(axisStrengths: Map<String, Float>) {
        val anyActive = axisStrengths.any { kotlin.math.abs(it.value) > 0.01f }

        if (!anyActive) {
            try { nativeLib.nativeResetResidualGates() } catch (_: Exception) {}
            return
        }

        val strengthsObj = JSONObject()
        for ((axis, strength) in axisStrengths) {
            strengthsObj.put(axis, strength.toDouble())
        }

        try {
            // Use same cached direction vectors as head probing to compute layer importance
            val resultJson = nativeLib.nativeProbeAndSetHeadScales(
                strengthsObj.toString(),
                cacheDir.absolutePath
            )
            val result = JSONObject(resultJson)
            if (!result.optBoolean("success", false)) return

            val impArray = result.optJSONArray("layer_importance") ?: return
            val nLayer = impArray.length()
            if (nLayer == 0) return

            val avgStrength = axisStrengths.values.map { kotlin.math.abs(it) }
                .average().toFloat().coerceIn(0f, 1f)

            val attnGates = org.json.JSONArray()
            val ffnGates = org.json.JSONArray()

            for (il in 0 until nLayer) {
                val imp = impArray.optDouble(il, 0.0).toFloat()

                // FFN gates: more aggressive — personality is largely in FFN layers
                val ffnGate = when {
                    imp > 0.6f -> 1.0f + 0.4f * avgStrength * imp   // boost up to ~1.4x
                    imp < 0.25f -> 1.0f - 0.5f * avgStrength * (1.0f - imp) // suppress to ~0.5x
                    else -> 1.0f
                }.coerceIn(0.0f, 2.0f)

                // Attn gates: more conservative — attention is shared across behaviors
                val attnGate = when {
                    imp > 0.6f -> 1.0f + 0.2f * avgStrength * imp   // gentle boost up to ~1.2x
                    else -> 1.0f
                }.coerceIn(0.0f, 2.0f)

                attnGates.put(attnGate.toDouble())
                ffnGates.put(ffnGate.toDouble())
            }

            val gatesJson = JSONObject().apply {
                put("attn", attnGates)
                put("ffn", ffnGates)
            }
            nativeLib.nativeSetResidualGates(gatesJson.toString())
            Log.d(TAG, "Residual gates set ($nLayer layers, avgStrength=%.2f)".format(avgStrength))
        } catch (e: Exception) {
            Log.e(TAG, "Residual gates error: ${e.message}")
        }
    }

    /**
     * System G: Apply LayerNorm affine shift offsets from cached direction vectors.
     * Cheapest personality modification — one element-wise add per layer, zero flash-attn penalty.
     * Uses the same cached direction vectors computed by System A, scaled down for norm use.
     */
    private fun applyNormOffsets(axisStrengths: Map<String, Float>) {
        val anyActive = axisStrengths.any { kotlin.math.abs(it.value) > 0.01f }

        if (!anyActive) {
            try { nativeLib.nativeResetNormOffsets() } catch (_: Exception) {}
            return
        }

        val strengthsObj = JSONObject()
        for ((axis, strength) in axisStrengths) {
            strengthsObj.put(axis, strength.toDouble())
        }

        try {
            val resultJson = nativeLib.nativeSetNormOffsets(
                strengthsObj.toString(),
                cacheDir.absolutePath,
                NORM_OFFSET_SCALE
            )
            val result = JSONObject(resultJson)
            if (result.optBoolean("success", false)) {
                val nSet = result.optInt("n_layers_set", 0)
                Log.i(TAG, "Norm offsets: $nSet layers set (scale=$NORM_OFFSET_SCALE)")
            } else {
                Log.w(TAG, "Norm offsets failed: ${result.optString("error", "unknown")}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Norm offsets error: ${e.message}")
        }
    }

    /**
     * System F: Initialize fast weight memory if not already initialized.
     * The fast weight memory is a Hopfield-style associative memory that
     * auto-updates each token, giving the model a "conversation memory"
     * that doesn't grow with sequence length.
     */
    private fun initFastWeightsIfNeeded() {
        if (fastWeightsInitialized) return
        try {
            val success = nativeLib.nativeFastWeightInit(
                FAST_WEIGHT_DIM,
                FAST_WEIGHT_GAMMA,
                FAST_WEIGHT_ETA,
                FAST_WEIGHT_INJECT
            )
            if (success) {
                fastWeightsInitialized = true
                Log.i(TAG, "Fast weight memory initialized (dim=$FAST_WEIGHT_DIM, γ=$FAST_WEIGHT_GAMMA)")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Fast weight init error: ${e.message}")
        }
    }

    /**
     * Call after each generated token to update the fast weight memory.
     * This writes the current activation pattern into the associative memory.
     */
    fun onTokenGenerated() {
        if (!fastWeightsInitialized) return
        try {
            nativeLib.nativeFastWeightUpdate()
        } catch (_: Exception) {}
    }

    /**
     * System P5: Initialize dynamic sparse masks.
     * All neurons start active (mask = 1.0). Masks are updated periodically
     * by calling updateSparseMasks() with sample text.
     */
    private fun initSparseMasksIfNeeded() {
        if (sparseMasksInitialized) return
        try {
            val success = nativeLib.nativeInitSparseMasks(1.0f) // all active initially
            if (success) {
                sparseMasksInitialized = true
                Log.i(TAG, "Sparse masks initialized (all neurons active)")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Sparse mask init error: ${e.message}")
        }
    }

    /**
     * System P5: Update sparse masks based on recent text.
     * Call periodically (e.g., every 64 tokens) with recent conversation context.
     *
     * @param sampleText Recent conversation text to analyze activations
     * @return Average sparsity ratio, or null on error
     */
    fun updateSparseMasks(sampleText: String): Float? {
        if (!sparseMasksInitialized || sampleText.length < 20) return null
        return try {
            val resultJson = nativeLib.nativeUpdateSparseMasks(
                text = sampleText,
                keepRatio = SPARSE_KEEP_RATIO,
                momentum = SPARSE_MOMENTUM
            )
            val result = JSONObject(resultJson)
            if (result.optBoolean("success", false)) {
                val sparsity = result.optDouble("avg_sparsity", 0.0).toFloat()
                Log.i(TAG, "Sparse masks updated: avg sparsity=%.1f%%".format(sparsity * 100))
                sparsity
            } else {
                Log.w(TAG, "Sparse mask update failed: ${result.optString("error", "unknown")}")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Sparse mask update error: ${e.message}")
            null
        }
    }

    /**
     * Track tokens for periodic sparse mask updates.
     * Call after each generated token.
     */
    fun onTokenGeneratedForMaskUpdate(recentText: String) {
        tokensSinceLastMaskUpdate++
        if (tokensSinceLastMaskUpdate >= SPARSE_UPDATE_INTERVAL) {
            tokensSinceLastMaskUpdate = 0
            updateSparseMasks(recentText)
        }
    }

    /**
     * System P4: Initialize hypernetwork FFN LoRA.
     * Uses cached direction vectors (from System A) for warm-start initialization.
     * LoRA B starts at zero → no initial effect, trained over time by P7 learning.
     */
    private fun initHypernetworkIfNeeded(axisStrengths: Map<String, Float>) {
        if (hypernetworkInitialized) return
        val anyActive = axisStrengths.any { kotlin.math.abs(it.value) > 0.01f }
        if (!anyActive) return

        try {
            val strengthsObj = JSONObject()
            for ((axis, strength) in axisStrengths) {
                strengthsObj.put(axis, strength.toDouble())
            }

            val resultJson = nativeLib.nativeInitHypernetworkFromDirections(
                strengthsJson = strengthsObj.toString(),
                cacheDir = cacheDir.absolutePath,
                rank = HYPER_RANK,
                strength = HYPER_STRENGTH
            )
            val result = JSONObject(resultJson)
            if (result.optBoolean("success", false)) {
                hypernetworkInitialized = true
                val nTarget = result.optInt("n_target_layers", 0)
                val start = result.optInt("layer_start", 0)
                val end = result.optInt("layer_end", 0)
                Log.i(TAG, "Hypernetwork initialized: $nTarget target layers [$start, $end), rank=$HYPER_RANK")
            } else {
                Log.w(TAG, "Hypernetwork init failed: ${result.optString("error", "unknown")}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Hypernetwork init error: ${e.message}")
        }
    }

    /**
     * System P6: Initialize KAN-lite learnable activation overlay.
     * Coefficients start at zero (identity — no modification to base activation).
     * Once initialized, the forward-only learner (P7) tunes coefficients over time.
     */
    private fun initKanIfNeeded() {
        if (kanInitialized) return
        try {
            val success = nativeLib.nativeInitKan(KAN_INITIAL_ALPHA)
            if (success) {
                kanInitialized = true
                Log.i(TAG, "KAN-lite initialized (alpha=$KAN_INITIAL_ALPHA)")
            }
        } catch (e: Exception) {
            Log.e(TAG, "KAN init error: ${e.message}")
        }
    }

    /**
     * Clear all personality interventions, returning to base model behavior.
     */
    fun clearAll() {
        try {
            nativeLib.nativeClearControlVector()
            nativeLib.nativeSetLogitBias("[]")
            nativeLib.nativeResetHeadScales()
            nativeLib.nativeResetAttentionTemperatures()
            nativeLib.nativeResetResidualGates()
            nativeLib.nativeClearAttentionBias()
            nativeLib.nativeFastWeightReset()
            nativeLib.nativeResetNormOffsets()
            nativeLib.nativeResetHypernetwork()
            hypernetworkInitialized = false
            nativeLib.nativeResetSparseMasks()
            sparseMasksInitialized = false
            tokensSinceLastMaskUpdate = 0
            nativeLib.nativeResetKan()
            kanInitialized = false
            enableEmotionProbing(false)
            currentEmotionStrengths = emptyMap()
            lastAppliedStrengths = emptyMap()
            Log.i(TAG, "All personality interventions cleared")
        } catch (e: Exception) {
            Log.e(TAG, "Error clearing interventions: ${e.message}")
        }
    }

    /**
     * Update the current emotional state for dimensional gating.
     *
     * Called by EmotionalStateTracker when the detected mood changes.
     * Immediately re-applies control vectors with updated dimensional gating,
     * so different embedding dimensions activate based on the current emotion.
     *
     * @param emotionStrengths Map of axis id → current emotional strength (-1.0 to +1.0)
     */
    fun updateEmotionState(emotionStrengths: Map<String, Float>) {
        currentEmotionStrengths = emotionStrengths

        // Re-apply control vectors with updated gating if we have active personality
        val persona = lastAppliedStrengths
        if (persona.isEmpty() || persona.all { it.value == 0f }) return

        val personaObj = JSONObject()
        for ((axis, strength) in persona) {
            personaObj.put(axis, strength.toDouble())
        }

        val emotionObj = JSONObject()
        for ((axis, strength) in emotionStrengths) {
            emotionObj.put(axis, strength.toDouble())
        }

        try {
            nativeLib.nativeApplyEmotionGatedVectors(
                personaObj.toString(),
                emotionObj.toString(),
                cacheDir.absolutePath,
                EMOTION_GATE_SCALE
            )
            Log.d(TAG, "Emotion gating updated: ${emotionStrengths.entries.joinToString { "${it.key}=%.2f".format(it.value) }}")
        } catch (e: Exception) {
            Log.e(TAG, "Emotion gating update error: ${e.message}")
        }
    }

    /**
     * Reset fast weight memory only (e.g., on new conversation).
     * Keeps personality settings but clears conversation-specific memory.
     */
    fun resetConversationMemory() {
        try {
            nativeLib.nativeFastWeightReset()
            Log.i(TAG, "Conversation memory reset")
        } catch (_: Exception) {}
    }

    /** Get the last applied axis strengths. */
    fun getLastAppliedStrengths(): Map<String, Float> = lastAppliedStrengths

    /** Get fast weight memory state for debugging/UI. */
    fun getFastWeightState(): String? {
        return try {
            nativeLib.nativeFastWeightGetState()
        } catch (_: Exception) {
            null
        }
    }

    /**
     * System P7: Forward-only learning step.
     * Call between conversation turns with the last assistant response text.
     * Tunes KAN coefficients via SPSA perturbation (2 forward passes, ~0.5-1s on mobile).
     *
     * @param lastResponse The last generated assistant response text
     * @return Estimated improvement (positive = better), or null on error
     */
    fun learnFromResponse(lastResponse: String): Float? {
        if (!kanInitialized || lastResponse.length < 20) return null
        return try {
            val resultJson = nativeLib.nativeForwardLearnStep(
                text = lastResponse,
                learningRate = 0.005f,
                noiseScale = 0.05f,
                maxTokens = 128
            )
            val result = JSONObject(resultJson)
            if (result.optBoolean("success", false)) {
                val improvement = result.optDouble("improvement", 0.0).toFloat()
                val nTokens = result.optInt("n_tokens", 0)
                Log.i(TAG, "Forward-only learning: improvement=%.4f, tokens=%d".format(improvement, nTokens))
                // Auto-save after learning so progress persists
                saveInterventionState()
                improvement
            } else {
                Log.w(TAG, "Forward-only learning failed: ${result.optString("error", "unknown")}")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Forward-only learning error: ${e.message}")
            null
        }
    }

    // ==================== Emotion Probing Integration ====================

    /**
     * Enable/disable activation capture for Tier 0 emotion probing.
     * When active, each decode stores per-layer activations (~86KB for 24-layer model).
     * Only enabled when personality axes are active AND a tracker is attached.
     */
    private fun enableEmotionProbing(enabled: Boolean) {
        val shouldCapture = enabled && emotionalStateTracker != null
        if (shouldCapture == captureEnabled) return

        try {
            nativeLib.nativeSetCaptureEnabled(shouldCapture)
            captureEnabled = shouldCapture
            Log.d(TAG, "Emotion probing ${if (shouldCapture) "enabled" else "disabled"}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to toggle capture: ${e.message}")
        }
    }

    /**
     * Post-generation hook: probe the model's internal emotional state and update
     * the full emotion tracking pipeline.
     *
     * Call this after each generation turn completes. It:
     *   1. Probes residual stream for Tier 0 (model's actual emotional state)
     *   2. Runs keyword analysis for Tier 1 (user's emotional tone)
     *   3. Updates the EmotionalStateTracker with weighted blend
     *   4. Computes feedback correction (probe vs. persona target)
     *   5. Re-applies emotion-gated control vectors with corrected strengths
     *   6. Runs forward-only learning (P7) on the response
     *   7. Saves intervention state
     *
     * @param lastResponse The assistant's generated response text
     * @param userMessage The user's input message (for Tier 1 keyword analysis)
     */
    fun onGenerationTurnComplete(lastResponse: String, userMessage: String) {
        val tracker = emotionalStateTracker ?: return
        val persona = lastAppliedStrengths
        if (persona.isEmpty() || persona.all { it.value == 0f }) return

        // Tier 0: Residual stream probe (ground truth from model internals)
        var tier0: EmotionalStateTracker.EmotionalState? = null
        if (captureEnabled) {
            try {
                val probeJson = nativeLib.nativeProbeEmotionAxes(cacheDir.absolutePath)
                tier0 = tracker.parseProbeResult(probeJson)
                if (tier0 != null) {
                    Log.d(TAG, "Probe result: w=%.2f e=%.2f h=%.2f em=%.2f".format(
                        tier0.warmth, tier0.energy, tier0.humor, tier0.empathy
                    ))
                }
            } catch (e: Exception) {
                Log.w(TAG, "Probe failed: ${e.message}")
            }
        }

        // Tier 1: Fast keyword analysis on user message
        val tier1 = tracker.analyzeKeywords(userMessage)

        // Update emotional state tracker with all available tiers
        tracker.update(tier0 = tier0, tier1 = tier1)

        // Compute feedback correction: compare model's actual state to persona target
        val correction = tracker.computeFeedbackCorrection(persona)

        // Build corrected emotion strengths for dimensional gating
        val emotionState = tracker.state.value
        val emotionMap = emotionState.toVectorOffsets().toMutableMap()

        // Add feedback correction to emotion state — nudges interventions toward target
        for ((axis, delta) in correction) {
            emotionMap[axis] = (emotionMap[axis] ?: 0f) + delta
        }

        // Re-apply emotion-gated control vectors with updated state
        updateEmotionState(emotionMap)

        Log.d(TAG, "Turn complete: regime=${tracker.regime.value}, " +
                "correction=${correction.entries.joinToString { "${it.key}=%.3f".format(it.value) }}")

        // P7: Forward-only learning on the response (tunes KAN coefficients)
        learnFromResponse(lastResponse)
    }
}
