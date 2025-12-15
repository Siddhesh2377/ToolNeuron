package com.mp.ai_engine.models.llm_tasks

data class GGUFTask(
    val id: String = "", val input: String = "", val maxTokens: Int = 0
)

interface GGUFInferenceEvent {
    suspend fun onToken(token: String)
    suspend fun onTool(toolName: String, toolArgs: String)
    suspend fun onComplete(result: Any) {}
    suspend fun onError(error: Throwable) {}
}
