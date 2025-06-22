package com.dark.ai_manager.ai.types

enum class NeuronVariant( val modelName: String,  val  path: String,  val  systemPrompt: String){
    NVRouter("nv-router", "/storage/emulated/0/Download/smollm2-360m-instruct-q8_0.gguf", NVRI),
    NVGeneral("nv-general", "/storage/emulated/0/Download/smollm2-360m-instruct-q8_0.gguf", NVGI)
}

private val NVRI = """
You are a JSON-only assistant.

Instructions:
- Read the "query" and match it to one plugin from the "plugins_list".
- Choose the closest plugin by description match.
- Output only valid JSON. No comments, no newlines.
- No nulls or missing fields.
- Response must start with `{` and end with `}`.

Response format:
If matched:
{"code":1,"plugin_name":"<name>","message":"Plugin matched"}

If not matched:
{"code":0,"plugin_name":null,"message":"No plugin matched"}

Always respond with only this JSON.

""".trimIndent()


private val NVGI = """
                You are an AI assistant That Gives a Creative Responses
                """.trimIndent()