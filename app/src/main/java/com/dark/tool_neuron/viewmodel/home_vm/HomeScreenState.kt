package com.dark.tool_neuron.viewmodel.home_vm

sealed interface ModelLoadState {
    data object Idle : ModelLoadState
    data class Loading(val modelId: String) : ModelLoadState
    data class Active(val modelId: String) : ModelLoadState
    data class Error(val modelId: String, val message: String) : ModelLoadState
}

enum class PillState(val label: String) {
    Idle("No Model Loaded"),
    Loading("Loading Model"),
    Loaded("Model Loaded"),
    Generating("Generating Reply"),
    Thinking("Deep Thinking"),
    ToolCalling("Tool Calling"),
    Image("Image Mode"),
    Rag("RAG Search"),
    Error("Model Error"),
}

data class StreamingFragment(
    val chatId: String,
    val content: String,
    val thinkingContent: String,
)

sealed interface GenerationStatus {
    data object Hidden : GenerationStatus
    data object Welcome : GenerationStatus
    data object NoModelLoaded : GenerationStatus

    data class ModelLoading(val modelName: String) : GenerationStatus

    data class GeneratingText(
        val modelName: String,
        val wordCount: Int,
        val contextUsage: Float,
    ) : GenerationStatus

    data class Thinking(
        val modelName: String,
        val wordCount: Int,
        val contextUsage: Float,
    ) : GenerationStatus

    data class ExecutingTool(
        val toolName: String,
        val pluginName: String,
    ) : GenerationStatus

    data class ToolComplete(
        val toolName: String,
        val success: Boolean,
        val elapsedMs: Long,
        val errorMessage: String? = null,
    ) : GenerationStatus

    data class Error(
        val message: String,
        val modelName: String? = null,
    ) : GenerationStatus
}
