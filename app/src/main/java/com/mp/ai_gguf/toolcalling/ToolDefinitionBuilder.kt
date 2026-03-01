package com.mp.ai_gguf.toolcalling

import org.json.JSONArray
import org.json.JSONObject

/**
 * Builder for tool definitions in OpenAI function-calling format.
 */
class ToolDefinitionBuilder(
    val name: String,
    val description: String
) {
    private data class ParamDef(
        val name: String,
        val description: String,
        val type: String,
        val required: Boolean,
        val enumValues: List<String>? = null
    )

    private val params = mutableListOf<ParamDef>()

    fun stringParam(name: String, description: String, required: Boolean = true): ToolDefinitionBuilder {
        params.add(ParamDef(name, description, "string", required))
        return this
    }

    fun numberParam(name: String, description: String, required: Boolean = true): ToolDefinitionBuilder {
        params.add(ParamDef(name, description, "number", required))
        return this
    }

    fun integerParam(name: String, description: String, required: Boolean = true): ToolDefinitionBuilder {
        params.add(ParamDef(name, description, "integer", required))
        return this
    }

    fun booleanParam(name: String, description: String, required: Boolean = true): ToolDefinitionBuilder {
        params.add(ParamDef(name, description, "boolean", required))
        return this
    }

    fun enumParam(name: String, description: String, values: List<String>, required: Boolean = true): ToolDefinitionBuilder {
        params.add(ParamDef(name, description, "string", required, values))
        return this
    }

    fun build(): ToolDefinition {
        val properties = JSONObject()
        val required = JSONArray()

        for (param in params) {
            val prop = JSONObject()
            prop.put("type", param.type)
            prop.put("description", param.description)
            if (param.enumValues != null) {
                prop.put("enum", JSONArray(param.enumValues))
            }
            properties.put(param.name, prop)
            if (param.required) {
                required.put(param.name)
            }
        }

        val parameters = JSONObject()
        parameters.put("type", "object")
        parameters.put("properties", properties)
        if (required.length() > 0) {
            parameters.put("required", required)
        }

        return ToolDefinition(name, description, parameters)
    }

    class ToolDefinition(
        val name: String,
        val description: String,
        private val parameters: JSONObject
    ) {
        fun toOpenAIFormat(): JSONObject {
            val function = JSONObject()
            function.put("name", name)
            function.put("description", description)
            function.put("parameters", parameters)
            return function
        }
    }
}
