package com.dark.tool_neuron.service

import android.util.Log
import com.dark.tool_neuron.models.table_schema.McpConnectionStatus
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
 * Supports SSE and Streamable HTTP transport types.
 */
@Singleton
class McpClientService @Inject constructor() {
    
    companion object {
        private const val TAG = "McpClientService"
        private const val CONNECT_TIMEOUT_SECONDS = 15L
        private const val READ_TIMEOUT_SECONDS = 30L
        private val JSON_MEDIA_TYPE = "application/json".toMediaType()
    }
    
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .build()
    
    /**
     * Test connection to an MCP server and retrieve server capabilities
     */
    suspend fun testConnection(server: McpServer): McpTestResult = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Testing connection to MCP server: ${server.name} at ${server.url}")
            
            // Build the initialize request according to MCP protocol
            val initializeRequest = JSONObject().apply {
                put("jsonrpc", "2.0")
                put("id", 1)
                put("method", "initialize")
                put("params", JSONObject().apply {
                    put("protocolVersion", "2024-11-05")
                    put("capabilities", JSONObject())
                    put("clientInfo", JSONObject().apply {
                        put("name", "ToolNeuron")
                        put("version", "1.0.0")
                    })
                })
            }
            
            val requestBuilder = Request.Builder()
                .url(server.url)
                .post(initializeRequest.toString().toRequestBody(JSON_MEDIA_TYPE))
                .addHeader("Content-Type", "application/json")
                .addHeader("Accept", "application/json")
            
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
            
            val responseBody = response.body?.string()
            if (responseBody.isNullOrBlank()) {
                return@withContext McpTestResult(
                    success = false,
                    message = "Server returned empty response"
                )
            }
            
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
                .addHeader("Accept", "application/json")
            
            server.apiKey?.let { key ->
                requestBuilder.addHeader("Authorization", "Bearer $key")
            }
            
            val response = httpClient.newCall(requestBuilder.build()).execute()
            
            if (!response.isSuccessful) {
                return@withContext emptyList()
            }
            
            val responseBody = response.body?.string() ?: return@withContext emptyList()
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
                .addHeader("Accept", "application/json")
            
            server.apiKey?.let { key ->
                requestBuilder.addHeader("Authorization", "Bearer $key")
            }
            
            val response = httpClient.newCall(requestBuilder.build()).execute()
            
            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("Server returned: ${response.code}"))
            }
            
            val responseBody = response.body?.string()
                ?: return@withContext Result.failure(Exception("Empty response"))
            
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
