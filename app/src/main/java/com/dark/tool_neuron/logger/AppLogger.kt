package com.dark.tool_neuron.logger

import android.util.Log
import com.dark.tool_neuron.userdata.ntds.neuron_tree.NeuronNode
import com.dark.tool_neuron.userdata.ntds.neuron_tree.NeuronTree
import com.dark.tool_neuron.userdata.ntds.neuron_tree.NodeData
import com.dark.tool_neuron.userdata.ntds.neuron_tree.NodeType
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*

/**
 * Simple logging system that stores logs in brain file
 * Displays like chat messages with timing information
 */
object AppLogger {
    private const val TAG = "AppLogger"
    private const val LOGS_NODE_ID = "systemLogs"

    enum class LogLevel {
        INFO, WARN, ERROR
    }

    /**
     * Get or create the logs node
     */
    private fun getLogsNode(root: NeuronNode): NeuronNode {
        val tree = NeuronTree(root)
        return tree.getNodeDirectOrNull(LOGS_NODE_ID) ?: run {
            val logsNode = NeuronNode(
                id = LOGS_NODE_ID,
                data = NodeData(
                    content = JSONObject().apply {
                        put("title", "System Logs")
                        put("sessions", JSONArray())
                    }.toString(),
                    type = NodeType.OPERATOR
                )
            )
            tree.addChild(root.id, logsNode)
            logsNode
        }
    }

    /**
     * Start a new logging session
     */
    fun startSession(root: NeuronNode, sessionName: String) {
        try {
            val logsNode = getLogsNode(root)
            val tree = NeuronTree(root)

            // Create new session node
            val sessionNode = NeuronNode(
                data = NodeData(
                    content = JSONObject().apply {
                        put("sessionName", sessionName)
                        put("startTime", System.currentTimeMillis())
                        put("logs", JSONArray())
                    }.toString(),
                    type = NodeType.HOLDER
                )
            )

            tree.addChild(logsNode.id, sessionNode)
            Log.d(TAG, "Started session: $sessionName")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start session", e)
        }
    }

    /**
     * Log a message to the current session
     */
    fun log(
        root: NeuronNode,
        level: LogLevel,
        message: String,
        details: Map<String, Any>? = null
    ) {
        try {
            val logsNode = getLogsNode(root)
            val currentSession = logsNode.children.lastOrNull()

            if (currentSession == null) {
                startSession(root, "Default Session")
                return log(root, level, message, details)
            }

            val sessionData = JSONObject(currentSession.data.content)
            val logs = sessionData.getJSONArray("logs")

            // Create log entry
            val logEntry = JSONObject().apply {
                put("timestamp", System.currentTimeMillis())
                put("level", level.name)
                put("message", message)
                details?.let {
                    val detailsJson = JSONObject()
                    it.forEach { (k, v) -> detailsJson.put(k, v) }
                    put("details", detailsJson)
                }
            }

            logs.put(logEntry)
            sessionData.put("logs", logs)
            currentSession.data.content = sessionData.toString()

            // Also log to Logcat
            val logMsg = "[$level] $message"
            when (level) {
                LogLevel.INFO -> Log.i(TAG, logMsg)
                LogLevel.WARN -> Log.w(TAG, logMsg)
                LogLevel.ERROR -> Log.e(TAG, logMsg)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to log message", e)
        }
    }

    /**
     * End the current session
     */
    fun endSession(root: NeuronNode) {
        try {
            val logsNode = getLogsNode(root)
            val currentSession = logsNode.children.lastOrNull() ?: return

            val sessionData = JSONObject(currentSession.data.content)
            sessionData.put("endTime", System.currentTimeMillis())
            currentSession.data.content = sessionData.toString()

            Log.d(TAG, "Ended session")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to end session", e)
        }
    }

    /**
     * Convenience methods
     */
    fun info(root: NeuronNode, message: String, details: Map<String, Any>? = null) {
        log(root, LogLevel.INFO, message, details)
    }

    fun warn(root: NeuronNode, message: String, details: Map<String, Any>? = null) {
        log(root, LogLevel.WARN, message, details)
    }

    fun error(root: NeuronNode, message: String, details: Map<String, Any>? = null) {
        log(root, LogLevel.ERROR, message, details)
    }

    /**
     * Get all sessions
     */
    fun getSessions(root: NeuronNode): List<LogSession> {
        return try {
            val logsNode = getLogsNode(root)
            logsNode.children.map { sessionNode ->
                val data = JSONObject(sessionNode.data.content)
                LogSession.fromJson(sessionNode.id, data)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get sessions", e)
            emptyList()
        }
    }

    /**
     * Clear all logs
     */
    fun clearAllLogs(root: NeuronNode) {
        try {
            val tree = NeuronTree(root)
            val logsNode = tree.getNodeDirectOrNull(LOGS_NODE_ID) ?: return

            logsNode.children.toList().forEach { child ->
                tree.deleteNodeById(child.id)
            }

            Log.d(TAG, "Cleared all logs")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to clear logs", e)
        }
    }
}

/**
 * Represents a logging session
 */
data class LogSession(
    val id: String,
    val name: String,
    val startTime: Long,
    val endTime: Long?,
    val logs: List<LogEntry>
) {
    companion object {
        fun fromJson(id: String, json: JSONObject): LogSession {
            val logsArray = json.getJSONArray("logs")
            val logs = (0 until logsArray.length()).map { i ->
                LogEntry.fromJson(logsArray.getJSONObject(i))
            }

            return LogSession(
                id = id,
                name = json.getString("sessionName"),
                startTime = json.getLong("startTime"),
                endTime = json.optLong("endTime").takeIf { it > 0 },
                logs = logs
            )
        }
    }

    fun getDuration(): Long? {
        return endTime?.let { it - startTime }
    }

    fun getFormattedStartTime(): String {
        val sdf = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
        return sdf.format(Date(startTime))
    }

    fun getFormattedDuration(): String {
        val duration = getDuration() ?: return "In progress"
        val seconds = duration / 1000.0
        return String.format("%.2fs", seconds)
    }
}

/**
 * Represents a single log entry
 */
data class LogEntry(
    val timestamp: Long,
    val level: AppLogger.LogLevel,
    val message: String,
    val details: Map<String, Any>?
) {
    companion object {
        fun fromJson(json: JSONObject): LogEntry {
            val details = if (json.has("details")) {
                val detailsJson = json.getJSONObject("details")
                detailsJson.keys().asSequence().associateWith { key ->
                    detailsJson.get(key)
                }
            } else null

            return LogEntry(
                timestamp = json.getLong("timestamp"),
                level = AppLogger.LogLevel.valueOf(json.getString("level")),
                message = json.getString("message"),
                details = details
            )
        }
    }

    fun getFormattedTime(): String {
        val sdf = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
        return sdf.format(Date(timestamp))
    }
}