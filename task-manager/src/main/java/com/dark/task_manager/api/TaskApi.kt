package com.dark.task_manager.api

import android.content.Context
import com.dark.task_manager.model.TaskInfo
import com.dark.task_manager.model.TaskType

open class TaskApi(internal val context: Context) {
    open fun getTaskInfo(): TaskInfo{
        return TaskInfo(
            taskName = "Task Name",
            description = "Task Description",
            args = "System Prompt",
            taskType = TaskType.NONE
        )
    }
    open fun onStart(any: Any) {}
    open fun onRun(any: Any) {}
    open fun onStop(){}
}