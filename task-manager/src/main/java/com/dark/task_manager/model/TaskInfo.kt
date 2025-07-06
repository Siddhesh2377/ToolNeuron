package com.dark.task_manager.model

data class TaskInfo(
    val taskName: String,
    val description: String,
    val args: String,
    val taskType: TaskType
)
