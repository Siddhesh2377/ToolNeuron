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
            appendLine("You are a strict tool-calling AI. Always Read System Instruction First.")
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
                if (task.taskInfo.taskName != "Wiki Search"){
                    appendLine("Tool: ${task.taskInfo.taskName}")
                    appendLine("Args: ${task.taskInfo.args}")
                    appendLine()
                }
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

    suspend fun processSearchRequest(userPrompt: String): String {
        val systemPrompt = """
You are an AI that rewrites casual user prompts into strict Wikipedia-style search questions.

📌 Always return a **single line question** in this format:  
"What is <topic>?"

🛑 Do NOT:
- Add quotes
- Ask vague or open-ended questions like "How does..." or "Why..."
- Add meta text like "search" or "look up"
- Output anything other than a one-line question

Examples:
- Input: "Tell me about machine learning" → "What is machine learning?"
- Input: "Search Online For What is Brain?" → "What is the brain?"
- Input: "Info about hydrogen bomb" → "What is a hydrogen bomb?"

Only return the final question. No quotes. No prefix. Just the line.

""".trimIndent()



        val input = buildString {
            appendLine("SYSTEM INSTRUCTION:")
            appendLine(systemPrompt)
            appendLine()
            appendLine("User Prompt: $userPrompt")
        }

        Neuron.updateSystemPrompt(systemPrompt)

        val response = Neuron.generateResponseStreaming(input) {}

        Log.d("TaskDemoScreen", "UserPrompt: $userPrompt")
        Log.d("TaskDemoScreen", "Response: $response")

        return response.trim()
    }

}