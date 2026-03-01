package com.mp.ai_gguf.toolcalling

/**
 * Grammar enforcement mode for tool calling.
 */
enum class GrammarMode(val value: Int) {
    STRICT(0),  // Forces JSON tool call output
    LAZY(1)     // Model chooses text or tool call
}
