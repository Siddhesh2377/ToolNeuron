package com.dark.ai_module.data

import android.content.Context
import com.dark.ai_module.model.ModelsData
import java.io.File

object ModelsList {

    // Chat template with params + optional GBNF block.
    val chatTemplate = """
    {# ChatML-ish, llama.cpp minimal-engine safe #}

    {# === Behavior controls (sliders) === #}
    {%- if professional is defined or emotional is defined -%}
    <|im_start|>system
    The assistant should modulate style accordingly while staying accurate.
    <|im_end|>
    {%- endif -%}

    {# === Optional GBNF constraint (engine-level preferred; prompt is fallback) === #}
    {%- if gbnf is defined and gbnf|length > 0 -%}
    <|im_start|>system
    The assistant's NEXT message MUST conform to the following GBNF grammar.
    If a token would violate the grammar, do not emit it.
    <GBNF>
    {{ gbnf }}
    </GBNF>
    <|im_end|>
    {%- endif -%}

    {# === Conversation === #}
    {%- for m in messages -%}
    <|im_start|>{{ m['role'] }}
    {{ m['content'] }}
    <|im_end|>
    {%- endfor -%}

    {%- if add_generation_prompt -%}
    <|im_start|>assistant
    {%- endif -%}
""".trimIndent()

    val generalPurposeSystemPrompt = """
        You are a helpful, respectful and honest assistant. Always be as helpful as possible, and do not provide harmful or explicit content.
    """.trimIndent()

    fun getToolCallSystemPrompt(buildToolsListForPrompt: String): String {
        return """
                You are a precise, concise assistant.
                
                # Protocol
                - Turns are delimited by tokens `<|im_start|>role … <|im_end|>`.
                - If you NEED a tool, respond with JSON ONLY:
                  {"type":"tool_call","tool":"<tool_name>","arguments":{...}}
                - If you DO NOT need a tool, respond with JSON ONLY:
                  {"type":"final","content":"<your answer>"}
                - No extra prose, no markdown, no comments, no trailing commas.
                
                # Available tools (USE EXACT NAMES; do NOT rename or alias)
                $buildToolsListForPrompt 
                
                # Arguments rules
                - Use ONLY the arguments listed for the tool. Do not add extra keys.
                - Keep types exact (String vs Number). If a limit exists, respect it.
                
                # JSON schema you must follow when calling a tool
                {
                  "type": "tool_call",
                  "tool": "<one of the exact names above>",
                  "arguments": { /* strictly the documented keys for that tool */ }
                }
                """.trimIndent()

    }

    val toolCallSYSTEMP = """
        You are a tool‑calling assistant. Tools are provided to you as a schema; NEVER echo the schema back. 
    """.trimIndent()

    fun getModelList(context: Context): List<ModelsData> {

        val modelsDir = File(context.filesDir, "Models")
        if (!modelsDir.exists()) {
            modelsDir.mkdirs() // Create "Models" folder if not exists
        }

        val kodifyNano = ModelsData(
            id = 1,
            "Kodify-Nano-GGUF",
            "Kodify-Nano-GGUF - GGUF version of MTSAIR/Kodify-Nano, optimized for CPU/GPU inference with Ollama/llama.cpp. Lightweight LLM for code development tasks with minimal resource requirements.",
            32768,
            "YES",
            "https://huggingface.co/MTSAIR/Kodify-Nano-GGUF/resolve/main/Kodify_Nano_q4_k_s.gguf?download=true",
            "https://huggingface.co/MTSAIR/Kodify-Nano-GGUF",
            File(modelsDir, "Kodify-Nano-GGUF.gguf").absolutePath,
            chatTemplate,
            940
        )


        val lucy128K = ModelsData(
            id = 3,
            "Lucy-128k-gguf",
            "Lucy is a compact but capable 1.7B model focused on agentic web search and lightweight browsing. Built on Qwen3-1.7B, Lucy inherits deep research capabilities from larger models while being optimized to run efficiently on mobile devices, even with CPU-only configurations.",
            131072,
            "YES",
            "https://huggingface.co/Menlo/Lucy-128k-gguf/resolve/main/lucy_128k-Q3_K_S.gguf?download=true",
            "https://huggingface.co/Menlo/Lucy-128k-gguf",
            File(modelsDir, "lucy_128k-Q3_K_S.gguf").absolutePath,
            chatTemplate,
            940
        )

        val qwenZeroCoder = ModelsData(
            id = 4,
            "Qwen3-Zero-Coder-Reasoning-0.8B",
            "This is a coder/programming model, will full reasoning on the Qwen 3 platform that is insanely fast - hitting over 150 t/s on moderate hardware, and 50 t/s+ on CPU only...",
            40960,
            "YES",
            "https://huggingface.co/DavidAU/Qwen3-Zero-Coder-Reasoning-0.8B-NEO-EX-GGUF/resolve/main/Qwen3-Zero-Coder-Reasoning-0.8B-NEO-EX-D_AU-Q4_K_M-imat.gguf?download=true",
            "https://huggingface.co/DavidAU/Qwen3-Zero-Coder-Reasoning-0.8B-NEO-EX-GGUF",
            File(modelsDir, "QwenZeroCoder-Q4_K_M-imat.gguf").absolutePath,
            chatTemplate,
            528
        )



        return listOf(qwenZeroCoder, lucy128K, kodifyNano)
    }

    val CUSTOM_MODEL = ModelsData(
        id = -1, "Custom Model", "Custom Model", 4096, "NO", "---", "---", "", chatTemplate, 0
    )
}