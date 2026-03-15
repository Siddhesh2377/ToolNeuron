package com.dark.tool_neuron.engine

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.dark.ai_sd.DepthState
import com.dark.ai_sd.DiffusionBackendState
import com.dark.ai_sd.DiffusionGenerationState
import com.dark.ai_sd.DiffusionModelConfig
import com.dark.ai_sd.DiffusionRuntimeConfig
import com.dark.ai_sd.LamaState
import com.dark.ai_sd.SegmenterState
import com.dark.ai_sd.StableDiffusionManager
import com.dark.ai_sd.StyleState
import com.dark.ai_sd.UpscaleState
import com.dark.ai_sd.generationParams
import com.dark.ai_sd.modelConfig
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.io.ByteArrayOutputStream
import java.util.Base64

class DiffusionEngine {

    companion object {
        private const val TAG = "DiffusionEngine"
        private const val INIT_TIMEOUT_MS = 60_000L
    }

    private var sdManager: StableDiffusionManager? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var observeJob: Job? = null
    private val initDeferred = CompletableDeferred<Boolean>()

    // Expose state flows
    val backendState: StateFlow<DiffusionBackendState>
        get() = sdManager?.diffusionBackendState ?: MutableStateFlow(DiffusionBackendState.Idle)

    val generationState: StateFlow<DiffusionGenerationState>
        get() = sdManager?.diffusionGenerationState ?: MutableStateFlow(DiffusionGenerationState.Idle)

    val isGenerating: StateFlow<Boolean>
        get() = sdManager?.isGenerating ?: MutableStateFlow(false)

    suspend fun init(context: Context, safetyCheckerEnabled: Boolean = true) {
        try {
            val mgr = StableDiffusionManager.getInstance(context)
            sdManager = mgr
            mgr.initialize(
                DiffusionRuntimeConfig(
                    runtimeDir = "runtime_libs/qnnlibs",
                    qnnLibsAssetPath = "qnnlibs",
                    safetyCheckerEnabled = safetyCheckerEnabled,
                    safetyCheckerAssetPath = if (safetyCheckerEnabled) "safety_checker.mnn" else ""
                )
            )
            initDeferred.complete(true)
            Log.i(TAG, "DiffusionEngine initialized successfully")
        } catch (e: Exception) {
            initDeferred.complete(false)
            Log.e(TAG, "Init failed: ${e.message}", e)
            throw e
        }
    }

    suspend fun loadModel(
        name: String,
        modelDir: String,
        textEmbeddingSize: Int = 768,
        runOnCpu: Boolean = false,
        useCpuClip: Boolean = false,
        isPony: Boolean = false,
        httpPort: Int = 8081, // Kept for API compat, no longer used (JNI direct)
        width: Int = 512,
        height: Int = 512,
        safetyMode: Boolean
    ): Result<String> {
        // Wait for init() to complete before proceeding (fixes race with LLMService.onCreate)
        val initSuccess = withTimeoutOrNull(INIT_TIMEOUT_MS) { initDeferred.await() }
        if (initSuccess != true) {
            return Result.failure(IllegalStateException("DiffusionEngine initialization timed out or failed"))
        }
        val mgr = sdManager ?: return Result.failure(IllegalStateException("DiffusionEngine not initialized"))
        return try {
            val model = modelConfig {
                name(name)
                modelDir(modelDir)
                textEmbeddingSize(textEmbeddingSize)
                runOnCpu(runOnCpu)
                useCpuClip(useCpuClip)
                isPony(isPony)
                setSafetyMode(safetyMode)
            }

            Log.i(TAG, "Loading model: $model")

            val success = mgr.loadModel(model, width = width, height = height)

            if (success) {
                Log.i(TAG, "Model loaded successfully: $name")
                Result.success("Model loaded: $name (${if (runOnCpu) "CPU" else "NPU"})")
            } else {
                Log.e(TAG, "Failed to load model: $name")
                Result.failure(Exception("Failed to load model: $name"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "loadModel exception: ${e.message}", e)
            Result.failure(e)
        }
    }

    fun generateImage(
        prompt: String,
        negativePrompt: String = "",
        steps: Int = 28,
        cfgScale: Float = 7f,
        seed: Long? = null,
        width: Int = 512,
        height: Int = 512,
        scheduler: String = "dpm",
        useOpenCL: Boolean = false,
        inputImage: String? = null,
        mask: String? = null,
        denoiseStrength: Float = 0.6f,
        showDiffusionProcess: Boolean = false,
        showDiffusionStride: Int = 1
    ) {
        val params = generationParams {
            prompt(prompt)
            negativePrompt(negativePrompt)
            steps(steps)
            cfgScale(cfgScale)
            seed(seed)
            resolution(width, height)
            scheduler(scheduler)
            useOpenCL(useOpenCL)
            inputImage(inputImage)
            mask(mask)
            denoiseStrength(denoiseStrength)
            showProcess(showDiffusionProcess, showDiffusionStride)
        }

        val mgr = sdManager ?: throw IllegalStateException("DiffusionEngine not initialized")
        mgr.generateImage(params)
        Log.i(TAG, "Generation started: $prompt")
    }

    fun observeGenerationState(
        onProgress: (Float, Int, Int, Bitmap?) -> Unit,
        onComplete: (Bitmap, Long?, Int, Int) -> Unit,
        onError: (String) -> Unit
    ) {
        // Cancel previous observation
        observeJob?.cancel()

        observeJob = scope.launch {
            generationState.collect { state ->
                when (state) {
                    is DiffusionGenerationState.Progress -> {
                        onProgress(
                            state.progress,
                            state.currentStep,
                            state.totalSteps,
                            state.intermediateImage
                        )
                    }

                    is DiffusionGenerationState.Complete -> {
                        onComplete(
                            state.bitmap, state.seed, state.width, state.height
                        )
                    }

                    is DiffusionGenerationState.Error -> {
                        onError(state.message)
                    }

                    else -> {}
                }
            }
        }
    }

    fun cancelGeneration() {
        val mgr = sdManager ?: return
        mgr.cancelGeneration()
        Log.i(TAG, "Generation cancelled")
    }

    fun restartBackend(): Boolean {
        val mgr = sdManager ?: return false
        val success = mgr.restartBackend()
        if (success) {
            Log.i(TAG, "Backend restarted successfully")
        } else {
            Log.e(TAG, "Failed to restart backend")
        }
        return success
    }

    fun stopBackend() {
        val mgr = sdManager ?: return
        mgr.stopBackend()
        Log.i(TAG, "Backend stopped")
    }

    fun getCurrentModel(): DiffusionModelConfig? {
        return sdManager?.getCurrentModel()
    }

    fun getBackendStateString(): String {
        return when (val state = backendState.value) {
            is DiffusionBackendState.Idle -> "Idle"
            is DiffusionBackendState.Starting -> "Starting"
            is DiffusionBackendState.Running -> "Running"
            is DiffusionBackendState.Error -> "Error: ${state.message}"
        }
    }

    // ── Upscaler ──

    val upscaleState: StateFlow<UpscaleState>
        get() = sdManager?.upscaleState ?: MutableStateFlow(UpscaleState.Idle)

    fun loadUpscaler(modelPath: String): Boolean {
        val mgr = sdManager ?: return false
        return try {
            mgr.loadUpscaler(modelPath)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load upscaler: ${e.message}")
            false
        }
    }

    fun upscaleImage(bitmap: Bitmap) {
        val mgr = sdManager ?: return
        mgr.upscaleImage(bitmap)
    }

    fun releaseUpscaler() {
        val mgr = sdManager ?: return
        try {
            mgr.releaseUpscaler()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to release upscaler: ${e.message}")
        }
    }

    // ── Segmenter (MobileSAM) ──

    val segmenterState: StateFlow<SegmenterState>
        get() = sdManager?.segmenterState ?: MutableStateFlow(SegmenterState.Idle)

    fun loadSegmenter(encoderPath: String, decoderPath: String): Boolean {
        val mgr = sdManager ?: return false
        return try {
            mgr.loadSegmenter(encoderPath, decoderPath)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load segmenter: ${e.message}")
            false
        }
    }

    fun segmenterEncodeImage(bitmap: Bitmap): Boolean {
        val mgr = sdManager ?: return false
        return mgr.segmenterEncodeImage(bitmap)
    }

    fun segmentAtPoint(x: Float, y: Float) {
        val mgr = sdManager ?: return
        mgr.segmentAtPoint(x, y)
    }

    fun segmentWithBox(x1: Float, y1: Float, x2: Float, y2: Float) {
        val mgr = sdManager ?: return
        mgr.segmentWithBox(x1, y1, x2, y2)
    }

    fun releaseSegmenter() {
        val mgr = sdManager ?: return
        try { mgr.releaseSegmenter() } catch (e: Exception) {
            Log.e(TAG, "Failed to release segmenter: ${e.message}")
        }
    }

    // ── LaMa Inpainter ──

    val lamaState: StateFlow<LamaState>
        get() = sdManager?.lamaState ?: MutableStateFlow(LamaState.Idle)

    fun loadLamaInpainter(modelPath: String): Boolean {
        val mgr = sdManager ?: return false
        return try {
            mgr.loadLamaInpainter(modelPath)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load LaMa inpainter: ${e.message}")
            false
        }
    }

    fun lamaInpaint(inputBitmap: Bitmap, maskBitmap: Bitmap) {
        val mgr = sdManager ?: return
        mgr.lamaInpaint(inputBitmap, maskBitmap)
    }

    fun releaseLamaInpainter() {
        val mgr = sdManager ?: return
        try { mgr.releaseLamaInpainter() } catch (e: Exception) {
            Log.e(TAG, "Failed to release LaMa inpainter: ${e.message}")
        }
    }

    // ── Depth Estimator ──

    val depthState: StateFlow<DepthState>
        get() = sdManager?.depthState ?: MutableStateFlow(DepthState.Idle)

    fun loadDepthEstimator(modelPath: String): Boolean {
        val mgr = sdManager ?: return false
        return try {
            mgr.loadDepthEstimator(modelPath)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load depth estimator: ${e.message}")
            false
        }
    }

    fun estimateDepth(inputBitmap: Bitmap) {
        val mgr = sdManager ?: return
        mgr.estimateDepth(inputBitmap)
    }

    fun releaseDepthEstimator() {
        val mgr = sdManager ?: return
        try { mgr.releaseDepthEstimator() } catch (e: Exception) {
            Log.e(TAG, "Failed to release depth estimator: ${e.message}")
        }
    }

    // ── Style Transfer ──

    val styleState: StateFlow<StyleState>
        get() = sdManager?.styleState ?: MutableStateFlow(StyleState.Idle)

    fun loadStyleTransfer(modelPath: String): Boolean {
        val mgr = sdManager ?: return false
        return try {
            mgr.loadStyleTransfer(modelPath)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load style transfer: ${e.message}")
            false
        }
    }

    fun stylize(contentBitmap: Bitmap, styleBitmap: Bitmap, strength: Float = 1.0f) {
        val mgr = sdManager ?: return
        mgr.stylize(contentBitmap, styleBitmap, strength)
    }

    fun releaseStyleTransfer() {
        val mgr = sdManager ?: return
        try { mgr.releaseStyleTransfer() } catch (e: Exception) {
            Log.e(TAG, "Failed to release style transfer: ${e.message}")
        }
    }

    fun cleanup() {
        observeJob?.cancel()
        scope.cancel()
        sdManager?.cleanup()
        Log.i(TAG, "DiffusionEngine cleaned up")
    }

    // Utility functions

    fun bitmapToBase64(bitmap: Bitmap, quality: Int = 95): String {
        val outputStream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, quality, outputStream)
        val bytes = outputStream.toByteArray()
        return Base64.getEncoder().encodeToString(bytes)
    }
}