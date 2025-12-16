package com.mp.ai_engine.models.llm_tasks

import kotlinx.coroutines.CompletableDeferred

data class GGUFTask(
    val id: String,
    val input: String,
    val maxTokens: Int,
    val events: GGUFStreamEvents,
    val result: CompletableDeferred<String>
)

interface GGUFStreamEvents {
    fun onToken(token: String)
    fun onTool(toolName: String, toolArgs: String)
}
