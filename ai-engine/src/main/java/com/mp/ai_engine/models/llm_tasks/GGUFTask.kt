package com.mp.ai_engine.models.llm_tasks

import androidx.room.Embedded
import kotlinx.coroutines.CompletableDeferred
import java.util.UUID

data class GGUFTask(
    val id: String = UUID.randomUUID().toString(),
    val input: String,
    val taskType: GGUFTaskType,
    val maxTokens: Int = 100,
    val toolJson: String = "",
    val events: GGUFStreamEvents,
    val result: CompletableDeferred<String>,
    val resultEmbedded: CompletableDeferred<FloatArray>
)

enum class GGUFTaskType{
    GENERATE,
    EMBEDDING
}

interface GGUFStreamEvents {
    fun onToken(token: String)
    fun onTool(toolName: String, toolArgs: String)
}
