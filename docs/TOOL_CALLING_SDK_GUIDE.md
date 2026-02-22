# Tool Calling SDK Guide

## Overview

Clean, professional SDK for tool calling with Qwen models on Android.

**Key Features:**
- ✅ **Qwen-only validation** - Only works with Qwen models
- ✅ **Type-safe tool definitions** - DSL for defining tools
- ✅ **Automatic configuration** - Chat template & system prompt set automatically
- ✅ **Simple API** - Register tools, enable, and go

## Quick Start

### 1. Setup

```kotlin
import com.mp.ai_gguf.GGUFNativeLib
import com.mp.ai_gguf.toolcalling.*

val gguf = GGUFNativeLib()
val toolCallManager = ToolCallManager(gguf)
```

### 2. Define Tools

```kotlin
// Simple tool definition
val weatherTool = tool("get_weather", "Get current weather") {
    stringParam("location", "City name", required = true)
    stringParam(
        "units",
        "Temperature units",
        enum = listOf("celsius", "fahrenheit")
    )
}

// Register tools
toolCallManager.registerTools(
    weatherTool,
    tool("get_time", "Get current time") {
        stringParam("format", "Time format", enum = listOf("12h", "24h"))
    }
)
```

### 3. Load Model & Enable

```kotlin
// Load Qwen model
gguf.nativeLoadModelFromFd(fd, threads, ctxSize, ...)

// Check if compatible
if (!toolCallManager.isModelCompatible()) {
    println("Not a Qwen model: ${toolCallManager.modelArchitecture}")
    return
}

// Enable tool calling
if (toolCallManager.enable()) {
    println("✅ Tool calling enabled!")
} else {
    println("❌ Failed: ${toolCallManager.lastError}")
}
```

### 4. Generate with Tools

```kotlin
gguf.nativeGenerateStream(prompt, maxTokens, object : StreamCallback {
    override fun onToolCall(name: String, argsJson: String) {
        // Parse tool call
        val toolCall = toolCallManager.parseToolCall(argsJson)

        if (toolCall != null) {
            // Execute tool
            val result = executeTool(toolCall)

            // Show result to user
            println("🔧 Tool: ${toolCall.name}")
            println("✅ Result: $result")
        }
    }

    override fun onToken(token: String) {
        print(token)
    }

    override fun onDone() {
        println("\n✅ Complete")
    }
})
```

## Tool Definition DSL

### Parameter Types

```kotlin
tool("my_tool", "Tool description") {
    // String parameter
    stringParam("name", "Parameter description", required = true)

    // String with enum constraint
    stringParam("option", "Choose one", enum = listOf("a", "b", "c"))

    // Number parameter
    numberParam("count", "How many", required = false)

    // Boolean parameter
    booleanParam("enabled", "Enable feature")

    // Object parameter
    objectParam("config", "Configuration", properties = mapOf(
        "host" to ToolParameter("string", "Hostname"),
        "port" to ToolParameter("number", "Port number")
    ))

    // Array parameter
    arrayParam("items", "List of items",
        items = ToolParameter("string", "Item"))
}
```

### Builder Pattern

```kotlin
val tool = ToolDefinitionBuilder("calculator", "Perform calculations")
    .numberParam("a", "First number", required = true)
    .numberParam("b", "Second number", required = true)
    .stringParam("operation", "Math operation",
        enum = listOf("add", "subtract", "multiply", "divide"))
    .build()
```

## Tool Execution

### Parsing Tool Calls

```kotlin
val toolCall = toolCallManager.parseToolCall(argsJson)

// Access arguments type-safely
val location = toolCall.getString("location", "Unknown")
val count = toolCall.getInt("count", 0)
val enabled = toolCall.getBoolean("enabled", false)

// Check if argument exists
if (toolCall.has("optional_param")) {
    val value = toolCall.getString("optional_param")
}
```

### Example Tool Implementations

```kotlin
fun executeTool(toolCall: ToolCall): String {
    return when (toolCall.name) {
        "get_weather" -> {
            val location = toolCall.getString("location")
            val units = toolCall.getString("units", "celsius")
            fetchWeather(location, units)
        }

        "get_time" -> {
            val format = toolCall.getString("format", "24h")
            getCurrentTime(format)
        }

        "calculator" -> {
            val a = toolCall.getDouble("a")
            val b = toolCall.getDouble("b")
            val op = toolCall.getString("operation")
            calculate(a, b, op).toString()
        }

        else -> "Unknown tool: ${toolCall.name}"
    }
}
```

## Architecture Validation

The SDK enforces Qwen-only usage:

```kotlin
// Check before enabling
if (toolCallManager.isModelCompatible()) {
    toolCallManager.enable()
} else {
    val arch = toolCallManager.modelArchitecture
    println("Tool calling requires Qwen model, got: $arch")
}
```

**Supported:** `qwen`, `qwen2`, `qwen2.5`
**Not supported:** `llama`, `mistral`, `gemma`, etc.

## Common Tools Example

```kotlin
// Use pre-configured common tools
val toolCallManager = ToolCallManager.withCommonTools(gguf)

// Includes:
// - get_current_time(format)
// - show_message(message, duration)
// - get_device_info(info_type)

toolCallManager.enable()
```

## API Reference

### ToolCallManager

```kotlin
class ToolCallManager(nativeLib: GGUFNativeLib) {
    // Model validation
    val modelArchitecture: String
    fun isModelCompatible(): Boolean
    fun isEnabled(): Boolean

    // Tool registration
    fun registerTool(tool: ToolDefinition): Boolean
    fun registerTools(vararg tools: ToolDefinition): Boolean
    fun unregisterTool(name: String): Boolean
    fun clearTools()

    // Enable/disable
    fun enable(): Boolean
    fun disable()
    fun reset()

    // Tool call parsing
    fun parseToolCall(jsonResponse: String): ToolCall?

    // Error handling
    var lastError: String?
}
```

### ToolDefinition

```kotlin
data class ToolDefinition(
    val name: String,
    val description: String,
    val parameters: Map<String, ToolParameter>,
    val required: List<String>
) {
    fun toOpenAIFormat(): JSONObject
}
```

### ToolCall

```kotlin
data class ToolCall(
    val name: String,
    val arguments: JSONObject
) {
    fun getString(key: String, default: String = ""): String
    fun getInt(key: String, default: Int = 0): Int
    fun getBoolean(key: String, default: Boolean = false): Boolean
    fun getDouble(key: String, default: Double = 0.0): Double
    fun has(key: String): Boolean
    fun toJson(): JSONObject
}
```

## Error Handling

```kotlin
// Check after operations
if (!toolCallManager.registerTool(myTool)) {
    println("Failed to register: ${toolCallManager.lastError}")
}

if (!toolCallManager.enable()) {
    println("Failed to enable: ${toolCallManager.lastError}")
}

// Parse tool calls safely
val toolCall = toolCallManager.parseToolCall(json)
if (toolCall == null) {
    println("Parse failed: ${toolCallManager.lastError}")
}
```

## Complete Example

```kotlin
// 1. Setup
val gguf = GGUFNativeLib()
val toolCallManager = ToolCallManager(gguf)

// 2. Register tools
toolCallManager.registerTools(
    tool("get_weather", "Get weather for a location") {
        stringParam("city", "City name", required = true)
        stringParam("units", "celsius or fahrenheit",
            enum = listOf("celsius", "fahrenheit"))
    },
    tool("set_alarm", "Set an alarm") {
        stringParam("time", "Time in HH:MM format", required = true)
        stringParam("label", "Alarm label")
    }
)

// 3. Load model
val fd = contentResolver.openFileDescriptor(modelUri, "r")!!.detachFd()
gguf.nativeLoadModelFromFd(fd, threads = 4, ctxSize = 2048, ...)

// 4. Validate and enable
if (toolCallManager.isModelCompatible()) {
    if (toolCallManager.enable()) {
        println("Ready for tool calling!")
    }
}

// 5. Generate
gguf.nativeGenerateStream("What's the weather in Tokyo?", 256,
    object : StreamCallback {
        override fun onToolCall(name: String, argsJson: String) {
            val call = toolCallManager.parseToolCall(argsJson) ?: return

            when (call.name) {
                "get_weather" -> {
                    val city = call.getString("city")
                    val units = call.getString("units", "celsius")
                    val weather = fetchWeather(city, units)
                    println("Weather: $weather")
                }
            }
        }

        override fun onToken(token: String) { print(token) }
        override fun onDone() { println("\nDone!") }
    }
)
```

## Best Practices

### 1. Tool Design
- **Atomic tools** - One clear purpose per tool
- **Clear descriptions** - Help the model understand when to use it
- **Explicit parameters** - Name parameters clearly

### 2. Error Handling
- Always check `isModelCompatible()` before enabling
- Check `lastError` when operations fail
- Handle null returns from `parseToolCall()`

### 3. Performance
- Register tools once during initialization
- Reuse ToolCallManager across requests
- Cache tool execution results when appropriate

### 4. Security
- Validate tool call arguments before execution
- Sanitize user-provided parameters
- Set reasonable limits (rate limiting, timeouts)

## Troubleshooting

### Tool calling not working

**Check:**
```kotlin
// 1. Model compatibility
if (!toolCallManager.isModelCompatible()) {
    println("Architecture: ${toolCallManager.modelArchitecture}")
    // Must be Qwen
}

// 2. Tools registered
if (toolCallManager.getRegisteredTools().isEmpty()) {
    println("No tools registered!")
}

// 3. Enabled successfully
if (!toolCallManager.isEnabled()) {
    println("Not enabled: ${toolCallManager.lastError}")
}
```

### Model outputs text instead of JSON

The model is not detecting the need for a tool call. Try:
- More explicit user prompts
- Better tool descriptions
- Check if model is actually Qwen

### Parse errors

```kotlin
val toolCall = toolCallManager.parseToolCall(json)
if (toolCall == null) {
    // Check the error
    println("Parse error: ${toolCallManager.lastError}")

    // Debug: print the raw JSON
    println("Raw JSON: $json")
}
```

## Summary

**Simple workflow:**
1. ✅ Create `ToolCallManager`
2. ✅ Register tools with DSL
3. ✅ Load Qwen model
4. ✅ Call `enable()`
5. ✅ Generate with `nativeGenerateStream`
6. ✅ Handle `onToolCall()` callback
7. ✅ Parse and execute tools

**Clean, focused, professional.**
