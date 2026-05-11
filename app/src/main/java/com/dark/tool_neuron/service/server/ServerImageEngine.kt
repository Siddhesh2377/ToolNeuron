package com.dark.tool_neuron.service.server

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.dark.ai_sd.DiffusionGenerationParams
import com.dark.ai_sd.DiffusionGenerationResult
import com.dark.ai_sd.DiffusionModelConfig
import com.dark.ai_sd.DiffusionRuntimeConfig
import com.dark.ai_sd.StableDiffusionManager
import com.dark.ai_sd.UpscaleState
import com.dark.tool_neuron.data.SocBucket
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.io.FileOutputStream

class ServerImageEngine(private val app: Context) {

    private val sdk by lazy { StableDiffusionManager.getInstance(app) }
    private val lock = Mutex()
    @Volatile private var initialized = false
    @Volatile private var loadedDiffusionId: String = ""
    @Volatile private var loadedUpscalerPath: String = ""

    suspend fun ensureRuntime(): Boolean = lock.withLock {
        if (initialized) return@withLock true
        val archive = File(app.filesDir, "$RUNTIME_DIR/qnnlibs.tar.xz")
        if (!archive.exists() || archive.length() < 1_000_000L) return@withLock false
        File(app.filesDir, RUNTIME_DIR).mkdirs()
        try {
            sdk.initialize(
                DiffusionRuntimeConfig(
                    runtimeDir = RUNTIME_DIR,
                    tarXzSourcePath = archive.absolutePath,
                ),
            )
            initialized = true
            true
        } catch (_: Exception) {
            false
        }
    }

    suspend fun loadDiffusion(modelId: String, modelName: String, modelPath: String, width: Int, height: Int): Boolean {
        if (!ensureRuntime()) return false
        if (loadedDiffusionId == modelId && sdk.isBackendRunning()) return true
        val effective = liftToModelDir(File(modelPath)).absolutePath
        val cfg = DiffusionModelConfig(
            name = modelName,
            modelDir = effective,
            textEmbeddingSize = 768,
            runOnCpu = !SocBucket.supportsNpu(),
            useCpuClip = SocBucket.supportsNpu(),
            isPony = false,
            safetyMode = false,
        )
        val ok = sdk.loadModel(cfg, width, height)
        if (ok) loadedDiffusionId = modelId
        return ok
    }

    suspend fun loadUpscaler(modelId: String, modelPath: String): Boolean {
        if (!ensureRuntime()) return false
        if (loadedUpscalerPath == modelPath) return true
        val isMnn = modelPath.endsWith(".mnn", ignoreCase = true)
        val ok = sdk.loadUpscaler(modelPath = modelPath, useMnn = isMnn, useOpenCL = !isMnn)
        if (ok) loadedUpscalerPath = modelPath
        return ok
    }

    suspend fun generate(params: DiffusionGenerationParams): DiffusionGenerationResult =
        sdk.generateImageSync(params)

    suspend fun upscale(bitmap: Bitmap): Bitmap? {
        sdk.upscaleImage(bitmap)
        val state = sdk.upscaleState.first { it is UpscaleState.Complete || it is UpscaleState.Error }
        return when (state) {
            is UpscaleState.Complete -> state.bitmap
            is UpscaleState.Error    -> null
            else                     -> null
        }
    }

    fun shutdown() {
        try { sdk.stopBackend() } catch (_: Exception) {}
        loadedDiffusionId = ""
        loadedUpscalerPath = ""
    }

    fun writePng(path: String, bitmap: Bitmap): Boolean {
        return try {
            FileOutputStream(path).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            true
        } catch (_: Exception) {
            false
        }
    }

    fun loadBitmap(path: String): Bitmap? = try {
        BitmapFactory.decodeFile(path)
    } catch (_: Exception) {
        null
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

    companion object {
        private const val RUNTIME_DIR = "ai_sd_runtime"
    }
}
