package com.dark.tool_neuron.model

import com.dark.tool_neuron.model.enums.ProviderType

enum class ModelFamily(val displayName: String, val priority: Int) {
    RECOMMENDED("Recommended", 0),
    LFM("LFM", 10),
    QWEN("Qwen", 20),
    GEMMA("Google Gemma", 30),
    SMOLLM("SmolLM", 40),
    PHI("Phi", 50),
    DEEPSEEK("DeepSeek", 60),
    MISTRAL("Mistral", 70),
    EMBEDDING("Embedding / RAG", 80),
    VISION("Vision", 90),
    IMAGE_GEN("Image generation", 100),
    OTHER("Other models", 200),
}

enum class ModelTask(val displayName: String) {
    CHAT("Chat"),
    INSTRUCT("Instruct"),
    THINKING("Thinking"),
    TOOL_SEARCH("Tool/Search"),
    VISION_CHAT("Vision chat"),
    EMBEDDING("Embedding"),
    TTS("Text-to-speech"),
    STT("Speech-to-text"),
    IMAGE_GEN("Image generation"),
    UPSCALER("Upscaler"),
    OTHER("Other"),
}

object ModelTaxonomy {
    fun family(model: HuggingFaceModel): ModelFamily {
        val haystack = listOf(model.id, model.name, model.repoId, model.repoPath, model.modelType)
            .plus(model.tags)
            .joinToString(" ")
            .lowercase()

        return when {
            model.modelType == "image_gen" -> ModelFamily.IMAGE_GEN
            model.modelType == "image_upscaler" || model.modelType == "tts" || model.modelType == "stt" -> ModelFamily.OTHER
            model.modelType == "embedding" -> ModelFamily.EMBEDDING
            model.isVlm || "vlm" in haystack || "vision" in haystack -> ModelFamily.VISION
            "lfm" in haystack -> ModelFamily.LFM
            "qwen" in haystack -> ModelFamily.QWEN
            "gemma" in haystack -> ModelFamily.GEMMA
            "smollm" in haystack || "smallm" in haystack -> ModelFamily.SMOLLM
            "phi" in haystack -> ModelFamily.PHI
            "deepseek" in haystack -> ModelFamily.DEEPSEEK
            "mistral" in haystack -> ModelFamily.MISTRAL
            else -> ModelFamily.OTHER
        }
    }

    fun family(model: ModelInfo): ModelFamily {
        val haystack = listOf(model.id, model.name, model.providerType.name).joinToString(" ").lowercase()
        return when {
            model.providerType == ProviderType.IMAGE_GEN -> ModelFamily.IMAGE_GEN
            model.providerType in setOf(ProviderType.TTS, ProviderType.STT, ProviderType.IMAGE_UPSCALER) -> ModelFamily.OTHER
            model.providerType == ProviderType.EMBEDDING -> ModelFamily.EMBEDDING
            model.providerType == ProviderType.VISION_CHAT || "vl" in haystack || "vision" in haystack -> ModelFamily.VISION
            "lfm" in haystack -> ModelFamily.LFM
            "qwen" in haystack -> ModelFamily.QWEN
            "gemma" in haystack -> ModelFamily.GEMMA
            "smollm" in haystack || "smallm" in haystack -> ModelFamily.SMOLLM
            "phi" in haystack -> ModelFamily.PHI
            "deepseek" in haystack -> ModelFamily.DEEPSEEK
            "mistral" in haystack -> ModelFamily.MISTRAL
            else -> ModelFamily.OTHER
        }
    }

    fun task(model: HuggingFaceModel): ModelTask {
        val haystack = listOf(model.id, model.name, model.repoId, model.repoPath, model.modelType)
            .plus(model.tags)
            .joinToString(" ")
            .lowercase()
        return when {
            model.modelType == "image_upscaler" -> ModelTask.UPSCALER
            model.modelType == "image_gen" -> ModelTask.IMAGE_GEN
            model.modelType == "tts" -> ModelTask.TTS
            model.modelType == "stt" -> ModelTask.STT
            model.modelType == "embedding" -> ModelTask.EMBEDDING
            model.isVlm -> ModelTask.VISION_CHAT
            "thinking" in haystack || "reasoning" in haystack || "deepseek-r1" in haystack -> ModelTask.THINKING
            "tool" in haystack || "function" in haystack -> ModelTask.TOOL_SEARCH
            "instruct" in haystack || "it" in haystack -> ModelTask.INSTRUCT
            model.modelType == "gguf" -> ModelTask.CHAT
            else -> ModelTask.OTHER
        }
    }

    fun task(model: ModelInfo): ModelTask = when (model.providerType) {
        ProviderType.GGUF -> ModelTask.CHAT
        ProviderType.VISION_CHAT -> ModelTask.VISION_CHAT
        ProviderType.TOOL_SEARCH -> ModelTask.TOOL_SEARCH
        ProviderType.TTS -> ModelTask.TTS
        ProviderType.STT -> ModelTask.STT
        ProviderType.EMBEDDING -> ModelTask.EMBEDDING
        ProviderType.IMAGE_GEN -> ModelTask.IMAGE_GEN
        ProviderType.IMAGE_UPSCALER -> ModelTask.UPSCALER
    }

    fun groupKey(model: HuggingFaceModel): String = family(model).name
}
