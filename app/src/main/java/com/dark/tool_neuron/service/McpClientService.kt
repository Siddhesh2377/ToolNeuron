package com.dark.tool_neuron.service

import android.util.Log
import com.dark.tool_neuron.models.table_schema.McpServer
import com.dark.tool_neuron.models.table_schema.McpTransportType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * MCP Client response data
 */
data class McpToolInfo(
    val name: String,
    val description: String?,
    val inputSchema: String?
)

data class McpTestResult(
    val success: Boolean,
    val message: String,
    val tools: List<McpToolInfo> = emptyList(),
    val serverInfo: String? = null
)

/**
 * Client service for connecting to remote MCP (Model Context Protocol) servers.
 * Supports both SSE (Server-Sent Events) and Streamable HTTP transport types.
 * 
 * Transport Types:
 * - SSE: Uses text/event-stream for responses (commonly used by servers like Zapier MCP)
 * - Streamable HTTP: Uses standard JSON responses
 */
@Singleton
class McpClientService @Inject constructor() {
    
    companion object {
        private const val TAG = "McpClientService"
        private const val CONNECT_TIMEOUT_SECONDS = 15L
        private const val READ_TIMEOUT_SECONDS = 30L
        private const val MCP_PROTOCOL_VERSION = "2024-11-05"
        private const val CLIENT_NAME = "ToolNeuron"
        private const val CLIENT_VERSION = "1.0.0"
        private val JSON_MEDIA_TYPE = "application/json".toMediaType()
        // Accept headers for different transport types
        private const val ACCEPT_HEADER_SSE = "application/json, text/event-stream"
        private const val ACCEPT_HEADER_HTTP = "application/json"
    }
    
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .build()
    
    /**
     * Get the appropriate Accept header based on transport type
     */
    private fun getAcceptHeader(transportType: McpTransportType): String {
        return when (transportType) {
            McpTransportType.SSE -> ACCEPT_HEADER_SSE
            McpTransportType.STREAMABLE_HTTP -> ACCEPT_HEADER_HTTP
        }
    }
    
    /**
     * Parse response body, handling SSE format for SSE transport.
     * For Streamable HTTP, returns the raw JSON body (no SSE envelope to parse).
     */
    private fun parseResponse(responseBody: String, transportType: McpTransportType): String {
        return when (transportType) {
            McpTransportType.SSE -> parseSseResponse(responseBody)
            McpTransportType.STREAMABLE_HTTP -> responseBody  // Already JSON, no SSE envelope to parse
        }
    }
    
    /**
     * Parse SSE (Server-Sent Events) response format.
     * SSE responses come as "event: message\ndata: {...json...}\n\n"
     * This handles single-event responses commonly used in MCP request/response patterns.
     */
    private fun parseSseResponse(responseBody: String): String {
        // Check if this is an SSE response
        if (!responseBody.contains("data:")) {
            // Not SSE format, return as-is
            return responseBody
        }
        
        // Split by double newlines to separate events
        val events = responseBody.split("\n\n")
        
        // Find the last event with data (for request/response pattern)
        for (event in events.reversed()) {
            val lines = event.lines()
            val dataLines = lines.filter { it.startsWith("data:") }
            
            if (dataLines.isNotEmpty()) {
                // Extract JSON from "data: {...}" format
                // Multiple data lines in same event should be joined with newlines per SSE spec
                return dataLines.joinToString("\n") { it.removePrefix("data:").trim() }
            }
        }
        
        // Fallback: return original response
        return responseBody
    }
    
    /**
     * Test connection to an MCP server and retrieve server capabilities
     */
    suspend fun testConnection(server: McpServer): McpTestResult = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Testing connection to MCP server: ${server.name} at ${server.url} (transport: ${server.transportType})")
            
            // Build the initialize request according to MCP protocol
            val initializeRequest = JSONObject().apply {
                put("jsonrpc", "2.0")
                put("id", 1)
                put("method", "initialize")
                put("params", JSONObject().apply {
                    put("protocolVersion", MCP_PROTOCOL_VERSION)
                    put("capabilities", JSONObject())
                    put("clientInfo", JSONObject().apply {
                        put("name", CLIENT_NAME)
                        put("version", CLIENT_VERSION)
                    })
                })
            }
            
            val requestBuilder = Request.Builder()
                .url(server.url)
                .post(initializeRequest.toString().toRequestBody(JSON_MEDIA_TYPE))
                .addHeader("Content-Type", "application/json")
                .addHeader("Accept", getAcceptHeader(server.transportType))
            
            // Add API key if provided
            server.apiKey?.let { key ->
                requestBuilder.addHeader("Authorization", "Bearer $key")
            }
            
            val response = httpClient.newCall(requestBuilder.build()).execute()
            
            if (!response.isSuccessful) {
                return@withContext McpTestResult(
                    success = false,
                    message = "Server returned error: ${response.code} ${response.message}"
                )
            }
            
            val rawResponseBody = response.body?.string()
            if (rawResponseBody.isNullOrBlank()) {
                return@withContext McpTestResult(
                    success = false,
                    message = "Server returned empty response"
                )
            }
            
            // Parse response based on transport type
            val responseBody = parseResponse(rawResponseBody, server.transportType)
            val jsonResponse = JSONObject(responseBody)
            
            // Check for JSON-RPC error
            if (jsonResponse.has("error")) {
                val error = jsonResponse.getJSONObject("error")
                return@withContext McpTestResult(
                    success = false,
                    message = "Server error: ${error.optString("message", "Unknown error")}"
                )
            }
            
            // Parse the result
            val result = jsonResponse.optJSONObject("result")
            val serverInfo = result?.optJSONObject("serverInfo")
            val serverName = serverInfo?.optString("name", "Unknown Server") ?: "Unknown Server"
            val serverVersion = serverInfo?.optString("version", "") ?: ""
            
            // Now list available tools
            val tools = listTools(server)
            
            McpTestResult(
                success = true,
                message = "Connected successfully",
                tools = tools,
                serverInfo = if (serverVersion.isNotEmpty()) "$serverName v$serverVersion" else serverName
            )
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to connect to MCP server: ${e.message}", e)
            McpTestResult(
                success = false,
                message = "Connection failed: ${e.message ?: "Unknown error"}"
            )
        }
    }
    
    /**
     * List available tools from an MCP server
     */
    private suspend fun listTools(server: McpServer): List<McpToolInfo> = withContext(Dispatchers.IO) {
        try {
            val listToolsRequest = JSONObject().apply {
                put("jsonrpc", "2.0")
                put("id", 2)
                put("method", "tools/list")
                put("params", JSONObject())
            }
            
            val requestBuilder = Request.Builder()
                .url(server.url)
                .post(listToolsRequest.toString().toRequestBody(JSON_MEDIA_TYPE))
                .addHeader("Content-Type", "application/json")
                .addHeader("Accept", getAcceptHeader(server.transportType))
            
            server.apiKey?.let { key ->
                requestBuilder.addHeader("Authorization", "Bearer $key")
            }
            
            val response = httpClient.newCall(requestBuilder.build()).execute()
            
            if (!response.isSuccessful) {
                return@withContext emptyList()
            }
            
            val rawResponseBody = response.body?.string() ?: return@withContext emptyList()
            // Parse response based on transport type
            val responseBody = parseResponse(rawResponseBody, server.transportType)
            val jsonResponse = JSONObject(responseBody)
            
            if (jsonResponse.has("error")) {
                return@withContext emptyList()
            }
            
            val result = jsonResponse.optJSONObject("result") ?: return@withContext emptyList()
            val toolsArray = result.optJSONArray("tools") ?: return@withContext emptyList()
            
            val tools = mutableListOf<McpToolInfo>()
            for (i in 0 until toolsArray.length()) {
                val tool = toolsArray.getJSONObject(i)
                tools.add(McpToolInfo(
                    name = tool.getString("name"),
                    description = tool.optString("description", null),
                    inputSchema = tool.optJSONObject("inputSchema")?.toString()
                ))
            }
            
            tools
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to list tools: ${e.message}", e)
            emptyList()
        }
    }
    
    /**
     * Call a tool on an MCP server
     */
    suspend fun callTool(
        server: McpServer,
        toolName: String,
        arguments: Map<String, Any>
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val callToolRequest = JSONObject().apply {
                put("jsonrpc", "2.0")
                put("id", System.currentTimeMillis())
                put("method", "tools/call")
                put("params", JSONObject().apply {
                    put("name", toolName)
                    put("arguments", JSONObject(arguments))
                })
            }
            
            val requestBuilder = Request.Builder()
                .url(server.url)
                .post(callToolRequest.toString().toRequestBody(JSON_MEDIA_TYPE))
                .addHeader("Content-Type", "application/json")
                .addHeader("Accept", getAcceptHeader(server.transportType))
            
            server.apiKey?.let { key ->
                requestBuilder.addHeader("Authorization", "Bearer $key")
            }
            
            val response = httpClient.newCall(requestBuilder.build()).execute()
            
            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("Server returned: ${response.code}"))
            }
            
            val rawResponseBody = response.body?.string()
                ?: return@withContext Result.failure(Exception("Empty response"))
            
            // Parse response based on transport type
            val responseBody = parseResponse(rawResponseBody, server.transportType)
            val jsonResponse = JSONObject(responseBody)
            
            if (jsonResponse.has("error")) {
                val error = jsonResponse.getJSONObject("error")
                return@withContext Result.failure(Exception(error.optString("message", "Unknown error")))
            }
            
            val result = jsonResponse.optJSONObject("result")
            Result.success(result?.toString() ?: responseBody)
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to call tool: ${e.message}", e)
            Result.failure(e)
        }
    }
}
