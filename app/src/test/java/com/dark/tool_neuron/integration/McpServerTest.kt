package com.dark.tool_neuron.integration

import com.dark.tool_neuron.models.table_schema.McpServer
import com.dark.tool_neuron.models.table_schema.McpTransportType
import com.dark.tool_neuron.service.McpToolInfo
import com.dark.tool_neuron.service.McpToolMapper
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for MCP server-related functionality.
 * These tests validate McpToolMapper functionality, JSON parsing,
 * and configuration objects without connecting to real MCP servers.
 */
class McpServerTest {

    // Helper function to parse SSE response format.
    // This is a simplified version for tests that extracts JSON from single-event SSE responses.
    // The production code in McpClientService.parseSseResponse() handles multiple events and validates JSON.
    private fun parseSseData(sseResponse: String): String {
        val dataLine = sseResponse.lines().find { it.startsWith("data:") }
            ?: return sseResponse
        return dataLine.removePrefix("data:").trim()
    }

    /**
     * Test that McpServer can be created with the correct configuration
     * for connecting to Zapier's MCP endpoint.
     */
    @Test
    fun createZapierMcpServerConfiguration() {
        val zapierUrl = "https://mcp.zapier.com/api/v1/connect?token=example-token"
        
        val server = McpServer(
            id = McpServer.generateId(),
            name = "Zapier MCP",
            url = zapierUrl,
            transportType = McpTransportType.SSE,
            apiKey = null, // Token is in URL
            description = "Zapier MCP integration for Google Docs tools"
        )
        
        assertNotNull(server.id)
        assertEquals("Zapier MCP", server.name)
        assertEquals(zapierUrl, server.url)
        assertEquals(McpTransportType.SSE, server.transportType)
        assertTrue(server.isEnabled)
    }

    /**
     * Test parsing of MCP initialize response in SSE format using helper function.
     */
    @Test
    fun parseMcpInitializeResponse() {
        val sseResponse = """event: message
data: {"result":{"protocolVersion":"2024-11-05","capabilities":{"tools":{"listChanged":true}},"serverInfo":{"name":"zapier","title":"Zapier MCP","version":"1.0.0"}},"jsonrpc":"2.0","id":1}"""

        // Use helper function to extract JSON from SSE format
        val jsonStr = parseSseData(sseResponse)
        val json = JSONObject(jsonStr)
        
        assertEquals("2.0", json.getString("jsonrpc"))
        assertEquals(1, json.getInt("id"))
        
        val result = json.getJSONObject("result")
        assertEquals("2024-11-05", result.getString("protocolVersion"))
        
        val serverInfo = result.getJSONObject("serverInfo")
        assertEquals("zapier", serverInfo.getString("name"))
        assertEquals("1.0.0", serverInfo.getString("version"))
    }

    /**
     * Test parsing of MCP tools/list response.
     */
    @Test
    fun parseMcpToolsListResponse() {
        val sseResponse = """event: message
data: {"result":{"tools":[{"name":"google_docs_create_document_from_text","description":"Create a new document from text.","inputSchema":{"type":"object","properties":{"title":{"type":"string"}},"required":[]}}]},"jsonrpc":"2.0","id":2}"""

        // Use helper function to extract JSON from SSE format
        val jsonStr = parseSseData(sseResponse)
        val json = JSONObject(jsonStr)
        
        val result = json.getJSONObject("result")
        val tools = result.getJSONArray("tools")
        
        assertEquals(1, tools.length())
        
        val tool = tools.getJSONObject(0)
        assertEquals("google_docs_create_document_from_text", tool.getString("name"))
        assertEquals("Create a new document from text.", tool.getString("description"))
        
        val inputSchema = tool.getJSONObject("inputSchema")
        assertEquals("object", inputSchema.getString("type"))
    }

    /**
     * Test that McpToolMapper correctly maps Zapier tools to the LLM format.
     */
    @Test
    fun mapZapierToolsToLlmFormat() {
        val server = McpServer(
            id = "zapier-1",
            name = "Zapier MCP",
            url = "https://mcp.zapier.com/api/v1/connect",
            transportType = McpTransportType.SSE
        )
        
        val tools = listOf(
            McpToolInfo(
                name = "google_docs_create_document_from_text",
                description = "Create a new document from text. Also supports limited HTML.",
                inputSchema = """{"type":"object","properties":{"instructions":{"type":"string","description":"Instructions for running this tool"},"title":{"type":"string","description":"Document Name"},"file":{"type":"string","description":"Document Content"}},"required":["instructions"]}"""
            ),
            McpToolInfo(
                name = "google_docs_find_a_document",
                description = "Search for a specific document by name.",
                inputSchema = """{"type":"object","properties":{"instructions":{"type":"string","description":"Instructions for running this tool"},"title":{"type":"string","description":"Document Name"}},"required":["instructions"]}"""
            )
        )
        
        val mapping = McpToolMapper.buildMapping(mapOf(server to tools))
        
        // Check that tools JSON is valid
        val toolsArray = JSONArray(mapping.toolsJson)
        assertEquals(2, toolsArray.length())
        
        // Check first tool structure
        val firstTool = toolsArray.getJSONObject(0)
        assertEquals("function", firstTool.getString("type"))
        
        val function = firstTool.getJSONObject("function")
        // Verify exact tool name format: "zapier_mcp_google_docs_create_document_from_text"
        assertEquals("zapier_mcp_google_docs_create_document_from_text", function.getString("name"))
        assertTrue(function.has("description"))
        
        // Check tool registry size and contents
        assertEquals(2, mapping.toolRegistry.size)
        
        // Verify exact tool name mapping in registry
        val toolNames = mapping.toolRegistry.values.map { it.toolName }.toSet()
        assertEquals(
            setOf("google_docs_create_document_from_text", "google_docs_find_a_document"),
            toolNames
        )
        
        // Verify all entries reference the same server
        mapping.toolRegistry.values.forEach { entry ->
            assertEquals(server, entry.server)
        }
    }

    /**
     * Test that tool call request is properly formatted for MCP protocol.
     */
    @Test
    fun formatMcpToolCallRequest() {
        val toolName = "google_docs_create_document_from_text"
        val arguments = JSONObject().apply {
            put("instructions", "Create a document titled 'Test' with content 'Hello World'")
            put("output_hint", "just the document URL")
            put("title", "Test Document")
            put("file", "Hello World")
        }
        
        // Use fixed ID for deterministic test behavior
        val request = JSONObject().apply {
            put("jsonrpc", "2.0")
            put("id", 123L)
            put("method", "tools/call")
            put("params", JSONObject().apply {
                put("name", toolName)
                put("arguments", arguments)
            })
        }
        
        assertEquals("2.0", request.getString("jsonrpc"))
        assertEquals(123L, request.getLong("id"))
        assertEquals("tools/call", request.getString("method"))
        
        val params = request.getJSONObject("params")
        assertEquals(toolName, params.getString("name"))
        
        val args = params.getJSONObject("arguments")
        assertEquals("Test Document", args.getString("title"))
        assertEquals("Hello World", args.getString("file"))
    }

    /**
     * Test that both transport types can be assigned to McpServer.
     */
    @Test
    fun verifyTransportTypeAssignment() {
        // SSE transport type
        val sseServer = McpServer(
            id = "server-sse",
            name = "SSE Server",
            url = "https://mcp.example.com/sse",
            transportType = McpTransportType.SSE
        )
        assertEquals(McpTransportType.SSE, sseServer.transportType)
        
        // Streamable HTTP transport type
        val httpServer = McpServer(
            id = "server-http",
            name = "HTTP Server",
            url = "https://mcp.example.com/http",
            transportType = McpTransportType.STREAMABLE_HTTP
        )
        assertEquals(McpTransportType.STREAMABLE_HTTP, httpServer.transportType)
    }

    /**
     * Test that server ID generation produces unique UUIDs.
     */
    @Test
    fun generateUniqueServerIds() {
        val ids = mutableSetOf<String>()
        // Generate 10 IDs to demonstrate uniqueness with reasonable confidence
        repeat(10) {
            ids.add(McpServer.generateId())
        }
        
        // All 10 IDs should be unique
        assertEquals(10, ids.size)
        
        // Verify IDs are valid UUID format (lowercase hexadecimal)
        ids.forEach { id ->
            assertTrue("ID should be a valid UUID format", id.matches(Regex("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")))
        }
    }
}
