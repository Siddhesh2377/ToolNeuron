package com.dark.tool_neuron.viewmodel

import android.app.Application
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.dark.ai_sd.DepthState
import com.dark.ai_sd.LamaState
import com.dark.ai_sd.SegmenterState
import com.dark.ai_sd.StyleState
import com.dark.ai_sd.UpscaleState
import com.dark.tool_neuron.engine.DiffusionEngine
import com.dark.tool_neuron.global.AppPaths
import com.dark.tool_neuron.service.ModelDownloadService
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@HiltViewModel
class ImageToolsViewModel @Inject constructor(
    application: Application
) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "ImageToolsVM"

        private const val MOBILESAM_ENCODER_URL =
            "https://github.com/wangzhaode/mnn-segment-anything/releases/download/mobile_mnn/mobile_embed.mnn"
        private const val MOBILESAM_DECODER_URL =
            "https://github.com/wangzhaode/mnn-segment-anything/releases/download/mobile_mnn/mobile_segment.mnn"
    }

    // ── Tool Definitions ──

    enum class ImageTool(val label: String) {
        UPSCALER("Upscale"),
        SEGMENTER("Segment"),
        LAMA_INPAINT("Remove Object"),
        DEPTH("Depth Map"),
        STYLE_TRANSFER("Style Transfer")
    }

    data class ToolModelSpec(
        val id: String,
        val displayName: String,
        val fileName: String,
        val sizeMB: Int,
        val downloadUrl: String
    )

    private val toolModelSpecs = mapOf(
        ImageTool.UPSCALER to ToolModelSpec(
            id = "upscaler_4x.mnn",
            displayName = "4x Upscaler",
            fileName = "upscaler_4x.mnn",
            sizeMB = 18,
            downloadUrl = "https://huggingface.co/tumuyan2/realsr-models/resolve/main/models-MNNSR/RealESRGAN_x4plus_anime_6B-x4.mnn"
        ),
        ImageTool.SEGMENTER to ToolModelSpec(
            id = "mobilesam",
            displayName = "MobileSAM",
            fileName = "mobilesam",
            sizeMB = 46,
            downloadUrl = MOBILESAM_ENCODER_URL // Multi-file, handled specially in downloadToolModel
        ),
        ImageTool.LAMA_INPAINT to ToolModelSpec(
            id = "lama_dilated.mnn",
            displayName = "LaMa Inpainter",
            fileName = "lama_dilated.mnn",
            sizeMB = 93,
            downloadUrl = "" // Requires ONNX→MNN conversion; not yet hosted
        ),
        ImageTool.DEPTH to ToolModelSpec(
            id = "midas_small.mnn",
            displayName = "MiDaS Depth",
            fileName = "midas_small.mnn",
            sizeMB = 66,
            downloadUrl = "" // Requires ONNX→MNN conversion; not yet hosted
        ),
        ImageTool.STYLE_TRANSFER to ToolModelSpec(
            id = "style_transfer.mnn",
            displayName = "Style Transfer",
            fileName = "style_transfer.mnn",
            sizeMB = 7,
            downloadUrl = "" // Requires ONNX→MNN conversion; not yet hosted
        )
    )

    // ── State ──

    sealed class ProcessingState {
        object Idle : ProcessingState()
        object Loading : ProcessingState()
        object Processing : ProcessingState()
        data class Complete(val timeMs: Int = 0) : ProcessingState()
        data class Error(val message: String) : ProcessingState()
    }

    private val _selectedTool = MutableStateFlow(ImageTool.UPSCALER)
    val selectedTool: StateFlow<ImageTool> = _selectedTool.asStateFlow()

    private val _inputImage = MutableStateFlow<Bitmap?>(null)
    val inputImage: StateFlow<Bitmap?> = _inputImage.asStateFlow()

    private val _styleImage = MutableStateFlow<Bitmap?>(null)
    val styleImage: StateFlow<Bitmap?> = _styleImage.asStateFlow()

    private val _resultImage = MutableStateFlow<Bitmap?>(null)
    val resultImage: StateFlow<Bitmap?> = _resultImage.asStateFlow()

    private val _processingState = MutableStateFlow<ProcessingState>(ProcessingState.Idle)
    val processingState: StateFlow<ProcessingState> = _processingState.asStateFlow()

    private val _styleStrength = MutableStateFlow(1.0f)
    val styleStrength: StateFlow<Float> = _styleStrength.asStateFlow()

    private val _toolModelReady = MutableStateFlow<Map<ImageTool, Boolean>>(emptyMap())
    val toolModelReady: StateFlow<Map<ImageTool, Boolean>> = _toolModelReady.asStateFlow()

    private var diffusionEngine: DiffusionEngine? = null
    private var loadedTools = mutableSetOf<ImageTool>()

    init {
        checkInstalledModels()
    }

    // ── Public Actions ──

    fun selectTool(tool: ImageTool) {
        _selectedTool.value = tool
        _resultImage.value = null
        _processingState.value = ProcessingState.Idle
    }

    fun setInputImage(uri: Uri) {
        viewModelScope.launch {
            val bitmap = withContext(Dispatchers.IO) {
                try {
                    getApplication<Application>().contentResolver.openInputStream(uri)?.use {
                        BitmapFactory.decodeStream(it)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to decode image: ${e.message}")
                    null
                }
            }
            _inputImage.value = bitmap
            _resultImage.value = null
            _processingState.value = ProcessingState.Idle
        }
    }

    fun setStyleImage(uri: Uri) {
        viewModelScope.launch {
            val bitmap = withContext(Dispatchers.IO) {
                try {
                    getApplication<Application>().contentResolver.openInputStream(uri)?.use {
                        BitmapFactory.decodeStream(it)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to decode style image: ${e.message}")
                    null
                }
            }
            _styleImage.value = bitmap
        }
    }

    fun setStyleStrength(strength: Float) {
        _styleStrength.value = strength.coerceIn(0f, 1f)
    }

    fun clearImages() {
        _inputImage.value = null
        _styleImage.value = null
        _resultImage.value = null
        _processingState.value = ProcessingState.Idle
    }

    fun process() {
        val input = _inputImage.value ?: return
        val tool = _selectedTool.value

        viewModelScope.launch {
            _processingState.value = ProcessingState.Loading
            try {
                val engine = ensureEngine()
                if (!ensureToolLoaded(engine, tool)) {
                    _processingState.value = ProcessingState.Error("Failed to load tool model")
                    return@launch
                }

                _processingState.value = ProcessingState.Processing
                val startTime = System.currentTimeMillis()

                withContext(Dispatchers.IO) {
                    when (tool) {
                        ImageTool.UPSCALER -> {
                            engine.upscaleImage(input)
                            observeUpscale(engine, startTime)
                        }
                        ImageTool.DEPTH -> {
                            engine.estimateDepth(input)
                            observeDepth(engine, startTime)
                        }
                        ImageTool.LAMA_INPAINT -> {
                            // TODO: mask drawing UI — for now show error
                            _processingState.value = ProcessingState.Error("Draw a mask on the image first")
                        }
                        ImageTool.SEGMENTER -> {
                            engine.segmenterEncodeImage(input)
                            // Tap-to-segment handled separately via onImageTap
                            _processingState.value = ProcessingState.Complete(0)
                        }
                        ImageTool.STYLE_TRANSFER -> {
                            val style = _styleImage.value
                            if (style == null) {
                                _processingState.value = ProcessingState.Error("Pick a style image first")
                                return@withContext
                            }
                            engine.stylize(input, style, _styleStrength.value)
                            observeStyle(engine, startTime)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Processing failed: ${e.message}", e)
                _processingState.value = ProcessingState.Error(e.message ?: "Processing failed")
            }
        }
    }

    fun isModelDownloaded(tool: ImageTool): Boolean {
        return _toolModelReady.value[tool] == true
    }

    fun getToolModelSpec(tool: ImageTool): ToolModelSpec? = toolModelSpecs[tool]

    fun downloadToolModel(tool: ImageTool) {
        val spec = toolModelSpecs[tool] ?: return
        if (spec.downloadUrl.isEmpty()) {
            _processingState.value = ProcessingState.Error(
                "${spec.displayName} model not yet available — requires ONNX→MNN conversion"
            )
            return
        }

        val context = getApplication<Application>()

        if (tool == ImageTool.SEGMENTER) {
            // MobileSAM needs two separate file downloads: encoder + decoder
            startToolDownload(context, "mobilesam/encoder.mnn", "MobileSAM Encoder", MOBILESAM_ENCODER_URL)
            startToolDownload(context, "mobilesam/decoder.mnn", "MobileSAM Decoder", MOBILESAM_DECODER_URL)
        } else {
            startToolDownload(context, spec.id, spec.displayName, spec.downloadUrl)
        }
    }

    private fun startToolDownload(context: Application, modelId: String, name: String, url: String) {
        val intent = Intent(context, ModelDownloadService::class.java).apply {
            action = ModelDownloadService.ACTION_START_DOWNLOAD
            putExtra(ModelDownloadService.EXTRA_MODEL_ID, modelId)
            putExtra(ModelDownloadService.EXTRA_MODEL_NAME, name)
            putExtra(ModelDownloadService.EXTRA_FILE_URL, url)
            putExtra(ModelDownloadService.EXTRA_MODEL_TYPE, "IMAGE_TOOL")
            putExtra(ModelDownloadService.EXTRA_IS_ZIP, false)
        }
        androidx.core.content.ContextCompat.startForegroundService(context, intent)
    }

    fun releaseAll() {
        viewModelScope.launch(Dispatchers.IO) {
            diffusionEngine?.let { engine ->
                loadedTools.forEach { tool ->
                    try {
                        when (tool) {
                            ImageTool.UPSCALER -> engine.releaseUpscaler()
                            ImageTool.SEGMENTER -> engine.releaseSegmenter()
                            ImageTool.LAMA_INPAINT -> engine.releaseLamaInpainter()
                            ImageTool.DEPTH -> engine.releaseDepthEstimator()
                            ImageTool.STYLE_TRANSFER -> engine.releaseStyleTransfer()
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to release ${tool.name}: ${e.message}")
                    }
                }
                loadedTools.clear()
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        releaseAll()
    }

    // ── Private Helpers ──

    private fun checkInstalledModels() {
        val context = getApplication<Application>()
        val toolDir = AppPaths.imageTools(context)
        val ready = mutableMapOf<ImageTool, Boolean>()

        for ((tool, spec) in toolModelSpecs) {
            ready[tool] = if (tool == ImageTool.SEGMENTER) {
                // MobileSAM needs both encoder + decoder
                File(toolDir, "mobilesam/encoder.mnn").exists() &&
                        File(toolDir, "mobilesam/decoder.mnn").exists()
            } else {
                File(toolDir, spec.fileName).exists()
            }
        }
        _toolModelReady.value = ready
    }

    fun refreshModelAvailability() {
        checkInstalledModels()
    }

    private suspend fun ensureEngine(): DiffusionEngine {
        return diffusionEngine ?: run {
            val engine = DiffusionEngine()
            engine.init(getApplication(), safetyCheckerEnabled = false)
            diffusionEngine = engine
            engine
        }
    }

    private suspend fun ensureToolLoaded(engine: DiffusionEngine, tool: ImageTool): Boolean {
        if (tool in loadedTools) return true

        val context = getApplication<Application>()
        val toolDir = AppPaths.imageTools(context)

        return withContext(Dispatchers.IO) {
            try {
                val success = when (tool) {
                    ImageTool.UPSCALER -> {
                        val path = File(toolDir, "upscaler_4x.mnn").absolutePath
                        engine.loadUpscaler(path)
                    }
                    ImageTool.SEGMENTER -> {
                        val encoderPath = File(toolDir, "mobilesam/encoder.mnn").absolutePath
                        val decoderPath = File(toolDir, "mobilesam/decoder.mnn").absolutePath
                        engine.loadSegmenter(encoderPath, decoderPath)
                    }
                    ImageTool.LAMA_INPAINT -> {
                        val path = File(toolDir, "lama_dilated.mnn").absolutePath
                        engine.loadLamaInpainter(path)
                    }
                    ImageTool.DEPTH -> {
                        val path = File(toolDir, "midas_small.mnn").absolutePath
                        engine.loadDepthEstimator(path)
                    }
                    ImageTool.STYLE_TRANSFER -> {
                        val path = File(toolDir, "style_transfer.mnn").absolutePath
                        engine.loadStyleTransfer(path)
                    }
                }

                if (success) loadedTools.add(tool)
                success
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load tool ${tool.name}: ${e.message}", e)
                false
            }
        }
    }

    // ── State Observers ──

    private suspend fun observeUpscale(engine: DiffusionEngine, startTime: Long) {
        engine.upscaleState.collect { state ->
            when (state) {
                is UpscaleState.Complete -> {
                    val elapsed = (System.currentTimeMillis() - startTime).toInt()
                    _resultImage.value = state.bitmap
                    _processingState.value = ProcessingState.Complete(elapsed)
                    return@collect
                }
                is UpscaleState.Error -> {
                    _processingState.value = ProcessingState.Error(state.message)
                    return@collect
                }
                else -> {}
            }
        }
    }

    private suspend fun observeDepth(engine: DiffusionEngine, startTime: Long) {
        engine.depthState.collect { state ->
            when (state) {
                is DepthState.Complete -> {
                    val elapsed = (System.currentTimeMillis() - startTime).toInt()
                    _resultImage.value = state.depthMap
                    _processingState.value = ProcessingState.Complete(elapsed)
                    return@collect
                }
                is DepthState.Error -> {
                    _processingState.value = ProcessingState.Error(state.message)
                    return@collect
                }
                else -> {}
            }
        }
    }

    private suspend fun observeStyle(engine: DiffusionEngine, startTime: Long) {
        engine.styleState.collect { state ->
            when (state) {
                is StyleState.Complete -> {
                    val elapsed = (System.currentTimeMillis() - startTime).toInt()
                    _resultImage.value = state.bitmap
                    _processingState.value = ProcessingState.Complete(elapsed)
                    return@collect
                }
                is StyleState.Error -> {
                    _processingState.value = ProcessingState.Error(state.message)
                    return@collect
                }
                else -> {}
            }
        }
    }
}
