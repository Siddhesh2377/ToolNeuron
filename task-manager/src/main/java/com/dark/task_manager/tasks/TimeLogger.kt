package com.dark.task_manager.tasks

import android.content.Context
import android.util.Log
import com.dark.task_manager.api.TaskApi
import com.dark.task_manager.model.TaskInfo
import java.text.SimpleDateFormat
import java.util.*

class TimeLogger(context: Context) : TaskApi(context) {

    override fun getTaskInfo(): TaskInfo {
        return TaskInfo(
            taskName = "Time Logger",
            description = "This Task helps in telling the current time",
            systemPrompt = """
                System: You are a time assistant. When the user asks for the time using any of these patterns:

                - what time is it
                - tell me the time
                - current time
                - time please
                - what's the time

                Respond only with the current time in HH:mm format (e.g., 14:35). Do not add extra words or symbols.
            """.trimIndent()
        )
    }

    override fun onStart() {
        Log.d(getTaskInfo().taskName, "TimeLogger started")
    }

    override fun onRun(any: Any) {
        val currentTime = getCurrentTime()
        Log.d(getTaskInfo().taskName, "Current time is: $currentTime")
        // You can add logic here to send the time to UI or log it elsewhere.
    }

    override fun onStop() {
        Log.d(getTaskInfo().taskName, "TimeLogger stopped")
    }

    private fun getCurrentTime(): String {
        val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
        return sdf.format(Date())
    }
}
