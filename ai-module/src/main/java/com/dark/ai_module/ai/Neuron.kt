package com.dark.ai_module.ai

import android.content.Context
import android.util.Log
import io.shubham0204.smollm.SmolLM
import io.shubham0204.smollm.SmolLM.InferenceParams
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.File
import java.util.concurrent.ConcurrentHashMap

object Neuron {
    private data class Variant(val job: Job, val modelPath: File, val instance: SmolLM)

    private var activeVariant: File? = null
    private val modelInstances = ConcurrentHashMap<String, Variant>()
    private val nvScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun loadModel(
        path: File,
        context: Context,
        contextLength: Long = 8024,
        chatTemplate: String? = null,
        forceReload: Boolean = false,
        systemPrompt: String,
        onLoaded: (() -> Unit)? = null
    ) {

        require(path.exists()) { "Model file missing at: ${path.path}" }
        val modelId = path.absolutePath
        Log.d("Neuron", "Loading ${path.name}, size=${path.length()}")

        if (!forceReload && modelInstances.containsKey(modelId)) {
            activeVariant = path
            onLoaded?.invoke()
            return
        }

        unloadAllModels()
        val model = SmolLM(context)


        val job = nvScope.launch {
            runCatching {
                model.load(
                    path.path, InferenceParams(
                        contextSize = contextLength,
                        chatTemplate = chatTemplate,
                        storeChats = false,
                        numThreads = maxOf(2, Runtime.getRuntime().availableProcessors() / 2),
                        useMmap = true,
                        useMlock = false
                    )
                )
                model.addSystemPrompt(systemPrompt)
                withContext(Dispatchers.Main) { onLoaded?.invoke() }
            }.onFailure {
                Log.e("Neuron", "Model load failed", it)
                model.close()
            }
        }

        modelInstances[modelId] = Variant(job, path, model)
        activeVariant = path
    }

    suspend fun generateResponseBlocking(input: String): String {
        val model = getActiveModel()
        model.addUserMessage(input)
        val response = withContext(Dispatchers.IO) { model.getResponse(input) }
        model.addAssistantMessage(response)
        return response.trim()
    }

    suspend fun updateSystemPrompt(systemPrompt: String) {
        val model = getActiveModel()
        model.addSystemPrompt(systemPrompt)
    }

    suspend fun generateResponseStreaming(
        input: String, onTokenReceived: (String) -> Unit
    ): String {
        val model = getActiveModel()
        model.addUserMessage(input)
        val fullResponse = StringBuilder()
        model.getResponseAsFlow(input).collect { token ->
            onTokenReceived(token)
            fullResponse.append(token)
        }
        val response = fullResponse.toString().trim()
        model.addAssistantMessage(response)
        return response
    }

    fun unloadActiveModel() {
        activeVariant?.let {
            modelInstances.remove(it.absolutePath)?.instance?.close()
        }
        activeVariant = null
    }

    fun stopGeneration(immediate: Boolean = false) {
        activeVariant?.let {
            modelInstances[it.absolutePath]?.instance?.let { model ->
                if (immediate) model.stopGenerationImmediately() else model.stopGeneration()
            }
        }
    }

    fun unloadAllModels() {
        modelInstances.values.forEach { it.instance.close() }
        modelInstances.clear()
        activeVariant = null
    }

    fun listLoadedModels(): List<String> = modelInstances.keys.toList()

    private suspend fun getActiveModel(): SmolLM {
        val variant = activeVariant ?: error("No active model selected.")
        val entry = modelInstances[variant.absolutePath] ?: error("Model not loaded.")
        return entry.instance
    }

}
