package com.dark.tool_neuron.viewmodel

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import androidx.core.graphics.scale
import java.io.File
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.dark.ai_sd.DiffusionBackendState
import com.dark.ai_sd.DiffusionGenerationParams
import com.dark.ai_sd.DiffusionGenerationState
import com.dark.ai_sd.RuntimeSetupState
import com.dark.ai_sd.UpscaleState
import com.dark.download_manager.HxdState
import com.dark.download_manager.HxdStatus
import com.dark.tool_neuron.model.ModelInfo
import com.dark.tool_neuron.model.enums.ProviderType
import com.dark.tool_neuron.repo.ImageGenManager
import com.dark.tool_neuron.repo.ModelRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

enum class ImageTaskMode { GENERATE, INPAINT, UPSCALE }

private data class ImageTaskInputs(
    val mode: ImageTaskMode = ImageTaskMode.GENERATE,
    val prompt: String = "",
    val negativePrompt: String = "",
    val steps: Int = 28,
    val cfg: Float = 7f,
    val seed: Long? = null,
    val width: Int = 512,
    val height: Int = 512,
    val denoiseStrength: Float = 0.6f,
    val schedulerKey: String = "dpm",
    val inputImagePath: String? = null,
    val maskImagePath: String? = null,
    val activeModelId: String = "",
    val activeUpscalerId: String = "",
    val localStatus: String = "",
    val localError: String? = null,
    val pendingLoadModelId: String = "",
    val pendingLoadModelName: String = "",
    val loadFailureReason: String? = null,
    val loadFailureModelId: String = "",
    val loadFailureModelName: String = "",
    val loadedDiffusionId: String = "",
    val loadedUpscalerId: String = "",
    /**
     * Resolutions the active model can run at (base size + every patch
     * file shipped alongside the .bin). Empty when no model is loaded.
     * Populated by [ImageTaskViewModel.setActiveModel] after a successful
     * load via [ImageGenManager.getSupportedResolutions].
     */
    val supportedResolutions: List<Pair<Int, Int>> = emptyList(),
)

enum class RuntimePhase { NEEDS_DOWNLOAD, DOWNLOADING, READY_TO_INITIALIZE, INITIALIZING, READY }

sealed class ModelLoadPhase {
    object Idle : ModelLoadPhase()
    data class Loading(val modelName: String) : ModelLoadPhase()
    data class Failed(val modelName: String, val reason: String) : ModelLoadPhase()
    data class Loaded(val modelName: String) : ModelLoadPhase()
}

data class ImageTaskUi(
    val mode: ImageTaskMode = ImageTaskMode.GENERATE,
    val prompt: String = "",
    val negativePrompt: String = "",
    val steps: Int = 28,
    val cfg: Float = 7f,
    val seed: Long? = null,
    val width: Int = 512,
    val height: Int = 512,
    val denoiseStrength: Float = 0.6f,
    val schedulerKey: String = "dpm",
    val inputImagePath: String? = null,
    val maskImagePath: String? = null,
    val hasMask: Boolean = false,
    val diffusionResultImage: Bitmap? = null,
    val upscaleResultImage: Bitmap? = null,
    val previewImage: Bitmap? = null,
    val progress: Float = 0f,
    val statusText: String = "",
    val errorText: String? = null,
    val installedDiffusionModels: List<ModelInfo> = emptyList(),
    val installedUpscalers: List<ModelInfo> = emptyList(),
    val activeModelId: String = "",
    val activeUpscalerId: String = "",
    val isBusy: Boolean = false,
    val runtimeReady: Boolean = false,
    val runtimePhase: RuntimePhase = RuntimePhase.NEEDS_DOWNLOAD,
    val runtimeDownloadProgress: Float = 0f,
    val runtimeDownloadBytes: Long = 0L,
    val runtimeDownloadTotal: Long = -1L,
    val modelLoadPhase: ModelLoadPhase = ModelLoadPhase.Idle,
    /**
     * Resolutions the active model can run at. Empty until a model is
     * loaded; the UI shows a "Pick a model first" hint in that state.
     * Driven by [ImageGenManager.getSupportedResolutions] which scans
     * for `.patch` files alongside the UNet binary.
     */
    val supportedResolutions: List<Pair<Int, Int>> = emptyList(),
)

@HiltViewModel
class ImageTaskViewModel @Inject constructor(
    application: Application,
    private val imageGen: ImageGenManager,
    private val modelRepo: ModelRepository,
) : AndroidViewModel(application) {

    private val TAG = "ImageTaskVM"

    private val inputs = MutableStateFlow(ImageTaskInputs())

    // Sticky preview cache. The SDK only fires intermediateImage on every Nth
    // step (showDiffusionStride) so most Progress emissions arrive with a null
    // bitmap. Without caching, the preview flickers between the last decoded
    // image and a blank slot. Holds onto the most recent non-null preview;
    // resets when generation starts / completes / errors.
    private val cachedPreview = MutableStateFlow<Bitmap?>(null)

    private val context: Context get() = getApplication()

    init {
        if (imageGen.isRuntimeArchivePresent()) {
            Log.d(TAG, "init: runtime archive present, auto-initializing")
            viewModelScope.launch {
                runCatching {
                    withContext(Dispatchers.Default) { imageGen.ensureRuntime() }
                }.onFailure { Log.e(TAG, "auto ensureRuntime threw", it) }
            }
        } else {
            Log.d(TAG, "init: runtime archive missing, user must download")
        }

        // Drive the sticky preview cache from generationState.
        //
        // (1) Updates only when a non-null intermediate arrives; clears on
        //     terminal states.
        // (2) ALWAYS copies the incoming bitmap. The SDK's worker thread can
        //     recycle / reuse its intermediate buffer between emissions, and
        //     Compose's `bitmap.asImageBitmap()` keeps a reference to the
        //     underlying pixel storage for as long as RenderThread is drawing
        //     it. A recycle-while-drawing race produces a SIGSEGV in
        //     RenderThread (observed: SEGV_MAPERR at a stale pointer mid-step).
        //     The copy gives us our own ref-counted Bitmap that the SDK can't
        //     pull out from under us.
        viewModelScope.launch {
            imageGen.generationState.collect { state ->
                when (state) {
                    is DiffusionGenerationState.Progress -> {
                        val incoming = state.intermediateImage ?: return@collect
                        val safe = runCatching {
                            incoming.copy(
                                incoming.config ?: Bitmap.Config.ARGB_8888,
                                false,
                            )
                        }.getOrNull() ?: return@collect
                        cachedPreview.value = safe
                    }
                    is DiffusionGenerationState.Complete,
                    is DiffusionGenerationState.Error,
                    is DiffusionGenerationState.Idle -> {
                        cachedPreview.value = null
                    }
                }
            }
        }
    }

    val ui: StateFlow<ImageTaskUi> = combine(
        inputs,
        modelRepo.models,
        imageGen.generationState,
        imageGen.runtimeSetupState,
        imageGen.upscaleState,
        imageGen.backendState,
        imageGen.runtimeDownload,
        cachedPreview,
    ) { values ->
        @Suppress("UNCHECKED_CAST")
        val i = values[0] as ImageTaskInputs
        @Suppress("UNCHECKED_CAST")
        val models = values[1] as List<ModelInfo>
        val gen = values[2] as DiffusionGenerationState
        val runtime = values[3] as RuntimeSetupState
        val upscale = values[4] as UpscaleState
        val backend = values[5] as DiffusionBackendState
        val runtimeDl = values[6] as HxdState?
        val sticky = values[7] as Bitmap?

        val archivePresent = imageGen.isRuntimeArchivePresent()
        val runtimeReady = runtime is RuntimeSetupState.Complete
        val runtimePhase = when {
            runtimeReady -> RuntimePhase.READY
            runtime is RuntimeSetupState.CopyingAsset
                || runtime is RuntimeSetupState.Extracting
                || runtime is RuntimeSetupState.CopyingSafetyChecker
                || runtime is RuntimeSetupState.InitializingRuntime -> RuntimePhase.INITIALIZING
            archivePresent -> RuntimePhase.READY_TO_INITIALIZE
            runtimeDl != null && runtimeDl.status in setOf(
                HxdStatus.QUEUED, HxdStatus.CONNECTING, HxdStatus.DOWNLOADING, HxdStatus.PAUSED,
            ) -> RuntimePhase.DOWNLOADING
            else -> RuntimePhase.NEEDS_DOWNLOAD
        }
        val dlProgress = runtimeDl?.let {
            if (it.totalBytes > 0) it.downloadedBytes.toFloat() / it.totalBytes else 0f
        } ?: 0f

        val modelLoadPhase: ModelLoadPhase = when {
            i.pendingLoadModelId.isNotBlank() -> ModelLoadPhase.Loading(i.pendingLoadModelName)
            backend is DiffusionBackendState.Starting && i.activeModelId.isNotBlank() -> {
                val name = models.firstOrNull { it.id == i.activeModelId }?.name ?: i.activeModelId
                ModelLoadPhase.Loading(name)
            }
            i.loadFailureReason != null -> ModelLoadPhase.Failed(
                modelName = i.loadFailureModelName.ifBlank { i.loadFailureModelId },
                reason = i.loadFailureReason,
            )
            i.mode == ImageTaskMode.UPSCALE && i.loadedUpscalerId.isNotBlank() -> {
                val name = models.firstOrNull { it.id == i.loadedUpscalerId }?.name ?: i.loadedUpscalerId
                ModelLoadPhase.Loaded(name)
            }
            i.mode != ImageTaskMode.UPSCALE && i.loadedDiffusionId.isNotBlank() -> {
                val name = models.firstOrNull { it.id == i.loadedDiffusionId }?.name ?: i.loadedDiffusionId
                ModelLoadPhase.Loaded(name)
            }
            else -> ModelLoadPhase.Idle
        }

        val sdkProgress = (gen as? DiffusionGenerationState.Progress)?.progress ?: 0f
        val diffusionResult = (gen as? DiffusionGenerationState.Complete)?.bitmap
        val upscaleResult = (upscale as? UpscaleState.Complete)?.bitmap

        val sdkError = (gen as? DiffusionGenerationState.Error)?.message
            ?: (upscale as? UpscaleState.Error)?.message
            ?: (backend as? DiffusionBackendState.Error)?.message
            ?: (runtime as? RuntimeSetupState.Error)?.message

        ImageTaskUi(
            mode = i.mode,
            prompt = i.prompt,
            negativePrompt = i.negativePrompt,
            steps = i.steps,
            cfg = i.cfg,
            seed = i.seed,
            width = i.width,
            height = i.height,
            supportedResolutions = i.supportedResolutions,
            denoiseStrength = i.denoiseStrength,
            schedulerKey = i.schedulerKey,
            inputImagePath = i.inputImagePath,
            maskImagePath = i.maskImagePath,
            hasMask = !i.maskImagePath.isNullOrBlank(),
            installedDiffusionModels = models.filter { it.providerType == ProviderType.IMAGE_GEN },
            installedUpscalers = models.filter { it.providerType == ProviderType.IMAGE_UPSCALER },
            activeModelId = i.activeModelId,
            activeUpscalerId = i.activeUpscalerId,
            diffusionResultImage = diffusionResult,
            upscaleResultImage = upscaleResult,
            previewImage = sticky,
            progress = sdkProgress,
            runtimeReady = runtime is RuntimeSetupState.Complete,
            statusText = composeStatus(i, gen, runtime, upscale, backend),
            errorText = sdkError ?: i.localError,
            isBusy = gen is DiffusionGenerationState.Progress
                || upscale is UpscaleState.Processing
                || runtime is RuntimeSetupState.CopyingAsset
                || runtime is RuntimeSetupState.Extracting
                || runtime is RuntimeSetupState.InitializingRuntime
                || backend is DiffusionBackendState.Starting
                || i.pendingLoadModelId.isNotBlank(),
            runtimePhase = runtimePhase,
            runtimeDownloadProgress = dlProgress,
            runtimeDownloadBytes = runtimeDl?.downloadedBytes ?: 0L,
            runtimeDownloadTotal = runtimeDl?.totalBytes ?: -1L,
            modelLoadPhase = modelLoadPhase,
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, ImageTaskUi())

    fun setMode(mode: ImageTaskMode) {
        inputs.update { it.copy(mode = mode, localError = null) }
    }

    fun setPrompt(value: String) = inputs.update { it.copy(prompt = value) }
    fun setNegativePrompt(value: String) = inputs.update { it.copy(negativePrompt = value) }
    fun setSteps(value: Int) = inputs.update { it.copy(steps = value.coerceIn(1, 50)) }
    fun setCfg(value: Float) = inputs.update { it.copy(cfg = value.coerceIn(1f, 15f)) }
    /**
     * Pick a (width, height) the active model supports. Width/height are
     * accepted as separate ints so the call site in Compose can stay
     * obvious; the ViewModel snaps them to the closest supported pair if
     * the model exposes one, and falls back to the requested values when
     * no model is loaded yet.
     */
    fun setResolution(width: Int, height: Int) {
        inputs.update {
            val supported = it.supportedResolutions
            val (w, h) = if (supported.isEmpty()) {
                width to height
            } else {
                supported.firstOrNull { (sw, sh) -> sw == width && sh == height }
                    ?: supported.first()
            }
            it.copy(width = w, height = h)
        }
    }
    fun setScheduler(key: String) = inputs.update { it.copy(schedulerKey = key) }
    fun setDenoiseStrength(value: Float) =
        inputs.update { it.copy(denoiseStrength = value.coerceIn(0f, 1f)) }

    fun setInputImage(uri: Uri) {
        inputs.update {
            it.copy(
                inputImagePath = uri.toString(),
                maskImagePath = null,
            )
        }
    }

    fun setMaskPath(path: String?) {
        inputs.update { it.copy(maskImagePath = path) }
    }

    suspend fun loadInputBitmap(): Bitmap? {
        val path = inputs.value.inputImagePath ?: return null
        return withContext(Dispatchers.IO) { decodeBitmap(path) }
    }

    fun upscaleResultBitmap(bitmap: Bitmap) {
        Log.d(TAG, "upscaleResultBitmap ${bitmap.width}x${bitmap.height}")
        val s = inputs.value
        if (s.loadedUpscalerId.isBlank()) {
            val installedUpscalers = modelRepo.models.value
                .count { it.providerType == ProviderType.IMAGE_UPSCALER }
            viewModelScope.launch(Dispatchers.IO) {
                val path = saveBitmapToCache(bitmap)
                inputs.update {
                    it.copy(
                        mode = ImageTaskMode.UPSCALE,
                        inputImagePath = path,
                        maskImagePath = null,
                        localError = if (installedUpscalers == 0)
                            "Install an upscaler from the Store first"
                        else "Pick an upscaler — then tap Upscale 4×",
                    )
                }
            }
            return
        }
        val maxEdge = maxOf(bitmap.width, bitmap.height)
        val targetEdge = maxEdge.coerceAtMost(1024)
        val safe = if (maxEdge == targetEdge) bitmap else {
            val scale = targetEdge.toFloat() / maxEdge.toFloat()
            bitmap.scale(
                (bitmap.width * scale).toInt(),
                (bitmap.height * scale).toInt(),
            )
        }
        viewModelScope.launch(Dispatchers.IO) {
            val path = saveBitmapToCache(bitmap)
            inputs.update {
                it.copy(
                    mode = ImageTaskMode.UPSCALE,
                    inputImagePath = path,
                    maskImagePath = null,
                    localError = null,
                )
            }
            imageGen.upscale(safe)
        }
    }

    private fun saveBitmapToCache(bitmap: Bitmap): String {
        val file = File(getApplication<Application>().cacheDir, "tn_image_${System.currentTimeMillis()}.png")
        file.outputStream().use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        return file.absolutePath
    }

    fun setActiveModel(modelId: String) {
        Log.d(TAG, "setActiveModel id=$modelId")
        if (!imageGen.isRuntimeArchivePresent()) {
            inputs.update { it.copy(localError = "Download the runtime first") }
            return
        }
        val model = modelRepo.models.value.firstOrNull {
            it.id == modelId && it.providerType == ProviderType.IMAGE_GEN
        } ?: run {
            Log.w(TAG, "setActiveModel: no model with id=$modelId in repo")
            inputs.update { it.copy(localError = "Model $modelId not installed") }
            return
        }
        inputs.update {
            it.copy(
                activeModelId = modelId,
                pendingLoadModelId = modelId,
                pendingLoadModelName = model.name,
                loadFailureReason = null,
                loadFailureModelId = "",
                loadFailureModelName = "",
                localStatus = "Loading model…",
                localError = null,
            )
        }
        viewModelScope.launch {
            val loadResult = withContext(Dispatchers.Default) {
                runCatching {
                    val (w, h) = inputs.value.let { it.width to it.height }
                    imageGen.loadDiffusionModel(model, w, h)
                }
            }
            val ok = loadResult.getOrDefault(false)
            val throwable = loadResult.exceptionOrNull()
            if (throwable != null) Log.e(TAG, "loadDiffusionModel threw", throwable)

            // Probe the model dir for supported resolutions only after a
            // successful load. The probe is a pure filesystem scan so it
            // can't fail catastrophically — we still wrap it for safety.
            val supported: List<Pair<Int, Int>> = if (ok) {
                runCatching {
                    withContext(Dispatchers.Default) {
                        imageGen.getSupportedResolutions(model)
                    }
                }.getOrElse {
                    Log.w(TAG, "getSupportedResolutions threw", it)
                    emptyList()
                }
            } else emptyList()

            inputs.update {
                if (ok) {
                    val (snappedW, snappedH) = snapToSupported(it.width, it.height, supported)
                    it.copy(
                        width = snappedW,
                        height = snappedH,
                        supportedResolutions = supported,
                        pendingLoadModelId = "",
                        pendingLoadModelName = "",
                        loadedDiffusionId = modelId,
                        loadFailureReason = null,
                        loadFailureModelId = "",
                        loadFailureModelName = "",
                        localStatus = "Model ready: ${model.name}",
                        localError = null,
                    )
                } else it.copy(
                    pendingLoadModelId = "",
                    pendingLoadModelName = "",
                    loadedDiffusionId = "",
                    activeModelId = "",
                    supportedResolutions = emptyList(),
                    loadFailureReason = throwable?.message
                        ?: "Could not load ${model.name}. The model may need a different runtime variant or your device's NPU bucket doesn't match.",
                    loadFailureModelId = modelId,
                    loadFailureModelName = model.name,
                    localStatus = "",
                    localError = null,
                )
            }
            if (ok) Log.d(TAG, "supportedResolutions=$supported")
        }
    }

    /**
     * If the current (w, h) is in [supported], keep it. Otherwise return
     * the smallest supported pair so the user always lands on a valid
     * resolution after a model swap. When [supported] is empty, no
     * filtering — caller's pair is preserved.
     */
    private fun snapToSupported(
        w: Int, h: Int,
        supported: List<Pair<Int, Int>>,
    ): Pair<Int, Int> {
        if (supported.isEmpty()) return w to h
        return supported.firstOrNull { it.first == w && it.second == h }
            ?: supported.first()
    }

    fun retryLoadActiveModel() {
        val failedId = inputs.value.loadFailureModelId
        if (failedId.isBlank()) return
        setActiveModel(failedId)
    }

    fun dismissLoadFailure() {
        inputs.update {
            it.copy(
                loadFailureReason = null,
                loadFailureModelId = "",
                loadFailureModelName = "",
            )
        }
    }

    fun setActiveUpscaler(modelId: String) {
        Log.d(TAG, "setActiveUpscaler id=$modelId")
        val model = modelRepo.models.value.firstOrNull {
            it.id == modelId && it.providerType == ProviderType.IMAGE_UPSCALER
        } ?: run {
            inputs.update { it.copy(localError = "Upscaler $modelId not installed") }
            return
        }
        inputs.update {
            it.copy(
                activeUpscalerId = modelId,
                pendingLoadModelId = modelId,
                pendingLoadModelName = model.name,
                loadFailureReason = null,
                loadFailureModelId = "",
                loadFailureModelName = "",
                localStatus = "Loading upscaler…",
                localError = null,
            )
        }
        viewModelScope.launch {
            val result = withContext(Dispatchers.Default) {
                runCatching { imageGen.loadUpscaler(model) }
            }
            val ok = result.getOrDefault(false)
            val throwable = result.exceptionOrNull()
            if (throwable != null) Log.e(TAG, "loadUpscaler threw", throwable)

            inputs.update {
                if (ok) it.copy(
                    pendingLoadModelId = "",
                    pendingLoadModelName = "",
                    loadedUpscalerId = modelId,
                    loadFailureReason = null,
                    loadFailureModelId = "",
                    loadFailureModelName = "",
                    localStatus = "Upscaler ready: ${model.name}",
                    localError = null,
                ) else it.copy(
                    pendingLoadModelId = "",
                    pendingLoadModelName = "",
                    loadedUpscalerId = "",
                    activeUpscalerId = "",
                    loadFailureReason = throwable?.message
                        ?: "Could not load ${model.name}",
                    loadFailureModelId = modelId,
                    loadFailureModelName = model.name,
                    localStatus = "",
                    localError = null,
                )
            }
        }
    }

    fun run() {
        Log.d(TAG, "run mode=${inputs.value.mode}")
        when (inputs.value.mode) {
            ImageTaskMode.GENERATE -> runDiffusion()
            ImageTaskMode.INPAINT -> runDiffusion()
            ImageTaskMode.UPSCALE -> runUpscale()
        }
    }

    fun cancel() {
        Log.d(TAG, "cancel")
        imageGen.cancelGeneration()
    }

    fun startRuntimeDownload() {
        Log.d(TAG, "startRuntimeDownload")
        val id = imageGen.downloadRuntime() ?: return
        viewModelScope.launch {
            imageGen.observeRuntimeDownload(id).collect { state ->
                if (state != null) {
                    imageGen.pushRuntimeDownloadState(state)
                    if (state.status == HxdStatus.COMPLETED) {
                        Log.d(TAG, "runtime download complete — auto-initializing")
                        runCatching {
                            withContext(Dispatchers.Default) { imageGen.ensureRuntime() }
                        }.onFailure { Log.e(TAG, "post-download ensureRuntime threw", it) }
                    }
                }
            }
        }
    }

    fun cancelRuntimeDownload() {
        imageGen.cancelRuntimeDownload()
    }

    fun initializeRuntime() {
        Log.d(TAG, "initializeRuntime")
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.Default) { imageGen.ensureRuntime() }
            }.onFailure { Log.e(TAG, "ensureRuntime threw", it) }
        }
    }

    private fun runDiffusion() {
        val s = inputs.value
        if (s.activeModelId.isBlank()) {
            inputs.update { it.copy(localError = "Pick an image model first") }
            return
        }
        if (s.loadedDiffusionId != s.activeModelId) {
            inputs.update { it.copy(localError = "Model is still loading — wait for it to finish") }
            return
        }
        if (s.prompt.isBlank()) {
            inputs.update { it.copy(localError = "Type a prompt first") }
            return
        }
        // Defensive: if the user somehow ended up with a (w, h) the model
        // doesn't support (e.g. a state migration after the model swapped),
        // refuse rather than letting the SDK silently denoise a partial
        // latent and emit pure noise — that's the bug we shipped this
        // pipeline to make impossible.
        if (s.supportedResolutions.isNotEmpty() &&
            s.supportedResolutions.none { it.first == s.width && it.second == s.height }) {
            inputs.update {
                it.copy(
                    localError = "Resolution ${s.width}×${s.height} isn't supported by this " +
                        "model. Pick one from the resolution chips."
                )
            }
            return
        }
        val params = DiffusionGenerationParams(
            prompt = s.prompt,
            negativePrompt = s.negativePrompt,
            steps = s.steps,
            cfgScale = s.cfg,
            seed = s.seed,
            width = s.width,
            height = s.height,
            scheduler = s.schedulerKey,
            useOpenCL = false,
            inputImage = if (s.mode == ImageTaskMode.INPAINT) s.inputImagePath else null,
            mask = if (s.mode == ImageTaskMode.INPAINT) s.maskImagePath else null,
            denoiseStrength = s.denoiseStrength,
            showDiffusionProcess = true,
            showDiffusionStride = 4,
        )
        Log.d(TAG, "generateImage steps=${params.steps} res=${params.width}x${params.height} sched=${params.scheduler}")
        inputs.update { it.copy(localError = null) }
        imageGen.generate(params)
    }

    private fun runUpscale() {
        val s = inputs.value
        if (s.activeUpscalerId.isBlank()) {
            inputs.update { it.copy(localError = "Pick an upscaler first") }
            return
        }
        if (s.loadedUpscalerId != s.activeUpscalerId) {
            inputs.update { it.copy(localError = "Upscaler is still loading — wait for it to finish") }
            return
        }
        val path = s.inputImagePath
        if (path.isNullOrBlank()) {
            inputs.update { it.copy(localError = "Pick an input image first") }
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            val bitmap = decodeBitmap(path) ?: run {
                inputs.update { it.copy(localError = "Couldn't read input image") }
                return@launch
            }
            // Cap input at 1024 max-edge: 4× output = 4096² ≈ 64 MB which fits
            // the default JVM heap. The SDK can technically take 2048² but that
            // produces an 8192² ≈ 256 MB bitmap which OOMs the heap on result
            // delivery (bitmap allocation in DiffusionManager.createBitmapFromRgb).
            val maxEdge = maxOf(bitmap.width, bitmap.height)
            val targetEdge = maxEdge.coerceAtMost(1024)
            val safe = if (maxEdge == targetEdge) bitmap else {
                val scale = targetEdge.toFloat() / maxEdge.toFloat()
                bitmap.scale(
                    (bitmap.width * scale).toInt(),
                    (bitmap.height * scale).toInt(),
                )
            }
            inputs.update { it.copy(localError = null) }
            imageGen.upscale(safe)
        }
    }

    private fun decodeBitmap(path: String): Bitmap? = try {
        if (path.startsWith("content://") || path.startsWith("file://")) {
            context.contentResolver.openInputStream(Uri.parse(path))
                ?.use { BitmapFactory.decodeStream(it) }
        } else {
            BitmapFactory.decodeFile(path)
        }
    } catch (_: Throwable) {
        null
    }

    private fun composeStatus(
        i: ImageTaskInputs,
        gen: DiffusionGenerationState,
        runtime: RuntimeSetupState,
        upscale: UpscaleState,
        backend: DiffusionBackendState,
    ): String = when {
        runtime is RuntimeSetupState.CopyingAsset -> "Setting up runtime: copying assets…"
        runtime is RuntimeSetupState.Extracting -> "Setting up runtime: ${runtime.currentFile}"
        runtime is RuntimeSetupState.CopyingSafetyChecker -> "Setting up runtime: safety checker"
        runtime is RuntimeSetupState.InitializingRuntime -> "Initializing native runtime…"
        backend is DiffusionBackendState.Starting -> "Loading model…"
        gen is DiffusionGenerationState.Progress ->
            "Step ${gen.currentStep}/${gen.totalSteps} (${"%.0f".format(gen.progress * 100)}%)"
        gen is DiffusionGenerationState.Complete -> "Done"
        upscale is UpscaleState.Processing -> "Upscaling…"
        upscale is UpscaleState.Complete -> "Upscale done"
        else -> i.localStatus
    }
}

private inline fun <T> MutableStateFlow<T>.update(transform: (T) -> T) {
    value = transform(value)
}
