package com.dark.ai_module.data

import android.content.Context
import com.dark.ai_module.model.ModelsData
import java.io.File

object ModelsList {
    fun getModelList(context: Context): List<ModelsData> {

        val modelsDir = File(context.filesDir, "Models")
        if (!modelsDir.exists()) {
            modelsDir.mkdirs() // Create "Models" folder if not exists
        }

        val chatTemplate = """
            <|im_start|>system
            Your name is Neuron, developed by NeuroV. You only send short, polite, relevant auto-replies when the user is unavailable. Never overthink, reason, or generate long responses. Keep replies under 10 words.
            Use the following parameters to adjust your tone:
            - Professionalism: {{ professionalism }} (0.1 - 9.0)
            - Emotional: {{ emotional }} (0.1 - 9.0)
            
            Respond with tone adjusted to these values.
            <|im_end|>
            {% for message in messages %}
            <|im_start|>{{ message['role'] }}
            {{ message['content'] }}<|im_end|>
            {% endfor %}
            {% if add_generation_prompt -%}
            <|im_start|>assistant
            {% endif %}
        """.trimIndent()



        val janNanoGuff = ModelsData(
            id = 0,
            "Jan-Nano-Guff",
            "Jan Nano is a fine-tuned language model built on top of the Qwen3 architecture. it balances compact size and extended context length, making it ideal for efficient, high-quality text generation in local or embedded environments.",
            40960,
            "YES",
            "https://huggingface.co/Menlo/Jan-nano-gguf/resolve/main/jan-nano-4b-Q4_K_M.gguf?download=true",
            "https://huggingface.co/Menlo/Jan-nano-gguf",
            File(modelsDir, "Jan-Nano-Guff.gguf").absolutePath,
            chatTemplate,
            2500
        )

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

        val llamaToolCallV2 = ModelsData(
            id = 2,
            "Llama_3.2_1B_Intruct-TC-V2",
            "A specialized fine-tuned version of the meta-llama/Llama-3.2-1B-Instruct model enhanced with function/tool calling capabilities. The model leverages the nguyenthanhthuan/function-calling-sharegpt dataset for training.",
            131072,
            "YES",
            "https://huggingface.co/mav23/Llama_3.2_1B_Intruct_Tool_Calling_V2-GGUF/resolve/main/llama_3.2_1b_intruct_tool_calling_v2.Q5_K_M.gguf?download=true",
            "https://huggingface.co/mav23/Llama_3.2_1B_Intruct_Tool_Calling_V2-GGUF",
            File(modelsDir, "llama_3.2_1b_instruct_tool_calling_v2.Q5_K_M.gguf").absolutePath,
            chatTemplate,
            912
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

        val smolJson = ModelsData(
            id = 4,
            "SmolLM2-360M-Instruct-Text-2-JSON",
            "This model is fine-tuned on SmolLM2-360M-Instruct-bnb-4bit using QLoRA to extract structured calendar event information from natural language text. It identifies and structures key scheduling entities like action, date, time, attendees, location, duration, recurrence, and notes.",
            8192,
            "YES",
            "https://huggingface.co/pramodkoujalagi/SmolLM2-360M-Instruct-Text-2-JSON/resolve/main/SmolLM2-360M-Instruct-Text-2-JSON.Q4_K_M.gguf?download=true",
            "https://huggingface.co/pramodkoujalagi/SmolLM2-360M-Instruct-Text-2-JSON",
            File(modelsDir, "SmolLM2-JSON.Q4_K_M.gguf").absolutePath,
            chatTemplate,
            303
        )

        return listOf(smolJson, lucy128K, kodifyNano, janNanoGuff, llamaToolCallV2)
    }
}