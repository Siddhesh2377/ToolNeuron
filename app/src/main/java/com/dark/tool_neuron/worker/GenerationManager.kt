package com.dark.tool_neuron.worker

import com.dark.tool_neuron.engine.GenerationEvent
import com.dark.tool_neuron.models.messages.Messages
import com.dark.tool_neuron.models.messages.Role
import kotlinx.coroutines.flow.Flow

class GenerationManager {

    fun isModelLoaded(): Boolean {
        return LlmModelWorker.isModelLoaded.value
    }

    fun generateStreaming(prompt: String, maxTokens: Int = 512): Flow<GenerationEvent> {
        return LlmModelWorker.ggufGenerateStreaming(prompt, maxTokens)
    }

    fun stopGeneration() {
        LlmModelWorker.ggufStopGeneration()
    }

    fun buildPromptFromHistory(messages: List<Messages>): String {
        val promptBuilder = StringBuilder()

        messages.forEach { message ->
            when (message.role) {
                Role.User -> {
                    promptBuilder.append("User: ${message.content.content}\n")
                }

                Role.Assistant -> {
                    promptBuilder.append("Assistant: ${message.content.content}\n")
                }
            }
        }

        return promptBuilder.toString()
    }

    fun buildSinglePrompt(userMessage: String): String {
        return "User: $userMessage\nAssistant:"
    }

    fun buildConversationPrompt(history: List<Messages>, currentPrompt: String): String {
        val builder = StringBuilder()

        history.forEach { message ->
            when (message.role) {
                Role.User -> {
                    builder.append("User: ${message.content.content}\n")
                }

                Role.Assistant -> {
                    builder.append("Assistant: ${message.content.content}\n")
                }
            }
        }

        builder.append("User: $currentPrompt\nAssistant:")

        return builder.toString()
    }
}