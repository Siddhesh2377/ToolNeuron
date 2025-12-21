package com.mp.ai_engine.models.llm_tasks

import kotlinx.coroutines.CompletableDeferred

data class OpenRouterTask(
    val id: String,
    val input: String,
    val maxTokens: Int,
    val events: OPStreamEvents,
    val result: CompletableDeferred<String>
)

interface OPStreamEvents {
    fun onToken(token: String)
    fun onTool(toolName: String, toolArgs: String)
}
