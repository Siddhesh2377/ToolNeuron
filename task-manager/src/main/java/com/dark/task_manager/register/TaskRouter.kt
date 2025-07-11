package com.dark.task_manager.register

import android.util.Log
import com.dark.ai_manager.ai.local.Neuron
import com.dark.task_manager.data.toolRouterAIPrompt

object TaskRouter {

    val taskList = TaskRegistry.getTasks()

    // Build the task string: "Task1: Description1, Task2: Description2, ..."
    val toolListStr = taskList.joinToString(separator = ", ") { task ->
        "tool Name -> ${task.taskInfo.taskName}: tool Description -> ${task.taskInfo.description}: tool Args -> ${task.taskInfo.args}"
    }

    suspend fun processUserPrompt(userPrompt: String): String {
        // Print or log it (optional)
        Log.d("TaskDemoScreen", "Task String: $toolListStr")

        val input = buildString {
            appendLine("SYSTEM INSTRUCTION:")
            appendLine("You are a strict tool-calling AI. When It is About Search, Them You Should Provide A Prompt Query For The Tool. & Always Read System Instruction First.")
            appendLine()
            appendLine("Output format:")
            appendLine("- JSON ONLY.")
            appendLine("- No explanations, no extra text.")
            appendLine()
            appendLine("Example:")
            appendLine("""{ "tool_call": { "name": "Wiki Search", "args": { "query": "example" } } }""")
            appendLine()
            appendLine("List of Available Tools:\n")

            taskList.forEach { task ->
                appendLine("Tool: ${task.taskInfo.taskName}")
                if (task.taskInfo.taskName == "Wiki Search") appendLine("System Instruction: ${task.taskInfo.description}")
                appendLine("Args: ${task.taskInfo.args}")
                appendLine()
            }

            appendLine("User Request: $userPrompt")
        }

        Log.e("TaskDemoScreen", "Input: $input")

        Neuron.updateSystemPrompt(toolRouterAIPrompt)

        val response = Neuron.generateResponseStreaming(input) {}

        Log.d("TaskDemoScreen", "UserPrompt: $userPrompt")
        Log.d("TaskDemoScreen", "Response: $response")

        return response
    }



}