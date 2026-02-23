package com.dark.tool_neuron.models.engine_schema

import kotlinx.serialization.Serializable

/**
 * Per-character sampling profile for the Persona Engine.
 *
 * All fields are nullable — `null` means "inherit from model config" (override pattern).
 * Only non-null fields override the model's default inference params.
 *
 * Example JSON (stored in Persona.samplingProfile):
 * ```json
 * {
 *   "temperature": 0.85,
 *   "minP": 0.03,
 *   "maxTokens": 100,
 *   "repeatPenalty": 1.05,
 *   "dryMultiplier": 0.8,
 *   "xtcProbability": 0.5,
 *   "stopStrings": ["\n\n"],
 *   "bannedTokens": ["certainly", "Moreover", "delve"]
 * }
 * ```
 */
@Serializable
data class PersonaSamplingProfile(
    // Base sampling
    val temperature: Float? = null,
    val topK: Int? = null,
    val topP: Float? = null,
    val minP: Float? = null,
    val maxTokens: Int? = null,
    val mirostat: Int? = null,
    val mirostatTau: Float? = null,
    val seed: Int? = null,

    // Repetition penalties
    val repeatPenalty: Float? = null,
    val frequencyPenalty: Float? = null,
    val presencePenalty: Float? = null,
    val penaltyLastN: Int? = null,

    // DRY sampler — kills repetitive n-gram patterns
    val dryMultiplier: Float? = null,
    val dryBase: Float? = null,
    val dryAllowedLength: Int? = null,
    val dryPenaltyLastN: Int? = null,

    // XTC sampler — forces creative word choices
    val xtcProbability: Float? = null,
    val xtcThreshold: Float? = null,

    // Stop strings — e.g., ["\n\n"] for short conversational responses
    val stopStrings: List<String>? = null,

    // Banned tokens — suppressed via logit bias (e.g., ["certainly", "delve"])
    val bannedTokens: List<String>? = null
)
