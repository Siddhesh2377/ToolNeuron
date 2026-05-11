package com.dark.tool_neuron.repo

import android.content.Context
import android.graphics.Bitmap
import com.dark.ai_sd.DiffusionBackendState
import com.dark.ai_sd.DiffusionGenerationParams
import com.dark.ai_sd.DiffusionGenerationState
import com.dark.ai_sd.DiffusionModelConfig
import com.dark.ai_sd.DiffusionRuntimeConfig
import com.dark.ai_sd.RuntimeSetupState
import com.dark.ai_sd.StableDiffusionManager
import com.dark.ai_sd.UpscaleState
import com.dark.download_manager.HxdManager
import com.dark.download_manager.HxdState
import com.dark.download_manager.HxdStatus
import com.dark.tool_neuron.data.SocBucket
import com.dark.tool_neuron.model.ModelInfo
import com.dark.tool_neuron.model.enums.ProviderType
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ImageGenManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val sdk by lazy { StableDiffusionManager.getInstance(context) }
    private val initLock = Mutex()
    @Volatile private var initialized = false
    @Volatile private var loadedModelId: String = ""

    private val _runtimeDownload = MutableStateFlow<HxdState?>(null)
    val runtimeDownload: StateFlow<HxdState?> = _runtimeDownload.asStateFlow()

    fun runtimeArchiveFile(): File =
        File(context.filesDir, "$RUNTIME_DIR_NAME/qnnlibs.tar.xz").apply { parentFile?.mkdirs() }

    fun isRuntimeArchivePresent(): Boolean {
        val f = runtimeArchiveFile()
        return f.exists() && f.length() > 1_000_000L
    }

    fun downloadRuntime(): Int? {
        if (isRuntimeArchivePresent()) return null
        val current = _runtimeDownload.value
        if (current != null && current.status in setOf(HxdStatus.QUEUED, HxdStatus.CONNECTING, HxdStatus.DOWNLOADING)) {
            return current.id
        }
        val dest = runtimeArchiveFile()
        if (dest.exists()) dest.delete()
        val id = HxdManager.enqueue(context, RUNTIME_ARCHIVE_URL, dest.absolutePath)
        _runtimeDownload.value = HxdState(
            id = id, url = RUNTIME_ARCHIVE_URL, destPath = dest.absolutePath,
            downloadedBytes = 0L, totalBytes = -1L, speedBps = 0L, status = HxdStatus.QUEUED,
        )
        return id
    }

    fun observeRuntimeDownload(id: Int) = HxdManager.observe(id)

    fun cancelRuntimeDownload() {
        _runtimeDownload.value?.let { HxdManager.cancel(it.id) }
        _runtimeDownload.value = null
    }

    fun pushRuntimeDownloadState(state: HxdState) {
        _runtimeDownload.value = state
    }

    companion object {
        const val RUNTIME_DIR_NAME = "ai_sd_runtime"
        const val RUNTIME_ARCHIVE_URL =
            "https://huggingface.co/Void2377/toon-neuron-storage/resolve/main/qnnlibs.tar.xz?download=true"
    }

    val backendState: StateFlow<DiffusionBackendState> get() = sdk.diffusionBackendState
    val generationState: StateFlow<DiffusionGenerationState> get() = sdk.diffusionGenerationState
    val isGenerating: StateFlow<Boolean> get() = sdk.isGenerating
    val upscaleState: StateFlow<UpscaleState> get() = sdk.upscaleState
    val runtimeSetupState: StateFlow<RuntimeSetupState> get() = sdk.runtimeSetupState

    suspend fun ensureRuntime() {
        initLock.withLock {
            if (initialized) return@withLock
            val archive = runtimeArchiveFile()
            check(archive.exists() && archive.length() > 1_000_000L) {
                "Runtime archive missing — call downloadRuntime() first"
            }
            File(context.filesDir, RUNTIME_DIR_NAME).mkdirs()
            sdk.initialize(
                DiffusionRuntimeConfig(
                    runtimeDir = RUNTIME_DIR_NAME,
                    tarXzSourcePath = archive.absolutePath,
                ),
            )
            initialized = true
        }
    }

    suspend fun loadDiffusionModel(model: ModelInfo, width: Int, height: Int): Boolean {
        ensureRuntime()
        if (loadedModelId == model.id) return sdk.isBackendRunning()
        val runOnCpu = !SocBucket.supportsNpu()
        val isSdxl = looksSdxl(model)
        val effectivePath = liftToModelDir(File(model.path)).absolutePath
        val cfg = DiffusionModelConfig(
            name = model.name,
            modelDir = effectivePath,
            textEmbeddingSize = if (isSdxl) 768 else 768,
            runOnCpu = runOnCpu,
            useCpuClip = !runOnCpu,
            isPony = false,
            safetyMode = false,
        )
        val ok = sdk.loadModel(cfg, width, height)
        if (ok) loadedModelId = model.id
        return ok
    }

    /**
     * Walk up from the model's installed path to the directory the SDK
     * actually loads from (the one holding `unet.bin`, `clip.mnn`, …).
     * Useful for callers that need to inspect on-disk artifacts (e.g.
     * patch-file enumeration) without going through the SDK load path.
     */
    fun resolveModelDir(model: ModelInfo): String =
        liftToModelDir(File(model.path)).absolutePath

    /**
     * Resolutions the loaded model can actually run at: the UNet's baked
     * base size + every `.patch` file found alongside it. xororz / Mr-J-369
     * directory layout encodes the base size in the parent dir name
     * (`output_512`, `output_768`, …); we detect that here so callers
     * don't have to guess. Falls back to 512×512 when no `output_<N>`
     * marker is on the path.
     *
     * Picking a resolution NOT in this list is what produces the silent
     * noise output we see when 1024² is requested on a `min` variant —
     * use this list to gate the UI.
     */
    fun getSupportedResolutions(model: ModelInfo): List<Pair<Int, Int>> {
        val dir = liftToModelDir(File(model.path))
        val (baseW, baseH) = inferBaseResolution(dir)
        return sdk.getSupportedResolutions(
            modelDir = dir.absolutePath,
            baseWidth = baseW,
            baseHeight = baseH,
        )
    }

    private fun inferBaseResolution(modelDir: File): Pair<Int, Int> {
        // xororz / Mr-J-369 encode base size as `output_<N>` in the dir
        // chain — usually one level above qnn_models_<variant>/.
        val pattern = Regex("output_(\\d+)")
        var cur: File? = modelDir
        repeat(4) {
            val match = cur?.name?.let { pattern.matchEntire(it) }
            if (match != null) {
                val n = match.groupValues[1].toIntOrNull()
                if (n != null) return n to n
            }
            cur = cur?.parentFile
        }
        return 512 to 512
    }

    private fun liftToModelDir(root: File): File {
        val signals = setOf(
            "unet.bin", "unet.mnn", "clip.mnn", "clip_v2.mnn",
            "vae_decoder.bin", "vae_decoder.mnn", "tokenizer.json",
        )
        var cur = root
        repeat(6) {
            val files = cur.listFiles()?.toList().orEmpty()
            val hasModelFile = files.any { it.isFile && it.name in signals }
            if (hasModelFile) return cur
            val onlyDir = files.singleOrNull { it.isDirectory && !it.name.startsWith(".") }
                ?: return cur
            cur = onlyDir
        }
        return cur
    }

    suspend fun loadUpscaler(model: ModelInfo): Boolean {
        ensureRuntime()
        val isMnnFile = model.path.endsWith(".mnn", ignoreCase = true)
        // QNN .bin paths now work standalone — the AAR's nativeLoadUpscaler
        // calls sd_pipeline::loadStandaloneQnnUpscaler which builds the
        // QnnModel + initializeQnnApp inline (mirrors LocalDream's per-request
        // /upscale handler).
        return sdk.loadUpscaler(
            modelPath = model.path,
            useMnn = isMnnFile,
            useOpenCL = !isMnnFile,
        )
    }

    fun generate(params: DiffusionGenerationParams) {
        sdk.generateImage(params)
    }

    fun cancelGeneration() {
        sdk.cancelGeneration()
    }

    fun resetGenerationState() {
        sdk.resetGenerationState()
    }

    fun upscale(bitmap: Bitmap) {
        sdk.upscaleImage(bitmap)
    }

    fun stop() {
        sdk.stopBackend()
        loadedModelId = ""
    }

    fun cleanup() {
        sdk.cleanup()
        loadedModelId = ""
    }

    fun isBackendRunning(): Boolean = sdk.isBackendRunning()

    fun activeModelId(): String = loadedModelId

    fun supportsImageGen(): Boolean = true

    fun installedDiffusionModels(allModels: List<ModelInfo>): List<ModelInfo> =
        allModels.filter { it.providerType == ProviderType.IMAGE_GEN }

    fun installedUpscalers(allModels: List<ModelInfo>): List<ModelInfo> =
        allModels.filter { it.providerType == ProviderType.IMAGE_UPSCALER }

    private fun looksSdxl(model: ModelInfo): Boolean =
        model.id.contains("sdxl", ignoreCase = true) ||
            model.id.contains("illustrious", ignoreCase = true)
}
