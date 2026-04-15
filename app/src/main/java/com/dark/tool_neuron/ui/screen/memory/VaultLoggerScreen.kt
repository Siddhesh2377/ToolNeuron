package com.dark.tool_neuron.ui.screen.memory

import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

// ── Log Level Enum ──
enum class LogLevel(val color: Color, val prefix: String) {
    DEBUG(Color(0xFF2196F3), "DEBUG"),
    INFO(Color(0xFF4CAF50), "INFO"),
    WARNING(Color(0xFFFFC107), "WARN"),
    ERROR(Color(0xFFF44336), "ERROR"),
    CRITICAL(Color(0xFFD32F2F), "CRIT")
}

// ── Log Entry ──
data class LogEntry(
    val timestamp: Long = System.currentTimeMillis(),
    val level: LogLevel,
    val tag: String,
    val message: String,
    val stackTrace: String? = null
)

// ── Vault Logger (thread-safe) ──
object VaultLogger {
    private val _logs = MutableStateFlow<List<LogEntry>>(emptyList())
    val logs: StateFlow<List<LogEntry>> = _logs.asStateFlow()

    @Volatile
    var isEnabled: Boolean = true

    @Volatile
    var maxLogSize: Int = 500

    fun log(level: LogLevel, tag: String, message: String, stackTrace: String? = null) {
        if (!isEnabled) return

        val entry = LogEntry(
            timestamp = System.currentTimeMillis(),
            level = level,
            tag = tag,
            message = message,
            stackTrace = stackTrace
        )

        _logs.update { current ->
            val updated = listOf(entry) + current
            if (updated.size > maxLogSize) updated.take(maxLogSize) else updated
        }
    }

    fun debug(tag: String, message: String) = log(LogLevel.DEBUG, tag, message)
    fun info(tag: String, message: String) = log(LogLevel.INFO, tag, message)
    fun warning(tag: String, message: String) = log(LogLevel.WARNING, tag, message)

    fun error(tag: String, message: String, exception: Throwable? = null) {
        log(LogLevel.ERROR, tag, message, exception?.stackTraceToString())
    }

    fun critical(tag: String, message: String, exception: Throwable? = null) {
        log(LogLevel.CRITICAL, tag, message, exception?.stackTraceToString())
    }

    fun clear() {
        _logs.value = emptyList()
    }
}
