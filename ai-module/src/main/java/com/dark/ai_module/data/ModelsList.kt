package com.dark.ai_module.data

import android.content.Context
import com.dark.ai_module.model.ModelsData
import java.io.File

object ModelsList {

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
        id = -1,
        "Custom Model",
        "Custom Model",
        4096,
        "NO",
        "---",
        "---",
        "",
        chatTemplate,
        0
    )
}