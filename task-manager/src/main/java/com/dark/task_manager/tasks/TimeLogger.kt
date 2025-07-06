package com.dark.task_manager.tasks

import android.content.Context
import android.util.Log
import com.dark.task_manager.api.TaskApi
import com.dark.task_manager.model.TaskInfo
import com.dark.task_manager.model.TaskType
import java.text.SimpleDateFormat
import java.util.*

class TimeLogger(context: Context) : TaskApi(context) {

    override fun getTaskInfo(): TaskInfo {
        return TaskInfo(
            taskName = "Time Logger",
            description = "This Task helps in telling the current time",
            args = """
                {"tool_call":{"name":"tell_time","args":{}}}
            """.trimIndent(),
            taskType = TaskType.FOREGROUND
        )
    }

    override fun onStart(any: Any) {
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
