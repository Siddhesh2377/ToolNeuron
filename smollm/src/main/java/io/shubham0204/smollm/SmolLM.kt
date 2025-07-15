package io.shubham0204.smollm

import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
import io.shubham0204.smollm.workers.JNIWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import java.io.File

class SmolLM(private val context: Context) {
    private val logTag = SmolLM::class.java.simpleName
    private var libraryLoaded = false
    private var nativePtr = 0L
    private lateinit var fileName: String

    @SuppressLint("UnsafeDynamicallyLoadedCode")
    suspend fun init() {
        val nativeJniPath = File(context.filesDir, "jniLibs").apply { mkdirs() }
        fileName = JNIWorker.getCompatibleJniLibName()
        val soFile = File(nativeJniPath, "lib$fileName.so")

        if (!soFile.exists()) {
            Log.d(logTag, "Downloading native lib: $fileName")
            withContext(Dispatchers.IO) {
                JNIWorker.downloadLib(context) {
                    Log.d(logTag, "Downloaded lib: $fileName")
                }
            }
        }

        Log.d(logTag, "Loading lib from: ${soFile.absolutePath}")
        System.load(soFile.absolutePath)
        libraryLoaded = true
    }

    suspend fun load(modelPath: String, params: InferenceParams = InferenceParams()) =
        withContext(Dispatchers.IO) {
            if (!libraryLoaded) init()
            val ctxSize = params.contextSize ?: DefaultInferenceParams.contextSize
            val template = params.chatTemplate ?: DefaultInferenceParams.chatTemplate

            try {
                nativePtr = loadModel(
                    modelPath,
                    params.minP,
                    params.temperature,
                    params.storeChats,
                    ctxSize,
                    template,
                    params.numThreads,
                    params.useMmap,
                    params.useMlock
                )
            } catch (e: IllegalStateException) {
                Log.e(logTag, "Model load failed: ${e.message}", e)
            }
        }

    object DefaultInferenceParams {
        const val contextSize: Long = 1024L
        val chatTemplate: String = """
            {% for message in messages %}
                {% if loop.first and messages[0]['role'] != 'system' %}
                    {{ '<|im_start|>system Your Name is Neuron Developed By NeuroV. Your only job is to send short, polite, relevant auto-replies when the user is unavailable. Never overthink, reason, or generate long responses. Keep replies under 10 words.<|im_end|> ' }}
                {% endif %}
                {{ '<|im_start|>' + message['role'] + ' ' + message['content'] + '<|im_end|> ' }}
            {% endfor %}
            {% if add_generation_prompt %}{{ '<|im_start|>assistant ' }}{% endif %}
        """.trimIndent()
    }

    data class InferenceParams(
        val minP: Float = 0.01f,
        val temperature: Float = 1.0f,
        val storeChats: Boolean = false,
        val contextSize: Long? = null,
        val chatTemplate: String? = null,
        val numThreads: Int = 4,
        val useMmap: Boolean = true,
        val useMlock: Boolean = false,
    )

    private fun verifyHandle() = require(nativePtr != 0L) { "Model not loaded. Call load() first." }

    fun addUserMessage(message: String) = addChatMessageSafe(message, "user")
    fun addSystemPrompt(prompt: String) = addChatMessageSafe(prompt, "system")
    fun addAssistantMessage(message: String) = addChatMessageSafe(message, "assistant")

    private fun addChatMessageSafe(message: String, role: String) {
        verifyHandle()
        addChatMessage(nativePtr, message, role)
    }

    fun getResponse(query: String): String {
        verifyHandle()
        startCompletion(nativePtr, query)
        val response = buildString {
            while (true) {
                val piece = completionLoop(nativePtr)
                if (piece == "[EOG]") break
                append(piece)
            }
        }
        stopCompletion(nativePtr)
        return response
    }

    fun getResponseAsFlow(query: String): Flow<String> = flow {
        verifyHandle()
        startCompletion(nativePtr, query)
        while (true) {
            val piece = completionLoop(nativePtr)
            if (piece == "[EOG]") break
            emit(piece)
        }
        stopCompletion(nativePtr)
    }

    fun stopGeneration() = stopCompletion(nativePtr)
    fun stopGenerationImmediately() {
        verifyHandle()
        stopGenerationImmediately(nativePtr)
    }

    fun getResponseGenerationSpeed(): Float {
        verifyHandle()
        return getResponseGenerationSpeed(nativePtr)
    }

    fun getContextLengthUsed(): Int {
        verifyHandle()
        return getContextSizeUsed(nativePtr)
    }

    fun close() {
        if (nativePtr != 0L) {
            close(nativePtr)
            nativePtr = 0L
        }
    }

    // Native methods
    private external fun loadModel(
        modelPath: String,
        minP: Float,
        temperature: Float,
        storeChats: Boolean,
        contextSize: Long,
        chatTemplate: String,
        nThreads: Int,
        useMmap: Boolean,
        useMlock: Boolean
    ): Long

    private external fun addChatMessage(modelPtr: Long, message: String, role: String)
    private external fun getResponseGenerationSpeed(modelPtr: Long): Float
    private external fun getContextSizeUsed(modelPtr: Long): Int
    private external fun close(modelPtr: Long)
    private external fun startCompletion(modelPtr: Long, prompt: String)
    private external fun completionLoop(modelPtr: Long): String
    private external fun stopCompletion(modelPtr: Long)
    private external fun stopGenerationImmediately(modelPtr: Long)
    private external fun clearChatMemory(modelPtr: Long)
}

