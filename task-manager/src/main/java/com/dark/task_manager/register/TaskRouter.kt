package com.dark.task_manager.register

import android.util.Log
import com.dark.ai_manager.ai.local.Neuron
import com.dark.task_manager.data.toolRouterAIPrompt
import com.dark.task_manager.data.toolsDefinition

object TaskRouter {

    val taskList = TaskRegistry.getTasks()

    // Build the task string: "Task1: Description1, Task2: Description2, ..."
    val toolListStr = taskList.joinToString(separator = ", ") { task ->
        "${task.taskInfo.taskName}: ${task.taskInfo.description}"
    }

    suspend fun processUserPrompt(userPrompt: String): String {
        // Print or log it (optional)
        Log.d("TaskDemoScreen", "Task String: $toolListStr")

        val input = buildString {
            appendLine(toolRouterAIPrompt)
            appendLine()
            appendLine("TOOLS LIST:")
            appendLine(toolListStr)
            appendLine()
            appendLine("USER PROMPT: $userPrompt")
        }

        Neuron.updateSystemPrompt(toolRouterAIPrompt)

        val response = Neuron.generateResponseStreaming(input) {}

        Log.d("TaskDemoScreen", "UserPrompt: $userPrompt")
        Log.d("TaskDemoScreen", "Response: $response")

        return response
    }



}