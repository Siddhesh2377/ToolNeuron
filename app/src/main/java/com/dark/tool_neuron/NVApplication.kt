package com.dark.tool_neuron

import android.app.Application
import android.os.Build
import com.dark.ai_module.workers.AudioManager
import com.dark.ai_module.workers.ModelManager
import com.dark.tool_neuron.logger.AppLogger
import com.dark.tool_neuron.userdata.ntds.neuron_tree.NeuronNode
import com.dark.tool_neuron.util.initOpenRouterFromPrefs
import com.dark.tool_neuron.worker.ChatManager
import com.dark.tool_neuron.worker.UserDataManager
import com.dark.plugins.manager.PluginManager
import com.dark.tool_neuron.worker.DataHubManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlin.system.measureTimeMillis

class NVApplication : Application() {
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val TAG = "NVApplication"

    override fun onCreate() {
        super.onCreate()

        appScope.launch {
            // Get root node first
            UserDataManager.init(applicationContext)
            val root = UserDataManager.getRootNode()

            // Start logging session
            AppLogger.startSession(root, "App Startup")

            logAppInfo(root)
            initializeManagers(root)

            // Save logs to brain file
            UserDataManager.performTreeSave(applicationContext)
        }
    }

    private fun logAppInfo(root: NeuronNode) {
        AppLogger.info(
            root = root,
            message = "Application starting",
            details = mapOf(
                "version" to BuildConfig.VERSION_NAME,
                "versionCode" to BuildConfig.VERSION_CODE,
                "device" to Build.MODEL,
                "android" to Build.VERSION.SDK_INT
            )
        )

        val runtime = Runtime.getRuntime()
        AppLogger.info(
            root = root,
            message = "System resources",
            details = mapOf(
                "maxMemory" to "${runtime.maxMemory() / (1024 * 1024)}MB",
                "processors" to runtime.availableProcessors()
            )
        )
    }

    private suspend fun initializeManagers(root: NeuronNode) {
        // ChatManager
        measureAndLog(root, "ChatManager") {
            ChatManager.refreshChats()
        }

        // ModelManager
        measureAndLog(root, "ModelManager") {
            ModelManager.init(applicationContext)
        }

        // AudioManager
        measureAndLog(root, "AudioManager") {
            AudioManager.init(applicationContext)
        }

        // PluginManager
        measureAndLog(root, "PluginManager") {
            PluginManager.init(applicationContext)
        }

        // DataHubManager
        measureAndLog(root, "DataHubManager") {
            DataHubManager.init(applicationContext)
        }

        // OpenRouter
        measureAndLog(root, "OpenRouter") {
            initOpenRouterFromPrefs(applicationContext)
        }

        AppLogger.info(root, "All managers initialized successfully")
    }

    private inline fun measureAndLog(
        root: NeuronNode,
        name: String,
        block: () -> Unit
    ) {
        try {
            val duration = measureTimeMillis {
                block()
            }

            AppLogger.info(
                root = root,
                message = "$name initialized",
                details = mapOf("duration" to "${duration}ms")
            )
        } catch (e: Exception) {
            AppLogger.error(
                root = root,
                message = "$name initialization failed",
                details = mapOf(
                    "error" to e.message.toString(),
                    "type" to e.javaClass.simpleName
                )
            )
        }
    }

    override fun onLowMemory() {
        super.onLowMemory()

        appScope.launch {
            val root = UserDataManager.getRootNode()
            val runtime = Runtime.getRuntime()

            AppLogger.warn(
                root = root,
                message = "Low memory warning",
                details = mapOf(
                    "freeMemory" to "${runtime.freeMemory() / (1024 * 1024)}MB",
                    "maxMemory" to "${runtime.maxMemory() / (1024 * 1024)}MB"
                )
            )

            UserDataManager.performTreeSave(applicationContext)
        }
    }

    override fun onTerminate() {
        super.onTerminate()
        val root = UserDataManager.getRootNode()
        AppLogger.endSession(root)
    }
}