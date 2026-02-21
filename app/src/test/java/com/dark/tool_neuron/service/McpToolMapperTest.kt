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

    @Test
    fun sanitizeIdentifierCollapsesConsecutiveSpecialChars() {
        assertEquals("my_tool", McpToolMapper.sanitizeIdentifier("My--Tool"))
        assertEquals("a_b", McpToolMapper.sanitizeIdentifier("a---b"))
        assertEquals("hello_world", McpToolMapper.sanitizeIdentifier("  hello   world  "))
        assertEquals("test", McpToolMapper.sanitizeIdentifier("---test---"))
        assertEquals("a_b_c", McpToolMapper.sanitizeIdentifier("a..b..c"))
    }

    @Test
    fun sanitizeIdentifierHandlesEdgeCases() {
        assertEquals("mcp", McpToolMapper.sanitizeIdentifier("").ifBlank { "mcp" })
        assertEquals("abc123", McpToolMapper.sanitizeIdentifier("ABC123"))
        assertEquals("tool_name_v2", McpToolMapper.sanitizeIdentifier("tool-name-v2"))
    }
}
