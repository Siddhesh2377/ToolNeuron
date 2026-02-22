package com.dark.tool_neuron.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.os.ParcelFileDescriptor
import android.os.Process
import android.util.Log
import androidx.core.app.NotificationCompat
import com.dark.tool_neuron.R
import com.dark.tool_neuron.engine.DiffusionEngine
import com.dark.tool_neuron.engine.GGUFEngine
import com.dark.tool_neuron.engine.GenerationEvent
import com.dark.tool_neuron.models.enums.PathType
import com.dark.tool_neuron.models.enums.ProviderType
import com.dark.tool_neuron.models.table_schema.Model
import com.dark.tool_neuron.models.table_schema.ModelConfig
import com.dark.tool_neuron.state.AppStateManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class LLMService : Service() {

    companion object {
        private const val TAG = "LLMService"
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val ggufEngine = GGUFEngine()
    private val diffusionEngine = DiffusionEngine()

    private val binder = object : ILLMService.Stub() {

        // ==================== GGUF Methods ====================

        override fun loadGgufModel(
            modelPath: String,
            modelName: String,
            loadingParams: String,
            inferenceParams: String,
            callback: IModelLoadCallback
        ) {
            scope.launch(Dispatchers.IO) {
                try {
                    AppStateManager.setLoadingModel(modelName)

                    val model = Model(
                        id = modelName,
                        modelPath = modelPath,
                        modelName = modelName,
                        pathType = PathType.FILE,
                        providerType = ProviderType.GGUF,
                        fileSize = null
                    )
                    val config = ModelConfig(
                        modelId = modelName,
                        modelLoadingParams = loadingParams,
                        modelInferenceParams = inferenceParams
                    )

                    val success = ggufEngine.load(model, config)

                    if (success) {
                        AppStateManager.setModelLoaded(modelName)
                        callback.onSuccess()
                    } else {
                        AppStateManager.setError("Failed to load model: $modelName")
                        callback.onError("Failed to load model")
                    }
                } catch (e: Exception) {
                    AppStateManager.setError(e.message ?: "Unknown error loading model")
                    callback.onError(e.message ?: "Unknown error")
                }
            }
        }

        override fun loadGgufModelFromFd(
            pfd: ParcelFileDescriptor,
            modelName: String,
            loadingParams: String,
            inferenceParams: String,
            callback: IModelLoadCallback
        ) {
            scope.launch(Dispatchers.IO) {
                try {
                    AppStateManager.setLoadingModel(modelName)

                    // Get the native FD and detach it so GGUFEngine can own it
                    val fd = pfd.detachFd()

                    val config = ModelConfig(
                        modelId = modelName,
                        modelLoadingParams = loadingParams,
                        modelInferenceParams = inferenceParams
                    )

                    val success = ggufEngine.loadFromFd(fd, config)

                    if (success) {
                        AppStateManager.setModelLoaded(modelName)
                        callback.onSuccess()
                    } else {
                        AppStateManager.setError("Failed to load model from FD: $modelName")
                        callback.onError("Failed to load model from file descriptor")
                    }
                } catch (e: Exception) {
                    AppStateManager.setError(e.message ?: "Unknown error loading model from FD")
                    callback.onError(e.message ?: "Unknown error")
                }
            }
        }

        override fun generateGguf(
            prompt: String, maxTokens: Int, callback: IGgufGenerationCallback
        ) {
            scope.launch(Dispatchers.IO) {
                try {
                    ggufEngine.generateFlow(prompt, maxTokens).collect { event ->
                        when (event) {
                            is GenerationEvent.Token -> {
                                callback.onToken(event.text)
                            }

                            is GenerationEvent.Done -> {
                                callback.onDone()
                            }

                            is GenerationEvent.Error -> {
                                callback.onError(event.message)
                            }

                            is GenerationEvent.Metrics -> {
                                callback.onMetrics(
                                    event.metrics.totalTokens,
                                    event.metrics.promptTokens,
                                    event.metrics.generatedTokens,
                                    event.metrics.tokensPerSecond,
                                    event.metrics.timeToFirstToken,
                                    event.metrics.totalTimeMs
                                )
                            }

                            is GenerationEvent.ToolCall -> {
                                callback.onToolCall(event.name, event.args)
                            }
                        }
                    }
                } catch (e: Exception) {
                    try {
                        callback.onError(e.message ?: "Unknown error")
                    } catch (_: Exception) {
                        // Client may have disconnected
                    }
                }
            }
        }

        override fun stopGenerationGguf() {
            ggufEngine.stopGeneration()
        }

        override fun unloadModelGguf() {
            scope.launch(Dispatchers.IO) {
                ggufEngine.unload()
                AppStateManager.setModelUnloaded()
            }
        }

        override fun getModelInfoGguf(): String? = ggufEngine.getModelInfo()

        override fun setToolsJsonGguf(toolsJson: String): Boolean {
            return ggufEngine.setToolsJson(toolsJson)
        }

        override fun clearToolsGguf() {
            ggufEngine.clearTools()
        }

        // ==================== Multi-turn Tool Calling ====================

        override fun enableToolCallingGguf(
            toolsJson: String,
            grammarMode: Int,
            useTypedGrammar: Boolean
        ): Boolean {
            return ggufEngine.enableToolCalling(toolsJson, grammarMode, useTypedGrammar)
        }

        override fun generateGgufMultiTurn(
            messagesJson: String,
            maxTokens: Int,
            callback: IGgufGenerationCallback
        ) {
            scope.launch(Dispatchers.IO) {
                try {
                    ggufEngine.generateMultiTurnFlow(messagesJson, maxTokens).collect { event ->
                        when (event) {
                            is GenerationEvent.Token -> {
                                callback.onToken(event.text)
                            }
                            is GenerationEvent.Done -> {
                                callback.onDone()
                            }
                            is GenerationEvent.Error -> {
                                callback.onError(event.message)
                            }
                            is GenerationEvent.Metrics -> {
                                callback.onMetrics(
                                    event.metrics.totalTokens,
                                    event.metrics.promptTokens,
                                    event.metrics.generatedTokens,
                                    event.metrics.tokensPerSecond,
                                    event.metrics.timeToFirstToken,
                                    event.metrics.totalTimeMs
                                )
                            }
                            is GenerationEvent.ToolCall -> {
                                callback.onToolCall(event.name, event.args)
                            }
                        }
                    }
                } catch (e: Exception) {
                    try {
                        callback.onError(e.message ?: "Unknown error")
                    } catch (_: Exception) {
                        // Client may have disconnected
                    }
                }
            }
        }

        override fun setGrammarModeGguf(mode: Int) {
            ggufEngine.setGrammarMode(mode)
        }

        override fun setTypedGrammarGguf(enabled: Boolean) {
            ggufEngine.setTypedGrammar(enabled)
        }

        override fun isToolCallingSupportedGguf(): Boolean {
            return ggufEngine.isToolCallingSupported()
        }

        // ==================== Diffusion Methods ====================

        override fun loadDiffusionModel(
            name: String,
            modelDir: String,
            height: Int,
            width: Int,
            textEmbeddingSize: Int,
            runOnCpu: Boolean,
            useCpuClip: Boolean,
            isPony: Boolean,
            httpPort: Int,
            safetyMode: Boolean,
            callback: IModelLoadCallback
        ) {
            scope.launch(Dispatchers.IO) {
                try {
                    Log.i(TAG, "Loading diffusion model: $name")
                    AppStateManager.setLoadingModel(name)

                    val result = diffusionEngine.loadModel(
                        name = name,
                        modelDir = modelDir,
                        textEmbeddingSize = textEmbeddingSize,
                        runOnCpu = runOnCpu,
                        useCpuClip = useCpuClip,
                        isPony = isPony,
                        httpPort = httpPort,
                        safetyMode = safetyMode,
                        height = height,
                        width = width
                    )

                    result.fold(onSuccess = { message ->
                        AppStateManager.setModelLoaded(name)
                        callback.onSuccess()
                        Log.i(TAG, "Diffusion model loaded: $message")
                    }, onFailure = { error ->
                        val errorMsg = error.message ?: "Failed to load diffusion model"
                        AppStateManager.setError(errorMsg)
                        callback.onError(errorMsg)
                        Log.e(TAG, "Failed to load diffusion model", error)
                    })
                } catch (e: Exception) {
                    val errorMsg = e.message ?: "Unknown error loading diffusion model"
                    AppStateManager.setError(errorMsg)
                    callback.onError(errorMsg)
                    Log.e(TAG, "Exception loading diffusion model", e)
                }
            }
        }

        override fun generateDiffusionImage(
            prompt: String,
            negativePrompt: String,
            steps: Int,
            cfgScale: Float,
            seed: Long,
            width: Int,
            height: Int,
            scheduler: String,
            useOpenCL: Boolean,
            inputImage: String?,
            mask: String?,
            denoiseStrength: Float,
            showDiffusionProcess: Boolean,
            showDiffusionStride: Int,
            callback: IDiffusionGenerationCallback
        ) {
            scope.launch(Dispatchers.IO) {
                try {
                    Log.i(TAG, "Starting diffusion generation: $prompt")

                    // Start generation
                    diffusionEngine.generateImage(
                        prompt = prompt,
                        negativePrompt = negativePrompt,
                        steps = steps,
                        cfgScale = cfgScale,
                        seed = if (seed == -1L) null else seed,
                        width = width,
                        height = height,
                        scheduler = scheduler,
                        useOpenCL = useOpenCL,
                        inputImage = inputImage,
                        mask = mask,
                        denoiseStrength = denoiseStrength,
                        showDiffusionProcess = showDiffusionProcess,
                        showDiffusionStride = showDiffusionStride
                    )

                    // Observe generation state
                    diffusionEngine.observeGenerationState(onProgress = { progress, currentStep, totalSteps, intermediateBitmap ->
                        try {
                            val imageBase64 = intermediateBitmap?.let {
                                diffusionEngine.bitmapToBase64(it, quality = 80)
                            } ?: ""

                            callback.onProgress(
                                progress, currentStep, totalSteps, imageBase64
                            )

                            Log.d(TAG, "Generation progress: ${(progress * 100).toInt()}%")
                        } catch (e: Exception) {
                            Log.e(TAG, "Error sending progress", e)
                        }
                    }, onComplete = { bitmap, completedSeed, resultWidth, resultHeight ->
                        try {
                            val imageBase64 = diffusionEngine.bitmapToBase64(bitmap)
                            callback.onComplete(
                                imageBase64, completedSeed ?: -1L, resultWidth, resultHeight
                            )
                            Log.i(TAG, "Generation completed successfully")
                        } catch (e: Exception) {
                            Log.e(TAG, "Error sending completion", e)
                            callback.onError(e.message ?: "Error processing result")
                        }
                    }, onError = { errorMessage ->
                        try {
                            callback.onError(errorMessage)
                            Log.e(TAG, "Generation error: $errorMessage")
                        } catch (e: Exception) {
                            Log.e(TAG, "Error sending error callback", e)
                        }
                    })
                } catch (e: Exception) {
                    try {
                        callback.onError(e.message ?: "Unknown error during generation")
                        Log.e(TAG, "Exception in generateDiffusionImage", e)
                    } catch (_: Exception) {
                        // Client may have disconnected
                    }
                }
            }
        }

        override fun stopGenerationDiffusion() {
            diffusionEngine.cancelGeneration()
            Log.i(TAG, "Diffusion generation stopped")
        }

        override fun restartDiffusionBackend(callback: IModelLoadCallback) {
            scope.launch(Dispatchers.IO) {
                try {
                    val success = diffusionEngine.restartBackend()
                    if (success) {
                        callback.onSuccess()
                    } else {
                        callback.onError("Failed to restart backend")
                    }
                } catch (e: Exception) {
                    callback.onError(e.message ?: "Unknown error restarting backend")
                }
            }
        }

        override fun stopDiffusionBackend() {
            diffusionEngine.stopBackend()
            Log.i(TAG, "Diffusion backend stopped")
        }

        override fun getDiffusionBackendState(): String {
            return diffusionEngine.getBackendStateString()
        }

        override fun getCurrentDiffusionModel(): String? {
            val model = diffusionEngine.getCurrentModel()
            return model?.let { "${it.name})" }
        }
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND)

        try {
            scope.launch(Dispatchers.IO) {
                diffusionEngine.init(applicationContext, safetyCheckerEnabled = true)
                Log.i(TAG, "LLMService created successfully")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize diffusion engine", e)
        }

        startForeground(1, createNotification())
    }

    override fun onDestroy() {
        scope.launch {
            ggufEngine.unload()
            diffusionEngine.cleanup()
        }
        scope.cancel()
        super.onDestroy()
        Log.i(TAG, "LLMService destroyed")
    }

    private fun createNotification(): Notification {
        val channelId = "llm_service"
        val manager = getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            channelId, "LLM Service", NotificationManager.IMPORTANCE_LOW
        )
        manager.createNotificationChannel(channel)

        return NotificationCompat.Builder(this, channelId).setContentTitle("AI Model Service")
            .setContentText("Running...").setSmallIcon(R.drawable.user)
            .setPriority(NotificationCompat.PRIORITY_LOW).setOngoing(true).setSilent(true).build()
    }
}