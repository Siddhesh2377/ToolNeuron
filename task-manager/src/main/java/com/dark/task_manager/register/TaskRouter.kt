package com.dark.task_manager.register

import android.util.Log
import com.dark.ai_manager.ai.local.Neuron
import com.dark.task_manager.data.taskRouterSystemPrompt
import com.dark.task_manager.data.toolsDefinition

object TaskRouter {

    val taskList = TaskRegistry.getTasks()

    // Build the task string: "Task1: Description1, Task2: Description2, ..."
    val taskString = taskList.joinToString(separator = ", ") { task ->
        "${task.taskInfo.taskName}: ${task.taskInfo.description}"
    }

    suspend fun processUserPrompt(userPrompt: String): String {
        // Print or log it (optional)
        Log.d("TaskDemoScreen", "Task String: $taskString")

        val input = buildString {
            appendLine(toolsDefinition)
            appendLine()
            appendLine("TASK LIST:")
            appendLine(taskString)
            appendLine()
            appendLine("USER PROMPT: $userPrompt")
        }

        Neuron.updateSystemPrompt(toolsDefinition)

        val response = Neuron.generateResponseStreaming(input) {}

        Log.d("TaskDemoScreen", "UserPrompt: $userPrompt")
        Log.d("TaskDemoScreen", "Response: $response")

        return response
    }



}