package com.dark.tool_neuron.worker

import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.IBinder
import android.util.Log
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
import java.util.Base64
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

@SuppressLint("StaticFieldLeak")
object LlmModelWorker {

    private const val TAG = "LlmModelWorker"

    private var service: ILLMService? = null
    private var boundContext: Context? = null
    private val serviceBound = CompletableDeferred<Unit>()

    // GGUF state
    private val _isGgufModelLoaded = MutableStateFlow(false)
    val isGgufModelLoaded: StateFlow<Boolean> = _isGgufModelLoaded.asStateFlow()

    // Diffusion state
    private val _isDiffusionModelLoaded = MutableStateFlow(false)
    val isDiffusionModelLoaded: StateFlow<Boolean> = _isDiffusionModelLoaded.asStateFlow()

    private val _diffusionBackendState = MutableStateFlow("Idle")
    val diffusionBackendState: StateFlow<String> = _diffusionBackendState.asStateFlow()

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            service = ILLMService.Stub.asInterface(binder)
            serviceBound.complete(Unit)
            Log.i(TAG, "Service connected")
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            service = null
            Log.w(TAG, "Service disconnected")
        }
    }

    // ==================== Service Management ====================

    fun bindService(context: Context) {
        if (boundContext != null) {
            Log.w(TAG, "Service already bound")
            return
        }

        val intent = Intent(context, LLMService::class.java)
        context.bindService(intent, connection, Context.BIND_AUTO_CREATE)
        boundContext = context.applicationContext
        Log.i(TAG, "Binding service...")
    }

    fun unbindService() {
        boundContext?.unbindService(connection)
        service = null
        boundContext = null
        _isGgufModelLoaded.value = false
        _isDiffusionModelLoaded.value = false
        Log.i(TAG, "Service unbound")
    }

    private suspend fun ensureServiceBound(): ILLMService {
        serviceBound.await()
        return service ?: throw IllegalStateException("Service not available")
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

    // ==================== Diffusion Methods ====================

    /**
     * Load a Stable Diffusion model
     */
    suspend fun loadDiffusionModel(
        name: String,
        modelDir: String,
        textEmbeddingSize: Int = 768,
        runOnCpu: Boolean = false,
        useCpuClip: Boolean = false,
        isPony: Boolean = false,
        httpPort: Int = 8081,
        safetyMode: Boolean = false
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
                width,
                height,
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
}