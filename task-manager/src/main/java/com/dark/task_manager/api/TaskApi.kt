package com.dark.task_manager.api

import android.content.Context
import com.dark.task_manager.model.TaskInfo

open class TaskApi(internal val context: Context) {
    open fun getTaskInfo(): TaskInfo{
        return TaskInfo(
            taskName = "Task Name",
            description = "Task Description",
            systemPrompt = "System Prompt"
        )
    }
    open fun onStart(){}
    open fun onRun(any: Any) {}
    open fun onStop(){}
}