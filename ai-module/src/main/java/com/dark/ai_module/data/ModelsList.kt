package com.dark.ai_module.data

import android.content.Context
import com.dark.ai_module.model.ModelData
import com.dark.ai_module.model.ModelProvider
import java.io.File

object ModelsList {

    // Chat template with params + optional GBNF block.
    val defaultChatTemplate = """
        <|im_start|>system
        You are a helpful assistant.  Keep responses short and concise.
        <|im_end|>
        
        <|im_start|>user
        {{user_msg}}
        <|im_end|>
        
        <|im_start|>assistant
        <|im_end|>
        """.trimIndent()

    val defaultSystemPrompt = """
        You are a helpful assistant.
    """.trimIndent()

    val toolCallingChatTemplate = """
    {%- if professional is defined or emotional is defined -%}
    <|im_start|>system
    The assistant should modulate style accordingly while staying accurate.
    <|im_end|>
    {%- endif -%}
    {%- if gbnf is defined and gbnf|length > 0 -%}
    <|im_start|>system
    The assistant's NEXT message MUST conform to the following GBNF grammar.
    If a token would violate the grammar, do not emit it.
    <GBNF>
    {{ gbnf }}
    </GBNF>
    <|im_end|>
    {%- endif -%}
    {%- for m in messages -%}
    <|im_start|>{{ m['role'] }}
    {{ m['content'] }}
    <|im_end|>
    {%- endfor -%}
    {%- if add_generation_prompt -%}
    <|im_start|>assistant
    {%- endif -%}
""".trimIndent()

    val toolCallingSystemPrompt = """
        You are a tool‑calling assistant. Tools are provided to you as a schema; NEVER echo the schema back. 
    """.trimIndent()

    fun getModelList(context: Context): List<ModelData> {
        val modelsDir = File(context.filesDir, "models")
        if (!modelsDir.exists()) modelsDir.mkdirs()

        // Path to the locally‑stored file is the *modelsDir* + filename
        // URL is the download location in the marketplace.
        val kodifyNano = ModelData(
            modelName = "Kodify‑Nano‑GGUF",
            providerName = ModelProvider.LocalGGUF.toString(),
            modelPath = File(modelsDir, "Kodify‑Nano‑GGUF.gguf").absolutePath,
            modelUrl = "https://huggingface.co/MTSAIR/Kodify-Nano-GGUF/resolve/main/Kodify_Nano_q4_k_s.gguf?download=true",
            ctxSize = 8_192,
            isToolCalling = true,
            isImported = false,
            chatTemplate = defaultChatTemplate,
            systemPrompt = defaultSystemPrompt
        )

        val lucy128K = ModelData(
            modelName = "Lucy‑128k‑GGUF",
            providerName = ModelProvider.LocalGGUF.toString(),
            modelPath = File(modelsDir, "Lucy‑128k‑GGUF.gguf").absolutePath,
            modelUrl = "https://huggingface.co/Menlo/Lucy-128k-gguf/resolve/main/lucy_128k-Q3_K_S.gguf?download=true",
            ctxSize = 8_192,
            isToolCalling = true,
            isImported = false,
            chatTemplate = defaultChatTemplate,
            systemPrompt = defaultSystemPrompt
        )

        return listOf(kodifyNano, lucy128K)
    }
}