package com.mp.ai_gguf.toolcalling

/**
 * Configuration for tool calling behavior.
 */
data class ToolCallingConfig(
    val useTypedGrammar: Boolean = true,
    val maxRounds: Int = 3,
    val maxTokensPerTurn: Int = 512
)
