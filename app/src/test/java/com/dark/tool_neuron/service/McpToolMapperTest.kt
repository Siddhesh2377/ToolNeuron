package com.dark.tool_neuron.service

import com.dark.tool_neuron.models.table_schema.McpServer
import com.dark.tool_neuron.models.table_schema.McpTransportType
import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class McpToolMapperTest {
    @Test
    fun buildMappingCreatesToolRegistry() {
        val server = McpServer(
            id = "server-1",
            name = "Zapier MCP",
            url = "https://example.com/mcp",
            transportType = McpTransportType.SSE
        )
        val tool = McpToolInfo(
            name = "send-email",
            description = "Send an email",
            inputSchema = """{"type":"object","properties":{"to":{"type":"string"}}}"""
        )

        val mapping = McpToolMapper.buildMapping(mapOf(server to listOf(tool)))
        val toolsArray = JSONArray(mapping.toolsJson)

        assertEquals(1, toolsArray.length())
        val function = toolsArray.getJSONObject(0).getJSONObject("function")
        assertEquals("zapier_mcp_send_email", function.getString("name"))
        assertEquals("object", function.getJSONObject("parameters").getString("type"))

        val reference = mapping.toolRegistry["zapier_mcp_send_email"]
        assertNotNull(reference)
        assertEquals(server, reference?.server)
        assertEquals("send-email", reference?.toolName)
    }
}
