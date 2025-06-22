package com.dark.task_manager.register

import android.content.Context
import com.dark.task_manager.api.TaskApi
import com.dark.task_manager.model.TaskListModel
import com.dark.task_manager.tasks.TimeLogger
import com.dark.task_manager.tasks.application_operator.ApplicationOperator
import kotlinx.coroutines.*

object TaskRegistry {

    private val tasks = mutableListOf<TaskListModel>()
    private val jobMap = mutableMapOf<String, Job>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    fun init(context: Context) {
        registerTask(ApplicationOperator(context), TimeLogger(context))
    }

    fun registerTask(vararg taskApis: TaskApi) {
        taskApis.forEach { taskApi ->
            val taskInfo = taskApi.getTaskInfo()
            if (tasks.none { it.taskInfo.taskName == taskInfo.taskName }) {
                tasks += TaskListModel(taskInfo, taskApi)
            }
        }
    }

    fun startTask(taskName: String) {
        val taskModel = tasks.find { it.taskInfo.taskName == taskName }
        if (taskModel == null) {
            println("Task '$taskName' not found.")
            return
        }

        if (jobMap.containsKey(taskName) && jobMap[taskName]?.isActive == true) {
            println("Task '$taskName' is already running.")
            return
        }

        val job = scope.launch {
            taskModel.taskApi.onRun("")
        }
        jobMap[taskName] = job
    }

    fun stopTask(taskName: String) {
        jobMap[taskName]?.let { job ->
            if (job.isActive) {
                job.cancel()
                println("Task '$taskName' stopped.")
            }
            jobMap.remove(taskName)
        } ?: println("Task '$taskName' is not running or already stopped.")

        // Optionally call onStop logic from TaskApi
        tasks.find { it.taskInfo.taskName == taskName }?.taskApi?.onStop()
    }

    fun getTasks(): List<TaskListModel> = tasks.toList()

    fun unregisterTask(taskApi: TaskApi) {
        val taskName = taskApi.getTaskInfo().taskName
        stopTask(taskName)
        tasks.removeAll { it.taskApi == taskApi }
    }

    fun stopAllTasks() {
        jobMap.keys.toList().forEach { stopTask(it) }
    }
}
