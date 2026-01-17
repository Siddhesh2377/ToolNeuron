package com.dark.tool_neuron.service

import com.dark.tool_neuron.models.table_schema.McpServer
import org.json.JSONArray
import org.json.JSONObject

data class McpToolReference(
    val server: McpServer,
    val toolName: String
)

data class McpToolMapping(
    val toolsJson: String,
    val toolRegistry: Map<String, McpToolReference>
)

object McpToolMapper {
    fun sanitizeIdentifier(value: String): String {
        return value.lowercase()
            .replace(Regex("[^a-z0-9]+"), "_")
            .trim('_')
    }

    fun buildMapping(serverTools: Map<McpServer, List<McpToolInfo>>): McpToolMapping {
        val toolsArray = JSONArray()
        val registry = mutableMapOf<String, McpToolReference>()

        serverTools.forEach { (server, tools) ->
            val serverPrefix = sanitizeIdentifier(server.name).ifBlank { "mcp" }
            tools.forEach { tool ->
                val toolSlug = sanitizeIdentifier(tool.name).ifBlank { "tool" }
                val toolId = "${serverPrefix}_${toolSlug}"
                toolsArray.put(buildToolDefinition(toolId, tool))
                registry[toolId] = McpToolReference(server, tool.name)
            }
        }

        return McpToolMapping(
            toolsJson = toolsArray.toString(),
            toolRegistry = registry
        )
    }

    private fun buildToolDefinition(toolId: String, tool: McpToolInfo): JSONObject {
        val function = JSONObject().apply {
            put("name", toolId)
            tool.description?.takeIf { it.isNotBlank() }?.let { put("description", it) }
            put("parameters", buildParameters(tool.inputSchema))
        }

        return JSONObject().apply {
            put("type", "function")
            put("function", function)
        }
    }

    private fun buildParameters(inputSchema: String?): JSONObject {
        val parsedSchema = inputSchema?.takeIf { it.isNotBlank() }?.let {
            runCatching { JSONObject(it) }.getOrNull()
        }

        return (parsedSchema ?: JSONObject()).apply {
            if (!has("type")) {
                put("type", "object")
            }
        }
    }
}
