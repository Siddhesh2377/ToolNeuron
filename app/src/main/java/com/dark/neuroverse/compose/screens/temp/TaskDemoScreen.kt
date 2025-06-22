package com.dark.neuroverse.compose.screens.temp

import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.dark.ai_manager.ai.local.Neuron
import com.dark.neuroverse.utils.taskRouterSystemPrompt
import com.dark.task_manager.register.TaskRegistry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch


@Composable
fun TaskDemoScreen(paddingValues: PaddingValues) {

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Button(onClick = {
            CoroutineScope(Dispatchers.IO).launch {
                try {

                    val taskList = TaskRegistry.getTasks()

                    // Build the task string: "Task1: Description1, Task2: Description2, ..."
                    val taskString = taskList.joinToString(separator = ", ") { task ->
                        "${task.taskInfo.taskName}: ${task.taskInfo.description}"
                    }

                    // Print or log it (optional)
                    Log.d("TaskDemoScreen", "Task String: $taskString")

                    val input = buildString {
                        appendLine(taskRouterSystemPrompt)
                        appendLine()
                        appendLine("Tasks:")
                        appendLine(taskString)
                        appendLine()
                        appendLine("User Prompt: What The Time Now ?")
                    }

                    val response = Neuron.generateResponseStreaming(input)

                    Log.d("TaskDemoScreen", "UserPrompt: What The Time Now ?")
                    Log.d("TaskDemoScreen", "Response: $response")
                } catch (e: Exception) {
                    println("Error loading model: ${e.message}")
                }
            }
        }) {
            Text("Run Task")
        }

    }
}