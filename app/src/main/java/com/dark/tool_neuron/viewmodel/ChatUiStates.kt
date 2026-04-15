package com.dark.tool_neuron.viewmodel

import android.graphics.Bitmap
import com.dark.tool_neuron.models.ModelType
import com.dark.tool_neuron.models.messages.ToolChainStepData

// ════════════════════════════════════════════
//  CHAT UI STATE GROUPS
//  Grouped by update frequency for optimal recomposition.
// ════════════════════════════════════════════

// ── HOT — updates per token (~50ms during generation) ──

data class StreamingState(
    val userMessage: String? = null,
    val assistantMessage: String = "",
    val image: Bitmap? = null,
    val imageProgress: Float = 0f,
    val imageStep: String = ""
)

// ── WARM — updates per user action / phase change ──

data class ChatUiState(
    val isGenerating: Boolean = false,
    val currentChatId: String? = null,
    val error: String? = null,
    val generationType: ModelType = ModelType.TEXT_GENERATION,
    val thinkingEnabled: Boolean = false,
    val modelSupportsThinking: Boolean = false
)

data class AgentState(
    val phase: AgentPhase = AgentPhase.Idle,
    val plan: String? = null,
    val summary: String? = null,
    val toolChainSteps: List<ToolChainStepData> = emptyList(),
    val currentRound: Int = 0
)

data class RagState(
    val context: String? = null,
    val results: List<RagQueryDisplayResult> = emptyList()
)

// ── COLD — updates rarely / per session ──

data class ChatConfigState(
    val streamingEnabled: Boolean = true,
    val chatMemoryEnabled: Boolean = true,
    val showDynamicWindow: Boolean = false,
    val showModelList: Boolean = false
)
