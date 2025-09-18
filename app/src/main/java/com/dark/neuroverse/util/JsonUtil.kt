package com.dark.neuroverse.util

import com.dark.neuroverse.model.ToolOutput
import com.dark.neuroverse.model.ToolOutputType
import org.json.JSONObject

fun extractPureJson(raw: String): String {
    // 1) If there's a ```json fence, remove everything before the first `{`
    val startFence = raw.indexOf("```json")
    val startBrace = raw.indexOf('{', if (startFence >= 0) startFence else 0)
    // 2) Find the last `}` in the whole string
    val endBrace = raw.lastIndexOf('}')
    if (startBrace >= 0 && endBrace > startBrace) {
        return raw.substring(startBrace, endBrace + 1).trim()
    }
    // fallback: if no braces found, return the raw string (will error on parsing)
    return raw.trim()
}

fun readToolOutputJson(json: String): JSONObject? {
    return try {
        JSONObject(json)
    } catch (err: Throwable) {
        null
    }
}

fun writeToolOutputJson(json: String): ToolOutput? {
    return try {
        val root = JSONObject(json)
        ToolOutput(
            root.getString("name"),
            identifyTheToolType(root.getString("type")),
            root.getString("output")
        )
    } catch (err: Throwable) {
        null
    }
}

private fun identifyTheToolType(string: String): ToolOutputType {
    return if ("text" in string) {
        ToolOutputType.Text
    } else if ("file" in string) {
        ToolOutputType.File
    } else {
        ToolOutputType.Url
    }
}