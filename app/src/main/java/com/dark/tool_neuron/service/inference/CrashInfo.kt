package com.dark.tool_neuron.service.inference

import org.json.JSONObject

data class CrashInfo(
    val lib: String,
    val signal: Int? = null,
    val signalName: String? = null,
    val operation: String? = null,
    val operationDetail: String? = null,
    val errorCode: Int? = null,
    val category: String? = null,
    val message: String,
    val timestamp: Long = System.currentTimeMillis(),
    val source: Source = Source.LIVE_ERROR,
) {
    enum class Source { LIVE_ERROR, NATIVE_CRASH }

    val suggestion: String? = derivedSuggestion(signalName, category, message, operation)

    companion object {
        fun fromJson(lib: String, raw: String, source: Source): CrashInfo? {
            if (raw.isBlank() || raw == "{}") return null
            return try {
                val root = JSONObject(raw)
                val signal = root.optInt("signal", -1).takeIf { it > 0 }
                val signalName = root.optString("signal_name").takeIf { it.isNotBlank() }
                val currentOp = root.optJSONObject("current_op")
                val lastError = root.optJSONObject("last_error")

                val opSource = lastError?.optJSONObject("op_at_time") ?: currentOp
                val operation = opSource?.optString("op")?.takeIf { it.isNotBlank() }
                val operationDetail = opSource?.optString("detail")?.takeIf { it.isNotBlank() }

                val errorCode = lastError?.optInt("code", -1)?.takeIf { it >= 0 }
                val category = lastError?.optString("category")?.takeIf { it.isNotBlank() }
                val message = lastError?.optString("message")?.takeIf { it.isNotBlank() }
                    ?: signalName?.let { "Native crash ($it)" }
                    ?: return null

                CrashInfo(
                    lib = lib,
                    signal = signal,
                    signalName = signalName,
                    operation = operation,
                    operationDetail = operationDetail,
                    errorCode = errorCode,
                    category = category,
                    message = message,
                    timestamp = lastError?.optLong("timestamp", 0L)
                        ?.takeIf { it > 0 }
                        ?: root.optLong("timestamp", System.currentTimeMillis()),
                    source = source,
                )
            } catch (_: Exception) { null }
        }

        private fun derivedSuggestion(
            signalName: String?,
            category: String?,
            message: String,
            operation: String?,
        ): String? {
            val haystack = "${message} ${category.orEmpty()} ${signalName.orEmpty()}".lowercase()
            return when {
                "out of memory" in haystack || "oom" in haystack || category == "OOM" ->
                    "Lower Context Size, switch KV Cache to q4_0, or pick a smaller / lower-quant model."
                category == "ContextAlloc" ->
                    "The KV cache could not fit in RAM. Reduce Context Size or change KV Cache type to q4_0."
                category == "ChatTemplate" || "template" in haystack ->
                    "Clear the Chat Template field — the model's built-in template is usually correct."
                category == "ModelLoad" || operation == "loadModel" ->
                    "The model file may be corrupt, the wrong format, or too big. Try re-downloading."
                category == "InvalidParam" ->
                    "A sampling parameter was out of range or malformed. Use Reset to defaults in Model Config."
                signalName == "SIGSEGV" || signalName == "SIGBUS" ->
                    "Native crash. Often caused by an incompatible quant + cache combo. Reset Model Config to defaults and try again."
                signalName == "SIGABRT" ->
                    "Native abort, usually a runtime assertion. Try a smaller model or reset Model Config to defaults."
                else -> null
            }
        }
    }
}
