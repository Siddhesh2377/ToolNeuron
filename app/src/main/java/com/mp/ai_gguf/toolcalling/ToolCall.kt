package com.mp.ai_gguf.toolcalling

import org.json.JSONObject

/**
 * Represents a parsed tool call from model output.
 */
data class ToolCall(
    val name: String,
    val arguments: JSONObject = JSONObject()
) {
    fun getString(key: String, default: String = ""): String {
        return arguments.optString(key, default)
    }

    fun getInt(key: String, default: Int = 0): Int {
        return arguments.optInt(key, default)
    }

    fun getDouble(key: String, default: Double = 0.0): Double {
        return arguments.optDouble(key, default)
    }

    fun getBoolean(key: String, default: Boolean = false): Boolean {
        return arguments.optBoolean(key, default)
    }

    fun has(key: String): Boolean {
        return arguments.has(key)
    }
}
