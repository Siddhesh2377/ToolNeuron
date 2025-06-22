package com.dark.neuroverse.neurov.mcp.ai

import android.util.Log
import android.view.ViewGroup
import com.dark.ai_manager.ai.local.Neuron
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import org.json.JSONArray
import org.json.JSONObject

object TaskRouter {
    private lateinit var pluginDescriptions: MutableList<Pair<String, String>>


    private val job = SupervisorJob()
    private val scope = CoroutineScope(job + Dispatchers.IO)


    @OptIn(ExperimentalCoroutinesApi::class)
    suspend fun process(prompt: String, onPluginLoaded: (String) -> Unit): ViewGroup? {
        return suspendCancellableCoroutine { cont ->
            val toolCallInput = JSONObject().apply {
                put("query", prompt)
                put("plugins", JSONArray().apply {
                    pluginDescriptions.forEach { (name, desc) ->
                        put(JSONObject().apply {
                            put("name", name)
                            put("description", desc)
                        })
                    }
                })
            }.toString()

            scope.launch {
                val temp = Neuron.generateResponseStreaming(prompt)
                Log.d("PluginRouter", "AI Response: $temp")
            }
        }
    }
}