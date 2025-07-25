package com.dark.plugins.worker

import android.content.Context
import com.dark.plugins.repo.PluginRegistry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.launch

object PluginManager {

    private val pluginScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val pluginJobs: MutableList<Job> = mutableListOf()

    fun runPlugin(pluginName: String, data: Any) {
        val plugin = PluginRegistry.getPlugin(pluginName)
        plugin?.let {
            val job = pluginScope.launch {
                try {
                    it.onCreate(data)
                    it.onDestroy()
                } catch (e: Exception) {
                    // Handle/log exception
                }
            }
            pluginJobs += job
        }
    }

    fun cancelAll() {
        pluginScope.coroutineContext.cancelChildren()
        pluginJobs.clear()
    }
}
