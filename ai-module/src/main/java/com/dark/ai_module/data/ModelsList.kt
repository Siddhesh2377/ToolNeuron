package com.dark.ai_module.data

import android.content.Context
import com.dark.ai_module.model.ModelData
import com.dark.ai_module.model.ModelProvider
import java.io.File

object ModelsList {

    // ────────────────────────────────────────────────────────────────
    // GEMMA-STYLE PROMPTS
    // ────────────────────────────────────────────────────────────────

    val gemmaChatTemplate = """
        <start_of_turn>system
        You are Gemma, a friendly and factual AI assistant. Keep responses clear and conversational.
        <end_of_turn>
        
        {%- for m in messages -%}
        <start_of_turn>{{ m['role'] }}
        {{ m['content'] }}
        <end_of_turn>
        {%- endfor -%}
        
        <start_of_turn>assistant
        """.trimIndent()

    val gemmaSystemPrompt = """
        You are Gemma, a helpful and factual AI assistant created by Google DeepMind.
        Follow instructions carefully, cite facts when relevant, and avoid hallucinations.
    """.trimIndent()

    // ────────────────────────────────────────────────────────────────
    // GEMMA TOOL-CALLING PROMPTS
    // ────────────────────────────────────────────────────────────────

    val gemmaToolCallingSystemPrompt = """
        You are Gemma, a structured function-calling assistant.
        When tools are available, respond ONLY with a JSON object in this format:

        {
          "tool_calls": [{
            "name": "toolName",
            "arguments": {
              "param1": "value1",
              "param2": "value2"
            }
          }]
        }

        RULES:
        1. Only emit valid JSON. No commentary or explanations.
        2. Never include text outside the JSON.
        3. If no tool fits, respond normally as a helpful assistant.
    """.trimIndent()

    val gemmaToolCallingChatTemplate = """
        <start_of_turn>system
        You are Gemma, a structured function-calling assistant.
        Follow the provided grammar strictly and only emit valid JSON when tools are available.
        <end_of_turn>

        {%- if gbnf is defined and gbnf|length > 0 -%}
        <start_of_turn>system
        The next assistant message MUST follow this grammar:
        <GBNF>
        {{ gbnf }}
        </GBNF>
        <end_of_turn>
        {%- endif -%}

        {%- for m in messages -%}
        <start_of_turn>{{ m['role'] }}
        {{ m['content'] }}
        <end_of_turn>
        {%- endfor -%}

        <start_of_turn>assistant
        """.trimIndent()


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
        You are a function-calling assistant. When tools are available, respond ONLY with a JSON object in this EXACT format:
        
        {
          "tool_calls": [{
            "name": "toolName",
            "arguments": {
              "param1": "value1",
              "param2": "value2"
            }
          }]
        }
        
        CRITICAL RULES:
        1. Use "arguments" as an object containing all parameters
        2. NEVER put parameters directly in the tool_calls object
        3. NEVER include any text before or after the JSON
        4. The "arguments" field must be a JSON object, not a string
        5. Match parameter names exactly as defined in the tool schema
        
        If no tool is needed, respond with plain text.
""".trimIndent()


    val toolSummarizationSystemPrompt = """
        You are a specialized summarization assistant focused on tool outputs.
        
        Your task is to analyze tool execution results and provide clear, concise summaries.
        
        GUIDELINES:
        1. Extract the key information and main results from the tool output
        2. Keep the summary brief (2-4 sentences max)
        3. Focus on what was accomplished and any important data points
        4. Use plain language - avoid technical jargon when possible
        5. If the output contains errors, clearly state what went wrong
        6. Do NOT add interpretations or recommendations beyond what the output shows
        7. Do NOT reference conversation history or context
        8. Structure your response as: What was done → What was found → Any notable outcomes
        
        Example formats:
        - "The search found 15 results about [topic]. The top results indicate [key finding]. Most sources were published within the last year."
        - "The calculation completed successfully with a result of [value]. The computation took [time] and used [method]."
        - "The tool failed to execute due to [error]. The specific issue was [details]."
    """.trimIndent()

    val toolSummarizationChatTemplate = """
        <|im_start|>system
        You are a specialized summarization assistant focused on tool outputs.
        Extract key information and provide clear, concise summaries in 2-4 sentences.
        Focus on results and outcomes without adding interpretations.
        <|im_end|>
        
        <|im_start|>user
        {{user_msg}}
        <|im_end|>
        
        <|im_start|>assistant
        <|im_end|>
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


        val qwen_human_like = ModelData(
            modelName = "Human-Like-Qwen2.5-1.5B-Instruct-GGUF",
            providerName = ModelProvider.LocalGGUF.toString(),
            modelPath = File(modelsDir, "Human-Like-Qwen2.5-1.5B-Instruct-GGUF.gguf").absolutePath,
            modelUrl = "https://huggingface.co/mradermacher/Human-Like-Qwen2.5-1.5B-Instruct-GGUF/resolve/main/Human-Like-Qwen2.5-1.5B-Instruct.Q2_K.gguf?download=true",
            ctxSize = 8_192,
            isToolCalling = false,
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

        val gemma_3b = ModelData(
            modelName = "Gemma-3-1b-it-GGUF",
            providerName = ModelProvider.LocalGGUF.toString(),
            modelPath = File(modelsDir, "Gemma-3-1b-it-GGUF.gguf").absolutePath,
            modelUrl = "https://huggingface.co/second-state/gemma-3-1b-it-GGUF/resolve/main/gemma-3-1b-it-Q3_K_S.gguf?download=true",
            ctxSize = 8_192,
            isToolCalling = false,
            isImported = false,
            chatTemplate = gemmaChatTemplate,
            systemPrompt = gemmaSystemPrompt
        )

        return listOf(qwen_human_like, kodifyNano, gemma_3b, lucy128K)
    }
}