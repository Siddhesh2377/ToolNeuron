package com.dark.tool_neuron.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.DeadObjectException
import android.os.ParcelFileDescriptor
import android.os.Process
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.dark.tool_neuron.R
import com.dark.tool_neuron.data.AppSettingsDataStore
import com.dark.tool_neuron.engine.DiffusionEngine
import com.dark.tool_neuron.engine.GGUFEngine
import com.dark.tool_neuron.engine.GenerationEvent
import com.dark.tool_neuron.models.enums.PathType
import com.dark.tool_neuron.models.enums.ProviderType
import com.dark.tool_neuron.models.table_schema.Model
import com.dark.tool_neuron.models.table_schema.ModelConfig
import com.dark.tool_neuron.network.NsdHelper
import com.dark.tool_neuron.network.RemoteServer
import com.dark.tool_neuron.state.AppStateManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import javax.inject.Inject

@AndroidEntryPoint
class LLMService : Service() {

    companion object {
        private const val TAG = "LLMService"

        /** Direct reference for same-process callers (avoids AIDL serialization). */
        @Volatile
        var instance: LLMService? = null
            private set
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val ggufEngine = GGUFEngine()
    private val diffusionEngine = DiffusionEngine()

    @Inject
    lateinit var remoteServer: RemoteServer

    @Inject
    lateinit var nsdHelper: NsdHelper

    private fun collectGenerationFlow(
        flow: kotlinx.coroutines.flow.Flow<GenerationEvent>,
        callback: IGgufGenerationCallback
    ) {
        scope.launch(Dispatchers.IO) {
            try {
                flow.collect { event ->
                    try {
                        when (event) {
                            is GenerationEvent.Token -> callback.onToken(event.text)
                            is GenerationEvent.Done -> callback.onDone()
                            is GenerationEvent.Error -> callback.onError(event.message)
                            is GenerationEvent.Metrics -> {
                                val m = event.metrics
                                callback.onMetrics(
                                    m.tokensPerSecond, m.timeToFirstTokenMs, m.totalTimeMs,
                                    m.tokensEvaluated, m.tokensPredicted,
                                    m.modelSizeMB, m.contextSizeMB, m.peakMemoryMB, m.memoryUsagePercent
                                )
                            }
                            is GenerationEvent.ToolCall -> callback.onToolCall(event.name, event.args)
                            is GenerationEvent.Progress -> callback.onProgress(event.progress)
                        }
                    } catch (e: DeadObjectException) {
                        Log.w(TAG, "Client disconnected during generation")
                        ggufEngine.stopGeneration()
                        return@collect
                    }
                }
            } catch (e: DeadObjectException) {
                Log.w(TAG, "Client disconnected during generation", e)
                ggufEngine.stopGeneration()
            } catch (e: Exception) {
                try {
                    callback.onError(e.message ?: "Unknown error")
                } catch (_: Exception) { }
            }
        }
    }

    private val binder = object : ILLMService.Stub() {

        // ── GGUF Methods ──

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

                    val result = ggufEngine.load(model, config)

                    result.fold(
                        onSuccess = {
                            AppStateManager.setModelLoaded(modelName)
                            callback.onSuccess()
                        },
                        onFailure = { error ->
                            val errorMsg = error.message ?: "Failed to load model"
                            AppStateManager.setError("Load failed: $errorMsg")
                            callback.onError(errorMsg)
                        }
                    )
                } catch (e: Exception) {
                    val errorMsg = e.message ?: "Unknown error"
                    AppStateManager.setError("Exception: $errorMsg")
                    callback.onError(errorMsg)
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
            // Detach fd synchronously before coroutine launch —
            // Binder may close the ParcelFileDescriptor after the AIDL call returns
            val fd = pfd.detachFd()

            scope.launch(Dispatchers.IO) {
                try {
                    AppStateManager.setLoadingModel(modelName)

                    val config = ModelConfig(
                        modelId = modelName,
                        modelLoadingParams = loadingParams,
                        modelInferenceParams = inferenceParams
                    )

                    val result = ggufEngine.loadFromFd(fd, config)

                    result.fold(
                        onSuccess = {
                            AppStateManager.setModelLoaded(modelName)
                            callback.onSuccess()
                        },
                        onFailure = { error ->
                            val errorMsg = error.message ?: "Failed to load model from file descriptor"
                            AppStateManager.setError("Load from FD failed: $errorMsg")
                            callback.onError(errorMsg)
                        }
                    )
                } catch (e: Exception) {
                    val errorMsg = e.message ?: "Unknown error"
                    AppStateManager.setError("Exception: $errorMsg")
                    callback.onError(errorMsg)
                }
            }
        }

        override fun generateGguf(
            prompt: String, maxTokens: Int, callback: IGgufGenerationCallback
        ) {
            collectGenerationFlow(ggufEngine.generateFlow(prompt, maxTokens), callback)
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

        override fun setToolsJsonGguf(toolsJson: String): Boolean =
            ggufEngine.setToolsJson(toolsJson)

        override fun clearToolsGguf() {
            ggufEngine.clearTools()
        }

        // ── Multi-turn Tool Calling ──

        override fun enableToolCallingGguf(
            toolsJson: String, grammarMode: Int, useTypedGrammar: Boolean
        ): Boolean = ggufEngine.enableToolCalling(toolsJson, grammarMode, useTypedGrammar)

        override fun generateGgufMultiTurn(
            messagesJson: String, maxTokens: Int, callback: IGgufGenerationCallback
        ) {
            collectGenerationFlow(ggufEngine.generateMultiTurnFlow(messagesJson, maxTokens), callback)
        }

        override fun setGrammarModeGguf(mode: Int) {
            // Grammar mode applied via tool calling config
        }

        override fun setTypedGrammarGguf(enabled: Boolean) {
            // Grammar mode applied via tool calling config
        }

        override fun isToolCallingSupportedGguf(): Boolean =
            ggufEngine.isToolCallingSupported()

        // ── Persona Engine ──

        override fun updateSamplerParamsGguf(paramsJson: String): Boolean =
            ggufEngine.updateSamplerParams(paramsJson)

        override fun setLogitBiasGguf(biasJson: String): Boolean =
            ggufEngine.setLogitBias(biasJson)

        override fun loadControlVectorsGguf(vectorsJson: String): Boolean =
            ggufEngine.loadControlVectors(vectorsJson)

        override fun clearControlVectorGguf(): Boolean =
            ggufEngine.clearControlVector()

        // ── KV Cache State Persistence ──

        override fun getStateSizeGguf(): Long = ggufEngine.getStateSize()
        override fun stateSaveToFileGguf(path: String): Boolean = ggufEngine.stateSaveToFile(path)
        override fun stateLoadFromFileGguf(path: String): Boolean = ggufEngine.stateLoadFromFile(path)

        // ── New Optimizations ──

        override fun setSpeculativeDecodingGguf(enabled: Boolean, nDraft: Int, ngramSize: Int) {
            ggufEngine.setSpeculativeDecoding(enabled, nDraft, ngramSize)
        }

        override fun setPromptCacheDirGguf(path: String) {
            ggufEngine.setPromptCacheDir(path)
        }

        override fun warmUpGguf(): Boolean = ggufEngine.warmUp()

        override fun supportsThinkingGguf(): Boolean = ggufEngine.supportsThinking()

        override fun setThinkingEnabledGguf(enabled: Boolean) {
            ggufEngine.setThinkingEnabled(enabled)
        }

        override fun getContextUsageGguf(): Float = ggufEngine.getContextUsage()

        // ── Context Window Tracking ──

        override fun getContextInfoGguf(prompt: String?): String {
            val info = ggufEngine.getContextInfo(prompt)
            return org.json.JSONObject().apply {
                put("total", info.total)
                put("used", info.used)
                put("remaining", info.remaining)
                put("promptEstimate", info.promptEstimate)
                put("afterPrompt", info.afterPrompt)
            }.toString()
        }

        // ── Character Engine ──

        override fun setPersonalityGguf(personalityJson: String): Boolean =
            ggufEngine.setPersonality(personalityJson)

        override fun setMoodGguf(mood: Int): Boolean =
            ggufEngine.setMood(mood)

        override fun setCustomMoodGguf(tempMod: Float, topPMod: Float, repPenaltyMod: Float): Boolean =
            ggufEngine.setCustomMood(tempMod, topPMod, repPenaltyMod)

        override fun getCharacterContextGguf(): String =
            ggufEngine.getCharacterContext()

        override fun buildPromptGguf(userPrompt: String): String =
            ggufEngine.buildPrompt(userPrompt)

        override fun setUncensoredGguf(enabled: Boolean): Boolean =
            ggufEngine.setUncensored(enabled)

        override fun isUncensoredGguf(): Boolean =
            ggufEngine.isUncensored()

        // ── Upscaler ──

        override fun loadUpscaler(modelPath: String, callback: IModelLoadCallback) {
            scope.launch(Dispatchers.IO) {
                try {
                    val success = diffusionEngine.loadUpscaler(modelPath)
                    if (success) callback.onSuccess()
                    else callback.onError("Failed to load upscaler")
                } catch (e: Exception) {
                    callback.onError(e.message ?: "Unknown error loading upscaler")
                }
            }
        }

        override fun releaseUpscaler() {
            diffusionEngine.releaseUpscaler()
        }

        // ── Diffusion Methods ──

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

                    diffusionEngine.observeGenerationState(onProgress = { progress, currentStep, totalSteps, intermediateBitmap ->
                        try {
                            val imageBase64 = intermediateBitmap?.let {
                                diffusionEngine.bitmapToBase64(it, quality = 80)
                            } ?: ""

                            callback.onProgress(progress, currentStep, totalSteps, imageBase64)
                            Log.d(TAG, "Generation progress: ${(progress * 100).toInt()}%")
                        } catch (e: Exception) {
                            Log.e(TAG, "Error sending progress", e)
                        }
                    }, onComplete = { bitmap, completedSeed, resultWidth, resultHeight ->
                        try {
                            val imageBase64 = diffusionEngine.bitmapToBase64(bitmap)
                            callback.onComplete(imageBase64, completedSeed ?: -1L, resultWidth, resultHeight)
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
                    } catch (_: Exception) { }
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
                    if (success) callback.onSuccess()
                    else callback.onError("Failed to restart backend")
                } catch (e: Exception) {
                    callback.onError(e.message ?: "Unknown error restarting backend")
                }
            }
        }

        override fun stopDiffusionBackend() {
            diffusionEngine.stopBackend()
            Log.i(TAG, "Diffusion backend stopped")
        }

        override fun getDiffusionBackendState(): String =
            diffusionEngine.getBackendStateString()

        override fun getCurrentDiffusionModel(): String? {
            val model = diffusionEngine.getCurrentModel()
            return model?.name
        }

        override fun startRemoteServer(port: Int) {
            remoteServer.setEngines(ggufEngine, diffusionEngine)
            remoteServer.start(port)
            nsdHelper.registerService(port)
        }

        override fun stopRemoteServer() {
            remoteServer.stop()
            nsdHelper.unregisterService()
        }

        override fun isRemoteServerRunning(): Boolean {
            return remoteServer.isRunning()
        }
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        instance = this
        Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND)

        // Tell llama.cpp where to find CPU backend variant .so files
        // (libggml-cpu-android_armv8.*.so) for runtime arch-level dispatch.
        try {
            val engineClass = Class.forName("com.dark.gguf_lib.GGMLEngine")
            val initMethod = engineClass.getMethod("initBackendDir", android.content.Context::class.java)
            initMethod.invoke(null, applicationContext)
        } catch (_: Throwable) {
            // Old AAR without initBackendDir — dladdr() fallback handles it
        }

        // Initialize diffusion engine
        scope.launch(Dispatchers.IO) {
            try {
                diffusionEngine.init(applicationContext, safetyCheckerEnabled = true)
                Log.i(TAG, "DiffusionEngine initialized in LLMService")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to initialize diffusion engine", e)
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceCompat.startForeground(
                this, 1, createNotification(),
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                else 0
            )
        } else {
            startForeground(1, createNotification())
        }

        // Monitor settings for Remote API
        scope.launch {
            val settings = AppSettingsDataStore(applicationContext)
            combine(
                settings.remoteApiEnabled,
                settings.remoteApiPort,
                settings.remoteApiNsdEnabled
            ) { enabled, port, nsdEnabled ->
                Triple(enabled, port, nsdEnabled)
            }.collect { (enabled, port, nsdEnabled) ->
                if (enabled) {
                    if (!remoteServer.isRunning()) {
                        remoteServer.setEngines(ggufEngine, diffusionEngine)
                        remoteServer.start(port)
                    }
                    if (nsdEnabled) {
                        nsdHelper.registerService(port)
                    } else {
                        nsdHelper.unregisterService()
                    }
                } else {
                    remoteServer.stop()
                    nsdHelper.unregisterService()
                }
            }
        }
    }

    override fun onDestroy() {
        remoteServer.stop()
        nsdHelper.unregisterService()
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
