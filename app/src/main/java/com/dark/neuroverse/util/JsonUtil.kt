package com.dark.neuroverse.util

fun extractPureJson(raw: String): String {
    // 1) If there's a ```json fence, remove everything before the first `{`
    val startFence = raw.indexOf("```json")
    val startBrace = raw.indexOf('{', if (startFence >= 0) startFence else 0)
    // 2) Find the last `}` in the whole string
    val endBrace   = raw.lastIndexOf('}')
    if (startBrace >= 0 && endBrace > startBrace) {
        return raw.substring(startBrace, endBrace + 1).trim()
    }
    // fallback: if no braces found, return the raw string (will error on parsing)
    return raw.trim()
}