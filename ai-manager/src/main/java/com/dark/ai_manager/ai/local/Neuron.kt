package com.dark.ai_manager.ai.local

import android.util.Log
import io.shubham0204.smollm.SmolLM
import io.shubham0204.smollm.SmolLM.InferenceParams
import kotlinx.coroutines.*
import java.io.File
import java.util.concurrent.ConcurrentHashMap

object Neuron {

    data class Variant(
        val job: Job,
        val modelPath: File,
        val instance: SmolLM
    )

    private var activeVariant: File? = null
    private val modelInstances = ConcurrentHashMap<String, Variant>()
    private val nvScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun loadModel(
        path: File,
        contextLength: Long = 8024,
        chatTemplate: String? = null,
        forceReload: Boolean = false,
        systemPrompt: String,
        onLoaded: (() -> Unit)? = null
    ) {
        val file = File("/storage/emulated/0/Download/Models/llama_3.2_1b_intruct_tool_calling_v2.Q2_K.gguf")
        require(file.exists()) { "Model file missing at: ${path.path}" }
        Log.d("Neuron", "Loading ${file.name}, size=${file.length()}")

        if (!forceReload && modelInstances.containsKey(path.name)) {
            activeVariant = path
            onLoaded?.invoke()
            return
        }

        unloadActiveModel()
        val model = SmolLM()

        val job = nvScope.launch {
            runCatching {
                model.load(
                    path.path,
                    InferenceParams(
                        contextSize = contextLength,
                        chatTemplate = chatTemplate,
                        storeChats = false,
                        numThreads = 6,
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

        modelInstances[path.name] = Variant(job, path, model)
        activeVariant = path
    }

    suspend fun generateResponseBlocking(input: String): String {
        val model = getActiveModel()
        model.addUserMessage(input)

        val response = withContext(Dispatchers.IO) { model.getResponse(input) }
        model.addAssistantMessage(response)
        return response.trim()
    }

    suspend fun updateSystemPrompt(systemPrompt: String){
        val model = getActiveModel()
        model.addSystemPrompt(systemPrompt)
    }

    suspend fun generateResponseStreaming(input: String, onTokenReceived: (String) -> Unit): String {
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
            modelInstances.remove(it.name)?.instance?.close()
        }
        activeVariant = null
    }

    fun stopGeneration(immediate: Boolean = false) {
        activeVariant?.let {
            modelInstances[it.name]?.instance?.let { model ->
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
        val entry = modelInstances[variant.name] ?: error("Model not loaded.")
        entry.job.join()
        return entry.instance
    }
}
