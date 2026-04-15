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
import androidx.compose.ui.unit.dp
import com.dark.tool_neuron.models.plugins.PluginInfo
import com.dark.tool_neuron.plugins.api.SuperPlugin
import com.dark.gguf_lib.toolcalling.ToolCall
import com.dark.gguf_lib.toolcalling.ToolDefinitionBuilder
import org.json.JSONObject
import java.security.MessageDigest
import java.util.UUID

class DevUtilsPlugin : SuperPlugin {

    companion object {
        private const val TAG = "DevUtilsPlugin"
        const val TOOL_TEXT_TRANSFORM = "text_transform"
        const val TOOL_HASH_GENERATE = "hash_generate"
        const val TOOL_UUID_GENERATE = "uuid_generate"
        const val TOOL_TEXT_STATS = "text_stats"
        const val TOOL_JSON_FORMAT = "json_format"
        const val TOOL_BASE64 = "base64_codec"
    }

    override fun getPluginInfo(): PluginInfo {
        return PluginInfo(
            name = "Dev Utils",
            description = "Developer utilities: text transforms, hashing, UUID, base64, JSON formatting",
            author = "ToolNeuron",
            version = "1.0.0",
            toolDefinitionBuilder = listOf(
                ToolDefinitionBuilder(
                    TOOL_TEXT_TRANSFORM,
                    "Transform text: uppercase, lowercase, reverse, title_case, snake_case, camel_case, trim, or count"
                )
                    .stringParam("text", "The input text to transform", required = true)
                    .stringParam("operation", "Operation: uppercase, lowercase, reverse, title_case, snake_case, camel_case, trim", required = true),

                ToolDefinitionBuilder(
                    TOOL_HASH_GENERATE,
                    "Generate a hash of the input text. Supports MD5, SHA-1, SHA-256, SHA-512"
                )
                    .stringParam("text", "The text to hash", required = true)
                    .stringParam("algorithm", "Hash algorithm: md5, sha1, sha256, sha512", required = true),

                ToolDefinitionBuilder(
                    TOOL_UUID_GENERATE,
                    "Generate one or more random UUIDs"
                )
                    .numberParam("count", "Number of UUIDs to generate (1-10, default 1)", required = false),

                ToolDefinitionBuilder(
                    TOOL_TEXT_STATS,
                    "Get statistics about a text: character count, word count, line count, sentence count"
                )
                    .stringParam("text", "The text to analyze", required = true),

                ToolDefinitionBuilder(
                    TOOL_JSON_FORMAT,
                    "Format/prettify a JSON string, or validate if it's valid JSON"
                )
                    .stringParam("json", "The JSON string to format or validate", required = true)
                    .booleanParam("validate_only", "If true, only validate without formatting", required = false),

                ToolDefinitionBuilder(
                    TOOL_BASE64,
                    "Encode or decode a base64 string"
                )
                    .stringParam("text", "The text to encode or decode", required = true)
                    .stringParam("operation", "Operation: encode or decode", required = true)
            )
        )
    }

    override fun serializeResult(data: Any): String = when (data) {
        is DevUtilsResponse -> JSONObject().apply {
            put("tool", data.tool)
            put("operation", data.operation)
            put("input", data.input)
            put("output", data.output)
        }.toString()
        is TextStatsResponse -> JSONObject().apply {
            put("charCount", data.charCount)
            put("charCountNoSpaces", data.charCountNoSpaces)
            put("wordCount", data.wordCount)
            put("lineCount", data.lineCount)
            put("sentenceCount", data.sentenceCount)
            put("summary", data.summary)
        }.toString()
        else -> data.toString()
    }

    override suspend fun executeTool(toolCall: ToolCall): Result<Any> {
        return try {
            when (toolCall.name) {
                TOOL_TEXT_TRANSFORM -> executeTextTransform(toolCall)
                TOOL_HASH_GENERATE -> executeHashGenerate(toolCall)
                TOOL_UUID_GENERATE -> executeUuidGenerate(toolCall)
                TOOL_TEXT_STATS -> executeTextStats(toolCall)
                TOOL_JSON_FORMAT -> executeJsonFormat(toolCall)
                TOOL_BASE64 -> executeBase64(toolCall)
                else -> Result.failure(IllegalArgumentException("Unknown tool: ${toolCall.name}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun executeTextTransform(toolCall: ToolCall): Result<Any> {
        val text = toolCall.getString("text")
        val operation = toolCall.getString("operation").lowercase().trim()

        val result = when (operation) {
            "uppercase", "upper" -> text.uppercase()
            "lowercase", "lower" -> text.lowercase()
            "reverse" -> text.reversed()
            "title_case", "title" -> text.split(" ").joinToString(" ") { word ->
                word.replaceFirstChar { it.uppercase() }
            }
            "snake_case", "snake" -> text.replace(Regex("[\\s-]+"), "_")
                .replace(Regex("([a-z])([A-Z])"), "$1_$2")
                .lowercase()
            "camel_case", "camel" -> {
                val words = text.split(Regex("[\\s_-]+"))
                words.first().lowercase() + words.drop(1).joinToString("") { word ->
                    word.replaceFirstChar { it.uppercase() }
                }
            }
            "trim" -> text.trim()
            else -> return Result.failure(IllegalArgumentException("Unknown operation: $operation"))
        }

        return Result.success(DevUtilsResponse(
            tool = TOOL_TEXT_TRANSFORM,
            operation = operation,
            input = text,
            output = result
        ))
    }

    private fun executeHashGenerate(toolCall: ToolCall): Result<Any> {
        val text = toolCall.getString("text")
        val algorithm = toolCall.getString("algorithm").lowercase().trim()

        val digestAlgorithm = when (algorithm) {
            "md5" -> "MD5"
            "sha1", "sha-1" -> "SHA-1"
            "sha256", "sha-256" -> "SHA-256"
            "sha512", "sha-512" -> "SHA-512"
            else -> return Result.failure(IllegalArgumentException("Unknown algorithm: $algorithm. Use md5, sha1, sha256, or sha512"))
        }

        val digest = MessageDigest.getInstance(digestAlgorithm)
        val hash = digest.digest(text.toByteArray())
            .joinToString("") { "%02x".format(it) }

        return Result.success(DevUtilsResponse(
            tool = TOOL_HASH_GENERATE,
            operation = algorithm,
            input = text,
            output = hash
        ))
    }

    private fun executeUuidGenerate(toolCall: ToolCall): Result<Any> {
        val count = toolCall.getInt("count", 1).coerceIn(1, 10)
        val uuids = (1..count).map { UUID.randomUUID().toString() }

        return Result.success(DevUtilsResponse(
            tool = TOOL_UUID_GENERATE,
            operation = "generate",
            input = "count=$count",
            output = uuids.joinToString("\n")
        ))
    }

    private fun executeTextStats(toolCall: ToolCall): Result<Any> {
        val text = toolCall.getString("text")

        val charCount = text.length
        val charCountNoSpaces = text.replace(" ", "").length
        val wordCount = if (text.isBlank()) 0 else text.trim().split(Regex("\\s+")).size
        val lineCount = if (text.isEmpty()) 0 else text.lines().size
        val sentenceCount = text.split(Regex("[.!?]+")).filter { it.isNotBlank() }.size

        val stats = "Characters: $charCount (no spaces: $charCountNoSpaces)\n" +
                "Words: $wordCount\n" +
                "Lines: $lineCount\n" +
                "Sentences: $sentenceCount"

        return Result.success(TextStatsResponse(
            charCount = charCount,
            charCountNoSpaces = charCountNoSpaces,
            wordCount = wordCount,
            lineCount = lineCount,
            sentenceCount = sentenceCount,
            summary = stats
        ))
    }

    private fun executeJsonFormat(toolCall: ToolCall): Result<Any> {
        val jsonString = toolCall.getString("json")
        val validateOnly = toolCall.getBoolean("validate_only", false)

        return try {
            val jsonObj = JSONObject(jsonString)
            if (validateOnly) {
                Result.success(DevUtilsResponse(
                    tool = TOOL_JSON_FORMAT,
                    operation = "validate",
                    input = jsonString,
                    output = "Valid JSON"
                ))
            } else {
                Result.success(DevUtilsResponse(
                    tool = TOOL_JSON_FORMAT,
                    operation = "format",
                    input = jsonString,
                    output = jsonObj.toString(2)
                ))
            }
        } catch (e: Exception) {
            // Try as JSONArray
            try {
                val jsonArr = org.json.JSONArray(jsonString)
                if (validateOnly) {
                    Result.success(DevUtilsResponse(
                        tool = TOOL_JSON_FORMAT,
                        operation = "validate",
                        input = jsonString,
                        output = "Valid JSON Array"
                    ))
                } else {
                    Result.success(DevUtilsResponse(
                        tool = TOOL_JSON_FORMAT,
                        operation = "format",
                        input = jsonString,
                        output = jsonArr.toString(2)
                    ))
                }
            } catch (e2: Exception) {
                Result.success(DevUtilsResponse(
                    tool = TOOL_JSON_FORMAT,
                    operation = if (validateOnly) "validate" else "format",
                    input = jsonString,
                    output = "Invalid JSON: ${e.message}"
                ))
            }
        }
    }

    private fun executeBase64(toolCall: ToolCall): Result<Any> {
        val text = toolCall.getString("text")
        val operation = toolCall.getString("operation").lowercase().trim()

        val result = when (operation) {
            "encode" -> {
                android.util.Base64.encodeToString(text.toByteArray(), android.util.Base64.NO_WRAP)
            }
            "decode" -> {
                try {
                    String(android.util.Base64.decode(text, android.util.Base64.DEFAULT))
                } catch (e: Exception) {
                    return Result.failure(IllegalArgumentException("Invalid base64 input: ${e.message}"))
                }
            }
            else -> return Result.failure(IllegalArgumentException("Unknown operation: $operation. Use 'encode' or 'decode'"))
        }

        return Result.success(DevUtilsResponse(
            tool = TOOL_BASE64,
            operation = operation,
            input = text,
            output = result
        ))
    }

    @Composable
    override fun ToolCallUI() {
        // No standalone UI needed
    }

    @Composable
    override fun CacheToolUI(data: JSONObject) {
        when {
            data.has("wordCount") || data.has("charCount") -> TextStatsResultUI(data)
            data.has("tool") -> DevUtilsResultUI(data)
            else -> {
                Text(
                    text = data.toString(2),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(8.dp)
                )
            }
        }
    }

    @Composable
    private fun DevUtilsResultUI(data: JSONObject) {
        val tool = data.optString("tool", "")
        val operation = data.optString("operation", "")
        val output = data.optString("output", "")

        val title = when (tool) {
            TOOL_TEXT_TRANSFORM -> "Text Transform ($operation)"
            TOOL_HASH_GENERATE -> "Hash ($operation)"
            TOOL_UUID_GENERATE -> "UUID Generator"
            TOOL_JSON_FORMAT -> "JSON ${operation.replaceFirstChar { it.uppercase() }}"
            TOOL_BASE64 -> "Base64 ${operation.replaceFirstChar { it.uppercase() }}"
            else -> "Dev Utils"
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
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ) {
                    Text(
                        text = output,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(8.dp)
                    )
                }
            }
        }
    }

    @Composable
    private fun TextStatsResultUI(data: JSONObject) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(6.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)
        ) {
            Column(
                modifier = Modifier.padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = "Text Statistics",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                val stats = listOf(
                    "Characters" to data.optInt("charCount", 0).toString(),
                    "Words" to data.optInt("wordCount", 0).toString(),
                    "Lines" to data.optInt("lineCount", 0).toString(),
                    "Sentences" to data.optInt("sentenceCount", 0).toString()
                )

                stats.forEach { (label, value) ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = value,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
    }
}

data class DevUtilsResponse(
    val tool: String,
    val operation: String,
    val input: String,
    val output: String
)

data class TextStatsResponse(
    val charCount: Int,
    val charCountNoSpaces: Int,
    val wordCount: Int,
    val lineCount: Int,
    val sentenceCount: Int,
    val summary: String
)
