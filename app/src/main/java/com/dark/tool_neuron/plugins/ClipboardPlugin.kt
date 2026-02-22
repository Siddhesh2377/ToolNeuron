package com.dark.tool_neuron.plugins

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.dark.tool_neuron.models.plugins.PluginInfo
import com.dark.tool_neuron.plugins.api.SuperPlugin
import com.dark.tool_neuron.ui.theme.rDp
import com.mp.ai_gguf.toolcalling.ToolCall
import com.mp.ai_gguf.toolcalling.ToolDefinitionBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

class ClipboardPlugin(private val context: Context) : SuperPlugin {

    companion object {
        private const val TAG = "ClipboardPlugin"
        const val TOOL_READ_CLIPBOARD = "read_clipboard"
        const val TOOL_WRITE_CLIPBOARD = "write_clipboard"
    }

    private val clipboardManager: ClipboardManager
        get() = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager

    override fun getPluginInfo(): PluginInfo {
        return PluginInfo(
            name = "Clipboard",
            description = "Read and write text to the system clipboard",
            author = "ToolNeuron",
            version = "1.0.0",
            toolDefinitionBuilder = listOf(
                ToolDefinitionBuilder(
                    TOOL_READ_CLIPBOARD,
                    "Read the current text content from the system clipboard"
                ),

                ToolDefinitionBuilder(
                    TOOL_WRITE_CLIPBOARD,
                    "Write text to the system clipboard"
                )
                    .stringParam("text", "The text to write to the clipboard", required = true)
            )
        )
    }

    override suspend fun executeTool(toolCall: ToolCall): Result<Any> {
        return try {
            when (toolCall.name) {
                TOOL_READ_CLIPBOARD -> executeReadClipboard()
                TOOL_WRITE_CLIPBOARD -> executeWriteClipboard(toolCall)
                else -> Result.failure(IllegalArgumentException("Unknown tool: ${toolCall.name}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun executeReadClipboard(): Result<Any> {
        return withContext(Dispatchers.Main) {
            val content = clipboardManager.primaryClip?.getItemAt(0)?.text?.toString() ?: ""
            val response = ClipboardResponse(
                operation = "read",
                content = content,
                success = true
            )
            Result.success(response)
        }
    }

    private suspend fun executeWriteClipboard(toolCall: ToolCall): Result<Any> {
        val text = toolCall.getString("text")
        if (text.isBlank()) {
            return Result.failure(IllegalArgumentException("Text to write is empty"))
        }

        return withContext(Dispatchers.Main) {
            clipboardManager.setPrimaryClip(ClipData.newPlainText("ToolNeuron", text))
            val response = ClipboardResponse(
                operation = "write",
                content = text,
                success = true
            )
            Result.success(response)
        }
    }

    @Composable
    override fun ToolCallUI() {
        // No standalone UI needed
    }

    @Composable
    override fun CacheToolUI(data: JSONObject) {
        val operation = data.optString("operation", "")
        val content = data.optString("content", "")

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(rDp(6.dp)),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)
        ) {
            Column(
                modifier = Modifier.padding(rDp(10.dp)),
                verticalArrangement = Arrangement.spacedBy(rDp(6.dp))
            ) {
                Text(
                    text = "Clipboard ${operation.replaceFirstChar { it.uppercase() }}",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Surface(
                    shape = RoundedCornerShape(rDp(4.dp)),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ) {
                    Text(
                        text = content.ifEmpty { "(empty)" },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(rDp(8.dp))
                    )
                }
            }
        }
    }
}

data class ClipboardResponse(
    val operation: String,
    val content: String,
    val success: Boolean
)
