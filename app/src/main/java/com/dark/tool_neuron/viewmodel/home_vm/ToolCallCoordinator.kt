package com.dark.tool_neuron.viewmodel.home_vm

import android.util.Log
import com.dark.tool_neuron.model.ChatMessage
import com.dark.tool_neuron.model.MessageKind
import com.dark.tool_neuron.plugin.PluginRegistry
import com.dark.tool_neuron.plugin.api.ToolDef
import com.dark.tool_neuron.repo.PluginPrefsRepository
import com.dark.tool_neuron.service.inference.InferenceClient
import com.dark.tool_neuron.service.inference.InferenceEvent
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.json.JSONObject
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "ToolCallCoordinator"
private const val ROLE_TOOL = "tool"

@Singleton
class ToolCallCoordinator @Inject constructor(
    private val pluginRegistry: PluginRegistry,
    private val pluginPrefs: PluginPrefsRepository,
) {
    fun enabledTools(): List<ToolDef> =
        pluginRegistry.enabledToolDefs(pluginPrefs.enabledIds())

    fun configureInference(webOn: Boolean): List<ToolDef> {
        val tools = if (webOn) enabledTools() else emptyList()
        Log.i(TAG, "configureInference: webOn=$webOn tools=${tools.map { it.name }}")
        if (tools.isEmpty()) {
            InferenceClient.clearTools()
            InferenceClient.setSystemPrompt("")
            return tools
        }
        val toolsJson = Json.encodeToString(JsonArray.serializer(), toOpenAiFormat(tools))
        InferenceClient.setSystemPrompt(TOOL_USE_GUIDANCE)
        InferenceClient.enableToolCalling(toolsJson)
        return tools
    }

    private companion object {
        const val TOOL_USE_GUIDANCE =
            "You are a helpful assistant. Tools are available but OPTIONAL. " +
            "Only call a tool when the user is explicitly asking for real-time, external, or up-to-date information " +
            "(news, prices, weather, current events, specific URLs, recent facts). " +
            "For greetings, chit-chat, opinions, explanations, math, coding help, or anything you already know, " +
            "answer directly in plain text without calling any tool. " +
            "When in doubt, answer directly — do not call a tool."
    }

    private fun toOpenAiFormat(tools: List<ToolDef>): JsonArray = buildJsonArray {
        tools.forEach { tool ->
            add(
                buildJsonObject {
                    put("type", "function")
                    put(
                        "function",
                        buildJsonObject {
                            put("name", tool.name)
                            put("description", tool.description)
                            put("parameters", tool.parameters)
                        },
                    )
                },
            )
        }
    }

    fun parseFromText(raw: String): InferenceEvent.ToolCall? {
        if (raw.isBlank()) return null

        val stripped = raw
            .replace(Regex("""```(?:json|JSON)?\s*"""), "")
            .replace("```", "")
            .trim()

        val tagged = Regex("""<tool_call>\s*(\{[\s\S]*?\})\s*</tool_call>""", RegexOption.IGNORE_CASE)
            .find(stripped)?.groupValues?.getOrNull(1)
        if (tagged != null) {
            parseToolCallJson(tagged)?.let { return it }
            parseToolCallLenient(tagged)?.let { return it }
        }

        val obj = extractFirstJsonObject(stripped) ?: return null
        parseToolCallJson(obj)?.let { return it }
        return parseToolCallLenient(obj)
    }

    suspend fun execute(chatId: String, call: InferenceEvent.ToolCall): ChatMessage {
        val plugin = pluginRegistry.pluginForTool(call.name)
        val resultJson = when {
            plugin == null ->
                """{"error":"unknown tool: ${call.name}"}"""
            !pluginPrefs.isEnabled(plugin.id) ->
                """{"error":"plugin disabled: ${plugin.id}"}"""
            else -> plugin.execute(call.name, call.argsJson).getOrElse { t ->
                JSONObject().put("error", t.message ?: "tool error").toString()
            }
        }
        return ChatMessage(
            id = UUID.randomUUID().toString(),
            chatId = chatId,
            role = ROLE_TOOL,
            content = resultJson,
            timestamp = System.currentTimeMillis(),
            kind = MessageKind.ToolResult,
            modelName = call.name,
        )
    }

    private fun parseToolCallLenient(raw: String): InferenceEvent.ToolCall? {
        val name = Regex(""""(?:name|tool_name|tool|function)"\s*:\s*"([^"]+)"""")
            .find(raw)?.groupValues?.getOrNull(1)
            ?: return null

        val argsBody = Regex(""""(?:arguments|args|parameters)"\s*:\s*(\{[\s\S]*?\})""")
            .find(raw)?.groupValues?.getOrNull(1)

        val argsJson = if (argsBody == null) "{}" else {
            runCatching { JSONObject(argsBody).toString() }.getOrElse {
                val rebuilt = JSONObject()
                Regex(""""([^"]+)"\s*:\s*"([^"]*)"""").findAll(argsBody).forEach { m ->
                    rebuilt.put(m.groupValues[1], m.groupValues[2])
                }
                Regex(""""([^"]+)"\s*:\s*(-?\d+(?:\.\d+)?)""").findAll(argsBody).forEach { m ->
                    rebuilt.put(m.groupValues[1], m.groupValues[2])
                }
                rebuilt.toString()
            }
        }

        return InferenceEvent.ToolCall(name = name, argsJson = argsJson)
    }

    private fun parseToolCallJson(jsonText: String): InferenceEvent.ToolCall? = runCatching {
        val obj = JSONObject(jsonText)
        val name = listOf("name", "tool_name", "tool", "function")
            .firstNotNullOfOrNull { key -> obj.optString(key).takeIf { it.isNotEmpty() } }
            ?: return@runCatching null
        val args = obj.opt("arguments") ?: obj.opt("args") ?: obj.opt("parameters")
        val argsJson = when (args) {
            is JSONObject -> args.toString()
            null -> "{}"
            else -> "{}"
        }
        InferenceEvent.ToolCall(name = name, argsJson = argsJson)
    }.getOrNull()

    private fun extractFirstJsonObject(raw: String): String? {
        val start = raw.indexOf('{')
        if (start < 0) return null
        var depth = 0
        var inString = false
        var escape = false
        for (i in start until raw.length) {
            val c = raw[i]
            when {
                escape -> escape = false
                c == '\\' && inString -> escape = true
                c == '"' -> inString = !inString
                inString -> Unit
                c == '{' -> depth++
                c == '}' -> {
                    depth--
                    if (depth == 0) return raw.substring(start, i + 1)
                }
            }
        }
        return null
    }
}
