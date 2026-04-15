package com.dark.tool_neuron.plugins

import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dark.tool_neuron.models.plugins.PluginInfo
import com.dark.tool_neuron.plugins.api.SuperPlugin
import com.dark.gguf_lib.toolcalling.ToolCall
import com.dark.gguf_lib.toolcalling.ToolDefinitionBuilder
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap

// ── Response Model ──

data class NotePadResponse(
    val tool: String,
    val key: String,
    val content: String,
    val success: Boolean,
    val noteCount: Int = 0
)

// ── Plugin ──

class NotePadPlugin : SuperPlugin {

    companion object {
        private const val TAG = "NotePadPlugin"
        const val TOOL_WRITE = "notepad_write"
        const val TOOL_READ = "notepad_read"
        const val TOOL_LIST = "notepad_list"
    }

    private val notes = ConcurrentHashMap<String, String>()

    override fun getPluginInfo(): PluginInfo {
        return PluginInfo(
            name = "NotePad",
            description = "Scratch pad for storing and retrieving notes across conversation turns",
            author = "ToolNeuron",
            version = "1.0.0",
            toolDefinitionBuilder = listOf(
                ToolDefinitionBuilder(
                    TOOL_WRITE,
                    "Store a named note. Overwrites if key already exists."
                )
                    .stringParam("key", "Unique name for this note (e.g. 'shopping_list', 'code_snippet')", required = true)
                    .stringParam("content", "The text content to store", required = true),

                ToolDefinitionBuilder(
                    TOOL_READ,
                    "Read a previously stored note by its key"
                )
                    .stringParam("key", "The name of the note to read", required = true),

                ToolDefinitionBuilder(
                    TOOL_LIST,
                    "List all stored note keys"
                )
            )
        )
    }

    // ── Serialization ──

    override fun serializeResult(data: Any): String = when (data) {
        is NotePadResponse -> JSONObject().apply {
            put("tool", data.tool)
            put("key", data.key)
            put("content", data.content)
            put("success", data.success)
            put("noteCount", data.noteCount)
        }.toString()
        else -> data.toString()
    }

    // ── Execution ──

    override suspend fun executeTool(toolCall: ToolCall): Result<Any> {
        return try {
            when (toolCall.name) {
                TOOL_WRITE -> executeWrite(toolCall)
                TOOL_READ -> executeRead(toolCall)
                TOOL_LIST -> executeList()
                else -> Result.failure(IllegalArgumentException("Unknown tool: ${toolCall.name}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun executeWrite(toolCall: ToolCall): Result<Any> {
        val key = toolCall.getString("key").trim()
        val content = toolCall.getString("content")

        if (key.isBlank()) return Result.failure(IllegalArgumentException("Key cannot be empty"))

        val existed = notes.containsKey(key)
        notes[key] = content
        Log.d(TAG, "Note ${if (existed) "updated" else "created"}: '$key' (${content.length} chars)")

        return Result.success(NotePadResponse(
            tool = TOOL_WRITE,
            key = key,
            content = if (existed) "Note '$key' updated (${content.length} chars)" else "Note '$key' saved (${content.length} chars)",
            success = true,
            noteCount = notes.size
        ))
    }

    private fun executeRead(toolCall: ToolCall): Result<Any> {
        val key = toolCall.getString("key").trim()
        val content = notes[key]

        return if (content != null) {
            Result.success(NotePadResponse(
                tool = TOOL_READ,
                key = key,
                content = content,
                success = true,
                noteCount = notes.size
            ))
        } else {
            Result.success(NotePadResponse(
                tool = TOOL_READ,
                key = key,
                content = "Note '$key' not found. Available keys: ${notes.keys.joinToString(", ").ifEmpty { "(none)" }}",
                success = false,
                noteCount = notes.size
            ))
        }
    }

    private fun executeList(): Result<Any> {
        val keys = notes.keys.toList().sorted()
        val content = if (keys.isEmpty()) {
            "No notes stored yet."
        } else {
            keys.joinToString("\n") { key ->
                val preview = notes[key]?.take(50) ?: ""
                "• $key: ${preview}${if ((notes[key]?.length ?: 0) > 50) "…" else ""}"
            }
        }

        return Result.success(NotePadResponse(
            tool = TOOL_LIST,
            key = "",
            content = content,
            success = true,
            noteCount = notes.size
        ))
    }

    // ── UI ──

    @Composable
    override fun ToolCallUI() {
        // No standalone UI needed
    }

    @Composable
    override fun CacheToolUI(data: JSONObject) {
        val tool = data.optString("tool", "")
        val key = data.optString("key", "")
        val content = data.optString("content", "")
        val noteCount = data.optInt("noteCount", 0)

        val title = when (tool) {
            TOOL_WRITE -> "Note Saved"
            TOOL_READ -> "Note: $key"
            TOOL_LIST -> "Notes ($noteCount)"
            else -> "NotePad"
        }

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(6.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)
        ) {
            Column(
                modifier = Modifier.padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (noteCount > 0) {
                        Text(
                            text = "$noteCount notes",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                if (content.isNotBlank()) {
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    ) {
                        Text(
                            text = content.take(500) + if (content.length > 500) "…" else "",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(8.dp),
                            maxLines = 15,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}
