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
import com.dark.ai_sd.toBase64Rgb
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import java.util.Base64

class DiffusionEngine {

    companion object {
        private const val TAG = "DiffusionEngine"
    }

    private lateinit var sdManager: StableDiffusionManager
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var observeJob: Job? = null

    // Expose state flows
    val backendState: StateFlow<DiffusionBackendState>
        get() = sdManager.diffusionBackendState

    val generationState: StateFlow<DiffusionGenerationState>
        get() = sdManager.diffusionGenerationState


    val isGenerating: StateFlow<Boolean>
        get() = sdManager.isGenerating

    suspend fun init(context: Context, safetyCheckerEnabled: Boolean = true) {
        try {
            sdManager = StableDiffusionManager.getInstance(context)
            sdManager.initialize(
                DiffusionRuntimeConfig(
                    runtimeDir = "runtime_libs/qnnlibs",
                    qnnLibsAssetPath = "qnnlibs",
                    safetyCheckerEnabled = safetyCheckerEnabled,
                    safetyCheckerAssetPath = if (safetyCheckerEnabled) "safety_checker.mnn" else ""
                )
            )
            Log.i(TAG, "DiffusionEngine initialized successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Init failed: ${e.message}", e)
            throw e
        }
    }

    fun loadModel(
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

            val success = sdManager.loadModel(model, width = width, height = height)

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

        sdManager.generateImage(params)
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

    fun observeBackendState(
        onStateChange: (DiffusionBackendState) -> Unit
    ) {
        scope.launch {
            backendState.collect { state ->
                onStateChange(state)
                Log.d(TAG, "Backend state: $state")
            }
        }
    }

    fun cancelGeneration() {
        sdManager.cancelGeneration()
        Log.i(TAG, "Generation cancelled")
    }

    fun restartBackend(): Boolean {
        val success = sdManager.restartBackend()
        if (success) {
            Log.i(TAG, "Backend restarted successfully")
        } else {
            Log.e(TAG, "Failed to restart backend")
        }
        return success
    }

    fun stopBackend() {
        sdManager.stopBackend()
        Log.i(TAG, "Backend stopped")
    }

    fun getCurrentModel(): DiffusionModelConfig? {
        return sdManager.getCurrentModel()
    }

    fun isBackendRunning(): Boolean {
        return sdManager.isBackendRunning()
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
        get() = sdManager.upscaleState

    fun loadUpscaler(modelPath: String): Boolean {
        return try {
            sdManager.loadUpscaler(modelPath)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load upscaler: ${e.message}")
            false
        }
    }

    fun upscaleImage(bitmap: Bitmap) {
        sdManager.upscaleImage(bitmap)
    }

    fun releaseUpscaler() {
        try {
            sdManager.releaseUpscaler()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to release upscaler: ${e.message}")
        }
    }

    // ── Segmenter (MobileSAM) ──

    val segmenterState: StateFlow<SegmenterState>
        get() = sdManager.segmenterState

    fun loadSegmenter(encoderPath: String, decoderPath: String): Boolean {
        return try {
            sdManager.loadSegmenter(encoderPath, decoderPath)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load segmenter: ${e.message}")
            false
        }
    }

    fun segmenterEncodeImage(bitmap: Bitmap): Boolean {
        return sdManager.segmenterEncodeImage(bitmap)
    }

    fun segmentAtPoint(x: Float, y: Float) {
        sdManager.segmentAtPoint(x, y)
    }

    fun segmentWithBox(x1: Float, y1: Float, x2: Float, y2: Float) {
        sdManager.segmentWithBox(x1, y1, x2, y2)
    }

    fun releaseSegmenter() {
        try { sdManager.releaseSegmenter() } catch (e: Exception) {
            Log.e(TAG, "Failed to release segmenter: ${e.message}")
        }
    }

    // ── LaMa Inpainter ──

    val lamaState: StateFlow<LamaState>
        get() = sdManager.lamaState

    fun loadLamaInpainter(modelPath: String): Boolean {
        return try {
            sdManager.loadLamaInpainter(modelPath)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load LaMa inpainter: ${e.message}")
            false
        }
    }

    fun lamaInpaint(inputBitmap: Bitmap, maskBitmap: Bitmap) {
        sdManager.lamaInpaint(inputBitmap, maskBitmap)
    }

    fun releaseLamaInpainter() {
        try { sdManager.releaseLamaInpainter() } catch (e: Exception) {
            Log.e(TAG, "Failed to release LaMa inpainter: ${e.message}")
        }
    }

    // ── Depth Estimator ──

    val depthState: StateFlow<DepthState>
        get() = sdManager.depthState

    fun loadDepthEstimator(modelPath: String): Boolean {
        return try {
            sdManager.loadDepthEstimator(modelPath)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load depth estimator: ${e.message}")
            false
        }
    }

    fun estimateDepth(inputBitmap: Bitmap) {
        sdManager.estimateDepth(inputBitmap)
    }

    fun releaseDepthEstimator() {
        try { sdManager.releaseDepthEstimator() } catch (e: Exception) {
            Log.e(TAG, "Failed to release depth estimator: ${e.message}")
        }
    }

    // ── Style Transfer ──

    val styleState: StateFlow<StyleState>
        get() = sdManager.styleState

    fun loadStyleTransfer(modelPath: String): Boolean {
        return try {
            sdManager.loadStyleTransfer(modelPath)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load style transfer: ${e.message}")
            false
        }
    }

    fun stylize(contentBitmap: Bitmap, styleBitmap: Bitmap, strength: Float = 1.0f) {
        sdManager.stylize(contentBitmap, styleBitmap, strength)
    }

    fun releaseStyleTransfer() {
        try { sdManager.releaseStyleTransfer() } catch (e: Exception) {
            Log.e(TAG, "Failed to release style transfer: ${e.message}")
        }
    }

    fun cleanup() {
        observeJob?.cancel()
        scope.cancel()
        sdManager.cleanup()
        Log.i(TAG, "DiffusionEngine cleaned up")
    }

    // Utility functions

    private fun generateModelId(name: String, runOnCpu: Boolean): String {
        return "${name.lowercase().replace(" ", "-")}-${if (runOnCpu) "cpu" else "npu"}"
    }

    fun bitmapToBase64(bitmap: Bitmap, quality: Int = 95): String {
        val outputStream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, quality, outputStream)
        val bytes = outputStream.toByteArray()
        return Base64.getEncoder().encodeToString(bytes)
    }

    fun bitmapToRgbBase64(bitmap: Bitmap): String {
        return bitmap.toBase64Rgb()
    }
}