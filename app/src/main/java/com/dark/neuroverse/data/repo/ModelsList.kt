package com.dark.neuroverse.data.repo

import android.content.Context
import com.dark.neuroverse.data.model.ModelsData
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

        return listOf(janNanoGuff)
    }
}