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

        val janNanoGuff = ModelsData(
            id = 0,
            "Jan-Nano-Guff",
            "Jan Nano is a fine-tuned language model built on top of the Qwen3 architecture. it balances compact size and extended context length, making it ideal for efficient, high-quality text generation in local or embedded environments.",
            "https://huggingface.co/Menlo/Jan-nano-gguf/resolve/main/jan-nano-4b-Q4_K_M.gguf?download=true",
            "https://huggingface.co/Menlo/Jan-nano-gguf",
            File(modelsDir, "Jan-Nano-Guff.gguf").absolutePath
        )

        val kodifyNano = ModelsData(
            id = 1,
            "Kodify-Nano-GGUF",
            "Kodify-Nano-GGUF - GGUF version of MTSAIR/Kodify-Nano, optimized for CPU/GPU inference with Ollama/llama.cpp. Lightweight LLM for code development tasks with minimal resource requirements.",
            "https://huggingface.co/MTSAIR/Kodify-Nano-GGUF/resolve/main/Kodify_Nano_q4_k_s.gguf?download=true",
            "https://huggingface.co/MTSAIR/Kodify-Nano-GGUF",
            File(modelsDir, "Kodify-Nano-GGUF.gguf").absolutePath
        )

        val llamaToolCallV2 = ModelsData(
            id = 0,
            "Llama_3.2_1B_Intruct-TC-V2",
            "A specialized fine-tuned version of the meta-llama/Llama-3.2-1B-Instruct model enhanced with function/tool calling capabilities. The model leverages the nguyenthanhthuan/function-calling-sharegpt dataset for training.",
            "https://huggingface.co/mav23/Llama_3.2_1B_Intruct_Tool_Calling_V2-GGUF/resolve/main/llama_3.2_1b_intruct_tool_calling_v2.Q2_K.gguf?download=true",
            "https://huggingface.co/mav23/Llama_3.2_1B_Intruct_Tool_Calling_V2-GGUF",
            File(modelsDir, "llama_3.2_1b_intruct_tool_calling_v2.Q2_K.gguf").absolutePath
        )

        return listOf(janNanoGuff, kodifyNano, llamaToolCallV2)
    }
}