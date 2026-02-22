package com.dark.tool_neuron.worker

import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.IBinder
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.dark.tool_neuron.engine.GenerationEvent
import com.dark.tool_neuron.models.table_schema.Model
import com.dark.tool_neuron.models.table_schema.ModelConfig
import com.dark.tool_neuron.service.IDiffusionGenerationCallback
import com.dark.tool_neuron.service.IGgufGenerationCallback
import com.dark.tool_neuron.service.ILLMService
import com.dark.tool_neuron.service.IModelLoadCallback
import com.dark.tool_neuron.service.LLMService
import com.mp.ai_gguf.models.DecodingMetrics
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import java.util.Base64
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

@SuppressLint("StaticFieldLeak")
object LlmModelWorker {

    private const val TAG = "LlmModelWorker"

    private var service: ILLMService? = null
    private var boundContext: Context? = null
    private val serviceBound = CompletableDeferred<Unit>()
    private var isBinding = false

    // GGUF state
    private val _isGgufModelLoaded = MutableStateFlow(false)
    val isGgufModelLoaded: StateFlow<Boolean> = _isGgufModelLoaded.asStateFlow()

    // Diffusion state
    private val _isDiffusionModelLoaded = MutableStateFlow(false)
    val isDiffusionModelLoaded: StateFlow<Boolean> = _isDiffusionModelLoaded.asStateFlow()

    private val _diffusionBackendState = MutableStateFlow("Idle")
    val diffusionBackendState: StateFlow<String> = _diffusionBackendState.asStateFlow()

    val _currentGgufModelId = MutableStateFlow<String?>(null)
    val currentGgufModelId: StateFlow<String?> = _currentGgufModelId.asStateFlow()

    val _currentDiffusionModelId = MutableStateFlow<String?>(null)
    val currentDiffusionModelId: StateFlow<String?> = _currentDiffusionModelId.asStateFlow()


    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            service = ILLMService.Stub.asInterface(binder)
            isBinding = false
            serviceBound.complete(Unit)
            Log.i(TAG, "Service connected")
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            service = null
            isBinding = false
            Log.w(TAG, "Service disconnected unexpectedly")
        }

        override fun onBindingDied(name: ComponentName?) {
            service = null
            isBinding = false
            Log.e(TAG, "Service binding died")
        }

        override fun onNullBinding(name: ComponentName?) {
            isBinding = false
            Log.e(TAG, "Service returned null binding")
        }
    }

    fun bindService(context: Context) {
        if (boundContext != null && service != null) {
            Log.w(TAG, "Service already bound and connected")
            return
        }

        if (isBinding) {
            Log.w(TAG, "Service binding already in progress")
            return
        }

        isBinding = true
        val appContext = context.applicationContext
        val intent = Intent(appContext, LLMService::class.java)

        val bound = appContext.bindService(
            intent,
            connection,
            Context.BIND_AUTO_CREATE or Context.BIND_IMPORTANT
        )

        if (bound) {
            boundContext = appContext
            Log.i(TAG, "Service binding initiated")
        } else {
            isBinding = false
            Log.e(TAG, "Failed to bind service")
        }
    }

    fun unbindService() {
        try {
            boundContext?.unbindService(connection)
            Log.i(TAG, "Service unbound")
        } catch (e: Exception) {
            Log.e(TAG, "Error unbinding service", e)
        } finally {
            service = null
            boundContext = null
            isBinding = false
            _isGgufModelLoaded.value = false
            _isDiffusionModelLoaded.value = false
        }
    }

    private suspend fun ensureServiceBound(): ILLMService {
        // Increased timeout for slow devices
        return withTimeout(10000) {
            serviceBound.await()
            service ?: throw IllegalStateException("Service not available")
        }
    }

    // ==================== GGUF Methods ====================

    suspend fun loadGgufModel(model: Model, modelConfig: ModelConfig): Boolean {
        val svc = ensureServiceBound()

        return suspendCancellableCoroutine { continuation ->
            val callback = object : IModelLoadCallback.Stub() {
                override fun onSuccess() {
                    _isGgufModelLoaded.value = true
                    Log.i(TAG, "GGUF model loaded successfully")
                    continuation.resume(true)
                }

                override fun onError(message: String) {
                    _isGgufModelLoaded.value = false
                    Log.e(TAG, "Failed to load GGUF model: $message")
                    continuation.resume(false)
                }
            }

            try {
                svc.loadGgufModel(
                    model.modelPath,
                    model.modelName,
                    modelConfig.modelLoadingParams ?: "",
                    modelConfig.modelInferenceParams ?: "",
                    callback
                )
            } catch (e: Exception) {
                Log.e(TAG, "Exception loading GGUF model", e)
                continuation.resumeWithException(e)
            }
        }
    }

    /**
     * Load GGUF model from a content:// URI using file descriptor
     * This is used for SAF (Storage Access Framework) URIs
     */
    suspend fun loadGgufModelFromUri(
        context: Context,
        uri: Uri,
        modelName: String,
        modelConfig: ModelConfig
    ): Boolean {
        val svc = ensureServiceBound()

        // Open ParcelFileDescriptor from content URI
        val pfd = context.contentResolver.openFileDescriptor(uri, "r")
            ?: throw IllegalArgumentException("Cannot open file descriptor for URI: $uri")

        return suspendCancellableCoroutine { continuation ->
            val callback = object : IModelLoadCallback.Stub() {
                override fun onSuccess() {
                    _isGgufModelLoaded.value = true
                    Log.i(TAG, "GGUF model loaded successfully from URI")
                    continuation.resume(true)
                }

                override fun onError(message: String) {
                    _isGgufModelLoaded.value = false
                    Log.e(TAG, "Failed to load GGUF model from URI: $message")
                    continuation.resume(false)
                }
            }

            try {
                svc.loadGgufModelFromFd(
                    pfd,
                    modelName,
                    modelConfig.modelLoadingParams ?: "",
                    modelConfig.modelInferenceParams ?: "",
                    callback
                )
            } catch (e: Exception) {
                Log.e(TAG, "Exception loading GGUF model from URI", e)
                pfd.close()
                continuation.resumeWithException(e)
            }
        }
    }

    fun ggufGenerateStreaming(prompt: String, maxToken: Int): Flow<GenerationEvent> = callbackFlow {
        serviceBound.await()

        val svc = service
        if (svc == null) {
            trySend(GenerationEvent.Error("Service not bound"))
            close()
            return@callbackFlow
        }

        val callback = object : IGgufGenerationCallback.Stub() {
            override fun onToken(token: String) {
                trySend(GenerationEvent.Token(token))
            }

            override fun onToolCall(name: String, args: String) {
                trySend(GenerationEvent.ToolCall(name, args))
            }

            override fun onMetrics(
                totalTokens: Int,
                promptTokens: Int,
                generatedTokens: Int,
                tokensPerSecond: Float,
                timeToFirstToken: Long,
                totalTimeMs: Long
            ) {
                trySend(
                    GenerationEvent.Metrics(
                        DecodingMetrics(
                            totalTokens,
                            promptTokens,
                            generatedTokens,
                            tokensPerSecond,
                            timeToFirstToken,
                            totalTimeMs
                        )
                    )
                )
            }

            override fun onDone() {
                trySend(GenerationEvent.Done)
                close()
            }

            override fun onError(message: String) {
                trySend(GenerationEvent.Error(message))
                close()
            }
        }

        try {
            svc.generateGguf(prompt, maxToken, callback)
        } catch (e: Exception) {
            trySend(GenerationEvent.Error(e.message ?: "Failed to start generation"))
            close()
        }

        awaitClose {
            // Optional: stop generation if flow is cancelled
        }
    }.buffer(Channel.UNLIMITED)
        .flowOn(Dispatchers.IO)

    fun ggufStopGeneration() {
        service?.stopGenerationGguf()
        Log.i(TAG, "GGUF generation stopped")
    }

    fun unloadGgufModel() {
        service?.unloadModelGguf()
        _isGgufModelLoaded.value = false
        Log.i(TAG, "GGUF model unloaded")
    }

    fun getGgufModelInfo(): String? {
        return service?.modelInfoGguf
    }

    // ==================== Tool Calling Methods ====================

    /**
     * Set tools for tool calling (backward-compatible)
     * @param toolsJson JSON string containing tool definitions in OpenAI format
     * @return true if tools were set successfully
     */
    fun setToolsGguf(toolsJson: String): Boolean {
        return try {
            service?.setToolsJsonGguf(toolsJson) ?: false
        } catch (e: Exception) {
            Log.e(TAG, "Failed to set tools: ${e.message}")
            false
        }
    }

    /**
     * Enable tool calling with grammar configuration.
     * Sets up tools, grammar mode, and typed grammar enforcement.
     *
     * @param toolsJson JSON array of tool definitions in OpenAI format
     * @param grammarMode 0=STRICT (forces JSON), 1=LAZY (model chooses text or tool call)
     * @param useTypedGrammar Whether to enforce exact param names/types/enums
     * @return true if tool calling was enabled successfully
     */
    fun enableToolCallingGguf(
        toolsJson: String,
        grammarMode: Int = 1, // LAZY by default
        useTypedGrammar: Boolean = true
    ): Boolean {
        return try {
            service?.enableToolCallingGguf(toolsJson, grammarMode, useTypedGrammar) ?: false
        } catch (e: Exception) {
            Log.e(TAG, "Failed to enable tool calling: ${e.message}")
            false
        }
    }

    /**
     * Clear all registered tools
     */
    fun clearToolsGguf() {
        try {
            service?.clearToolsGguf()
            Log.i(TAG, "Tools cleared")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to clear tools: ${e.message}")
        }
    }

    /**
     * Check if the loaded model supports tool calling.
     * Returns true for any model with a built-in chat template (model-agnostic).
     */
    fun isToolCallingSupportedGguf(): Boolean {
        return try {
            service?.isToolCallingSupportedGguf() ?: false
        } catch (e: Exception) {
            Log.e(TAG, "Failed to check tool calling support: ${e.message}")
            false
        }
    }

    /**
     * Set grammar enforcement mode
     * @param mode 0=STRICT, 1=LAZY
     */
    fun setGrammarModeGguf(mode: Int) {
        try {
            service?.setGrammarModeGguf(mode)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to set grammar mode: ${e.message}")
        }
    }

    /**
     * Enable or disable parameter-aware typed grammar
     */
    fun setTypedGrammarGguf(enabled: Boolean) {
        try {
            service?.setTypedGrammarGguf(enabled)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to set typed grammar: ${e.message}")
        }
    }

    // ==================== Multi-turn Generation ====================

    /**
     * Multi-turn streaming generation using full conversation history.
     * Used for multi-turn tool calling flows where the model generates,
     * calls a tool, receives the result, and generates again.
     *
     * @param messagesJson JSON array of {role, content} objects
     * @param maxTokens Maximum tokens per turn
     */
    fun ggufGenerateMultiTurnStreaming(
        messagesJson: String,
        maxTokens: Int
    ): Flow<GenerationEvent> = callbackFlow {
        serviceBound.await()

        val svc = service
        if (svc == null) {
            trySend(GenerationEvent.Error("Service not bound"))
            close()
            return@callbackFlow
        }

        val callback = object : IGgufGenerationCallback.Stub() {
            override fun onToken(token: String) {
                trySend(GenerationEvent.Token(token))
            }

            override fun onToolCall(name: String, args: String) {
                trySend(GenerationEvent.ToolCall(name, args))
            }

            override fun onMetrics(
                totalTokens: Int,
                promptTokens: Int,
                generatedTokens: Int,
                tokensPerSecond: Float,
                timeToFirstToken: Long,
                totalTimeMs: Long
            ) {
                trySend(
                    GenerationEvent.Metrics(
                        DecodingMetrics(
                            totalTokens,
                            promptTokens,
                            generatedTokens,
                            tokensPerSecond,
                            timeToFirstToken,
                            totalTimeMs
                        )
                    )
                )
            }

            override fun onDone() {
                trySend(GenerationEvent.Done)
                close()
            }

            override fun onError(message: String) {
                trySend(GenerationEvent.Error(message))
                close()
            }
        }

        try {
            svc.generateGgufMultiTurn(messagesJson, maxTokens, callback)
        } catch (e: Exception) {
            trySend(GenerationEvent.Error(e.message ?: "Failed to start multi-turn generation"))
            close()
        }

        awaitClose { }
    }.buffer(Channel.UNLIMITED)
        .flowOn(Dispatchers.IO)

    // ==================== Diffusion Methods ====================

    /**
     * Load a Stable Diffusion model
     */
    suspend fun loadDiffusionModel(
        name: String,
        modelDir: String,
        height: Int = 212,
        width: Int = 212,
        textEmbeddingSize: Int = 768,
        runOnCpu: Boolean = false,
        useCpuClip: Boolean = false,
        isPony: Boolean = false,
        httpPort: Int = 8081,
        safetyMode: Boolean
    ): Boolean {
        val svc = ensureServiceBound()

        return suspendCancellableCoroutine { continuation ->
            val callback = object : IModelLoadCallback.Stub() {
                override fun onSuccess() {
                    _isDiffusionModelLoaded.value = true
                    _diffusionBackendState.value = "Running"
                    Log.i(TAG, "Diffusion model loaded successfully: $name")
                    continuation.resume(true)
                }

                override fun onError(message: String) {
                    _isDiffusionModelLoaded.value = false
                    _diffusionBackendState.value = "Error: $message"
                    Log.e(TAG, "Failed to load diffusion model: $message")
                    continuation.resume(false)
                }
            }

            try {
                svc.loadDiffusionModel(
                    name,
                    modelDir,
                    height,
                    width,
                    textEmbeddingSize,
                    runOnCpu,
                    useCpuClip,
                    isPony,
                    httpPort,
                    safetyMode,
                    callback
                )
            } catch (e: Exception) {
                Log.e(TAG, "Exception loading diffusion model", e)
                continuation.resumeWithException(e)
            }
        }
    }

    /**
     * Diffusion generation event types
     */
    sealed class DiffusionGenerationEvent {
        data class Progress(
            val progress: Float,
            val currentStep: Int,
            val totalSteps: Int,
            val intermediateImage: Bitmap?
        ) : DiffusionGenerationEvent()

        data class Complete(
            val image: Bitmap,
            val seed: Long,
            val width: Int,
            val height: Int
        ) : DiffusionGenerationEvent()

        data class Error(val message: String) : DiffusionGenerationEvent()
    }

    /**
     * Generate image with Stable Diffusion as a Flow
     */
    fun generateDiffusionImage(
        prompt: String,
        negativePrompt: String = "",
        steps: Int = 28,
        cfgScale: Float = 7f,
        seed: Long = -1L,
        width: Int = 512,
        height: Int = 512,
        scheduler: String = "dpm",
        useOpenCL: Boolean = false,
        inputImage: String? = null,
        mask: String? = null,
        denoiseStrength: Float = 0.6f,
        showDiffusionProcess: Boolean = false,
        showDiffusionStride: Int = 1
    ): Flow<DiffusionGenerationEvent> = callbackFlow {
        serviceBound.await()

        val svc = service
        if (svc == null) {
            trySend(DiffusionGenerationEvent.Error("Service not bound"))
            close()
            return@callbackFlow
        }

        val callback = object : IDiffusionGenerationCallback.Stub() {
            override fun onProgress(
                progress: Float,
                currentStep: Int,
                totalSteps: Int,
                intermediateImageBase64: String?
            ) {
                val bitmap = intermediateImageBase64?.takeIf { it.isNotEmpty() }?.let {
                    base64ToBitmap(it)
                }

                trySend(
                    DiffusionGenerationEvent.Progress(
                        progress,
                        currentStep,
                        totalSteps,
                        bitmap
                    )
                )
            }

            override fun onComplete(
                imageBase64: String,
                completedSeed: Long,
                resultWidth: Int,
                resultHeight: Int
            ) {
                try {
                    val bitmap = base64ToBitmap(imageBase64)
                    trySend(
                        DiffusionGenerationEvent.Complete(
                            bitmap,
                            completedSeed,
                            resultWidth,
                            resultHeight
                        )
                    )
                    close()
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to decode result image", e)
                    trySend(DiffusionGenerationEvent.Error("Failed to decode result"))
                    close()
                }
            }

            override fun onError(message: String) {
                trySend(DiffusionGenerationEvent.Error(message))
                close()
            }
        }

        try {
            svc.generateDiffusionImage(
                prompt,
                negativePrompt,
                steps,
                cfgScale,
                seed,
                512,
                512,
                scheduler,
                useOpenCL,
                inputImage,
                mask,
                denoiseStrength,
                showDiffusionProcess,
                showDiffusionStride,
                callback
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start diffusion generation", e)
            trySend(DiffusionGenerationEvent.Error(e.message ?: "Failed to start generation"))
            close()
        }

        awaitClose {
            // Flow cancelled
        }
    }.buffer(Channel.UNLIMITED)
        .flowOn(Dispatchers.IO)

    /**
     * Stop diffusion image generation
     */
    fun stopDiffusionGeneration() {
        service?.stopGenerationDiffusion()
        Log.i(TAG, "Diffusion generation stopped")
    }

    /**
     * Restart diffusion backend
     */
    suspend fun restartDiffusionBackend(): Boolean {
        val svc = ensureServiceBound()

        return suspendCancellableCoroutine { continuation ->
            val callback = object : IModelLoadCallback.Stub() {
                override fun onSuccess() {
                    _diffusionBackendState.value = "Running"
                    Log.i(TAG, "Diffusion backend restarted")
                    continuation.resume(true)
                }

                override fun onError(message: String) {
                    _diffusionBackendState.value = "Error: $message"
                    Log.e(TAG, "Failed to restart diffusion backend: $message")
                    continuation.resume(false)
                }
            }

            try {
                svc.restartDiffusionBackend(callback)
            } catch (e: Exception) {
                Log.e(TAG, "Exception restarting diffusion backend", e)
                continuation.resumeWithException(e)
            }
        }
    }

    /**
     * Stop diffusion backend
     */
    fun stopDiffusionBackend() {
        service?.stopDiffusionBackend()
        _isDiffusionModelLoaded.value = false
        _diffusionBackendState.value = "Idle"
        Log.i(TAG, "Diffusion backend stopped")
    }

    /**
     * Get current diffusion backend state
     */
    suspend fun getDiffusionBackendState(): String {
        val svc = ensureServiceBound()
        return svc.diffusionBackendState ?: "Unknown"
    }

    /**
     * Get current diffusion model info
     */
    suspend fun getCurrentDiffusionModel(): String? {
        val svc = ensureServiceBound()
        return svc.currentDiffusionModel
    }

    // ==================== Utility Methods ====================

    /**
     * Convert base64 string to Bitmap
     */
    private fun base64ToBitmap(base64String: String): Bitmap {
        val decodedBytes = Base64.getDecoder().decode(base64String)
        return BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size)
            ?: throw IllegalArgumentException("Failed to decode bitmap from base64")
    }

    /**
     * Convert Bitmap to base64 string (for img2img)
     */
    fun bitmapToBase64(bitmap: Bitmap, quality: Int = 95): String {
        val outputStream = java.io.ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, quality, outputStream)
        val bytes = outputStream.toByteArray()
        return Base64.getEncoder().encodeToString(bytes)
    }

    /**
     * Convert Bitmap to RGB base64 (for img2img with raw RGB data)
     */
    fun bitmapToRgbBase64(bitmap: Bitmap): String {
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)

        val rgbBytes = ByteArray(bitmap.width * bitmap.height * 3)
        for (i in pixels.indices) {
            val pixel = pixels[i]
            val index = i * 3
            rgbBytes[index] = ((pixel shr 16) and 0xFF).toByte()     // R
            rgbBytes[index + 1] = ((pixel shr 8) and 0xFF).toByte()  // G
            rgbBytes[index + 2] = (pixel and 0xFF).toByte()          // B
        }

        return Base64.getEncoder().encodeToString(rgbBytes)
    }

    // ==================== Embedding Model Download ====================

    /**
     * Start background download of embedding model
     */
    fun startEmbeddingModelDownload(context: Context) {
        val workRequest = OneTimeWorkRequestBuilder<EmbeddingModelDownloadWorker>()
            .addTag(EmbeddingModelDownloadWorker.TAG)
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            EmbeddingModelDownloadWorker.TAG,
            ExistingWorkPolicy.KEEP,
            workRequest
        )

        Log.i(TAG, "Embedding model download started in background")
    }
}