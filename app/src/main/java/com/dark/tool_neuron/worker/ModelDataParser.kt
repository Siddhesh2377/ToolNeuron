package com.dark.tool_neuron.worker

import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import com.dark.tool_neuron.engine.GGUFEngine
import com.dark.tool_neuron.global.formatNumber
import com.dark.tool_neuron.models.enums.ProviderType
import com.dark.tool_neuron.models.table_schema.Model
import com.dark.tool_neuron.models.table_schema.ModelConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import android.os.ParcelFileDescriptor
import java.io.File
import java.io.InputStream
import java.security.MessageDigest

/**
 * Handles parsing and loading of different model formats
 */
class ModelDataParser {

    suspend fun loadModel(
        model: Model, config: ModelConfig?
    ): ModelLoadResult = withContext(Dispatchers.IO) {
        return@withContext when (model.providerType) {
            ProviderType.GGUF -> loadGGUFModel(model, config)
            ProviderType.DIFFUSION -> loadDiffusionModel(model, config)
            else -> ModelLoadResult.Error("Unsupported model type: ${model.providerType}")
        }
    }

    /**
     * Load GGUF model from content:// URI using file descriptor
     */
    suspend fun loadModelFromUri(
        context: Context,
        uri: Uri,
        modelName: String,
        config: ModelConfig?
    ): ModelLoadResult = withContext(Dispatchers.IO) {
        try {
            val engine = GGUFEngine()

            // Get FD from content resolver
            val pfd = context.contentResolver.openFileDescriptor(uri, "r")
                ?: return@withContext ModelLoadResult.Error("Cannot open file descriptor for URI")

            val fd = pfd.detachFd()  // Detach so engine owns the fd
            pfd.close()              // Close the PFD wrapper (no-op after detach, but tidy)

            val success = try {
                engine.loadFromFd(fd, config)
            } catch (e: Exception) {
                closeFdSafely(fd)
                throw e
            }

            if (success) {
                // Engine now owns fd — do not close it
                val infoJson = engine.getModelInfo()
                if (infoJson != null) {
                    val modelInfo = parseGGUFInfo(infoJson)
                    ModelLoadResult.Success(info = modelInfo, engine = engine)
                } else {
                    ModelLoadResult.Error("Failed to retrieve model information")
                }
            } else {
                closeFdSafely(fd)
                ModelLoadResult.Error("Failed to load GGUF model from URI")
            }
        } catch (e: Exception) {
            ModelLoadResult.Error("GGUF loading error: ${e.message}")
        }
    }

    private suspend fun loadGGUFModel(
        model: Model, config: ModelConfig?
    ): ModelLoadResult = withContext(Dispatchers.IO) {
        try {
            val engine = GGUFEngine()
            val success = engine.load(model, config)

            if (success) {
                val infoJson = engine.getModelInfo()
                if (infoJson != null) {
                    val modelInfo = parseGGUFInfo(infoJson)
                    ModelLoadResult.Success(
                        info = modelInfo, engine = engine
                    )
                } else {
                    ModelLoadResult.Error("Failed to retrieve model information")
                }
            } else {
                ModelLoadResult.Error("Failed to load GGUF model")
            }
        } catch (e: Exception) {
            ModelLoadResult.Error("GGUF loading error: ${e.message}")
        }
    }

    private suspend fun loadDiffusionModel(
        model: Model, config: ModelConfig?
    ): ModelLoadResult = withContext(Dispatchers.IO) {
        try {
            // Parse diffusion config from ModelConfig
            val diffusionConfig = DiffusionConfig.fromJson(config?.modelLoadingParams)
            val inferenceParams = parseDiffusionInferenceParams(config?.modelInferenceParams)

            // Validate model directory exists
            val modelDir = File(model.modelPath)
            if (!modelDir.exists() || !modelDir.isDirectory) {
                return@withContext ModelLoadResult.Error("Model directory not found: ${model.modelPath}")
            }

            // Check for required files — clip uses .mnn if runOnCpu OR useCpuClip (matches DiffusionManager)
            val clipFilename = if (diffusionConfig.runOnCpu || diffusionConfig.useCpuClip) "clip.mnn" else "clip.bin"
            val requiredFiles = if (diffusionConfig.runOnCpu) {
                listOf("clip.mnn", "unet.mnn", "vae_decoder.mnn", "tokenizer.json")
            } else {
                listOf(clipFilename, "unet.bin", "vae_decoder.bin", "tokenizer.json")
            }

            val missingFiles = requiredFiles.filter { !File(modelDir, it).exists() }
            if (missingFiles.isNotEmpty()) {
                return@withContext ModelLoadResult.Error("Missing model files: ${missingFiles.joinToString()}. Expected in: ${model.modelPath}")
            }

            // Load using worker (which uses service)
            val success = LlmModelWorker.loadDiffusionModel(
                name = model.modelName,
                modelDir = model.modelPath,
                height = diffusionConfig.height,
                width = diffusionConfig.width,
                textEmbeddingSize = diffusionConfig.textEmbeddingSize,
                runOnCpu = diffusionConfig.runOnCpu,
                useCpuClip = diffusionConfig.useCpuClip,
                isPony = diffusionConfig.isPony,
                httpPort = diffusionConfig.httpPort,
                safetyMode = diffusionConfig.safetyMode
            )

            if (success) {
                val modelInfo = DiffusionModelInfo(
                    providerType = ProviderType.DIFFUSION,
                    architecture = "Stable Diffusion",
                    name = model.modelName,
                    description = "Stable Diffusion model for image generation",
                    parameters = buildDiffusionParametersMap(diffusionConfig, modelDir),
                    modelConfig = diffusionConfig,
                    inferenceParams = inferenceParams
                )

                ModelLoadResult.Success(
                    info = modelInfo,
                    engine = "DiffusionEngine" // Placeholder since engine is in service
                )
            } else {
                ModelLoadResult.Error("Failed to load Diffusion model")
            }
        } catch (e: Exception) {
            ModelLoadResult.Error("Diffusion loading error: ${e.message}")
        }
    }


    private fun parseDiffusionInferenceParams(jsonString: String?): DiffusionInferenceParams {
        return DiffusionInferenceParams.fromJson(jsonString)
    }

    private fun buildDiffusionParametersMap(
        config: DiffusionConfig, modelDir: File
    ): Map<String, String> {
        return buildMap {
            put("Type", if (config.runOnCpu) "CPU" else "NPU/GPU")
            put("Text Embedding Size", config.textEmbeddingSize.toString())
            put("CLIP Mode", if (config.useCpuClip) "CPU" else "NPU")
            put("Resolution", "${config.width}×${config.height}")
            put("Port", config.httpPort.toString())

            if (config.isPony) {
                put("Model Variant", "Pony v6")
            }

            if (config.safetyMode) {
                put("Safety Checker", "Enabled")
            }

            // Check for available components
            val components = mutableListOf<String>()
            if (File(modelDir, "unet.bin").exists() || File(modelDir, "unet.mnn").exists()) {
                components.add("UNet")
            }
            if (File(modelDir, "vae_decoder.bin").exists() || File(
                    modelDir,
                    "vae_decoder.mnn"
                ).exists()
            ) {
                components.add("VAE Decoder")
            }
            if (File(modelDir, "vae_encoder.bin").exists() || File(
                    modelDir,
                    "vae_encoder.mnn"
                ).exists()
            ) {
                components.add("VAE Encoder")
            }
            if (File(modelDir, "clip_v2.mnn").exists() || File(modelDir, "clip.mnn").exists()) {
                components.add("CLIP")
            }

            if (components.isNotEmpty()) {
                put("Components", components.joinToString(", "))
            }

            // Check for patch files
            val patches = modelDir.listFiles { file ->
                file.name.endsWith(".patch")
            }?.map { it.nameWithoutExtension } ?: emptyList()

            if (patches.isNotEmpty()) {
                put("Available Resolutions", patches.joinToString(", "))
            }
        }
    }

    private fun parseGGUFInfo(jsonString: String): ModelInfo {
        return try {
            val json = JSONObject(jsonString)

            val parameters = buildMap {
                if (json.has("n_vocab")) {
                    put("Vocabulary Size", formatNumber(json.getInt("n_vocab")))
                }
                if (json.has("n_ctx_train")) {
                    put("Context Length", formatNumber(json.getInt("n_ctx_train")))
                }
                if (json.has("n_embd")) {
                    put("Embedding Dim", formatNumber(json.getInt("n_embd")))
                }
                if (json.has("n_layer")) {
                    put("Layers", json.getInt("n_layer").toString())
                }
                if (json.has("n_head")) {
                    put("Attention Heads", json.getInt("n_head").toString())
                }
                if (json.has("n_head_kv")) {
                    put("KV Heads", json.getInt("n_head_kv").toString())
                }
            }

            val vocabularyInfo = buildMap<String, String> {
                if (json.has("vocab_type")) {
                    put("Type", json.getString("vocab_type").uppercase())
                }
                if (json.has("bos")) {
                    put("BOS Token", json.getInt("bos").toString())
                }
                if (json.has("eos")) {
                    put("EOS Token", json.getInt("eos").toString())
                }
                if (json.has("eot")) {
                    put("EOT Token", json.getInt("eot").toString())
                }
                if (json.has("nl")) {
                    put("Newline Token", json.getInt("nl").toString())
                }
            }.takeIf { it.isNotEmpty() }

            GGUFModelInfo(
                providerType = ProviderType.GGUF,
                architecture = json.optString("architecture"),
                name = json.optString("name"),
                description = json.optString("description"),
                parameters = parameters,
                vocabularyInfo = vocabularyInfo,
                systemInfo = json.optString("system"),
                chatTemplate = json.optString("chat_template"),
                templateType = json.optString("template_type")
            )
        } catch (e: Exception) {
            GGUFModelInfo(
                providerType = ProviderType.GGUF,
                architecture = "",
                name = "",
                description = "Error: ${e.message}"
            )
        }
    }


    suspend fun unloadModel(engine: Any?) = withContext(Dispatchers.IO) {
        when (engine) {
            is GGUFEngine -> engine.unload()
            is String -> {
                if (engine == "DiffusionEngine") {
                    LlmModelWorker.stopDiffusionBackend()
                }
            }
        }
    }

    fun checksumSHA256(modelPath: String): String {
        val file = File(modelPath)

        // For directories (diffusion models), use directory name + metadata
        if (file.isDirectory) {
            return checksumDirectory(file)
        }

        // Fast partial hash: first 4 MB + file metadata
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(file.name.toByteArray())
        digest.update(file.length().toString().toByteArray())

        val limit = 4L * 1024 * 1024
        file.inputStream().use { input ->
            val buffer = ByteArray(8 * 1024)
            var remaining = limit
            while (remaining > 0) {
                val toRead = minOf(buffer.size.toLong(), remaining).toInt()
                val len = input.read(buffer, 0, toRead)
                if (len <= 0) break
                digest.update(buffer, 0, len)
                remaining -= len
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    /**
     * Fast partial SHA-256: hashes first 4 MB + file size + display name.
     * Reading the entire multi-GB file caused OOM-kills on Oppo/Redmi devices
     * whose OEM memory managers terminate long-running I/O.
     */
    fun checksumSHA256FromUri(context: Context, uri: Uri): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val fileSize = getFileSizeFromUri(context, uri)
        val fileName = getFileNameFromUri(context, uri)

        // Mix in metadata so identical-prefix files still differ
        digest.update(fileName.toByteArray())
        digest.update(fileSize.toString().toByteArray())

        // Hash only the first 4 MB
        val limit = 4L * 1024 * 1024
        context.contentResolver.openInputStream(uri)?.use { input ->
            val buffer = ByteArray(8 * 1024)
            var remaining = limit
            while (remaining > 0) {
                val toRead = minOf(buffer.size.toLong(), remaining).toInt()
                val len = input.read(buffer, 0, toRead)
                if (len <= 0) break
                digest.update(buffer, 0, len)
                remaining -= len
            }
        } ?: throw IllegalArgumentException("Cannot open input stream for URI: $uri")
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    /**
     * Get file size from content:// URI
     */
    fun getFileSizeFromUri(context: Context, uri: Uri): Long {
        return context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
            pfd.statSize
        } ?: 0L
    }

    /**
     * Get display name from content:// URI
     */
    fun getFileNameFromUri(context: Context, uri: Uri): String {
        var name = "Unknown Model"
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (cursor.moveToFirst() && nameIndex >= 0) {
                name = cursor.getString(nameIndex)
            }
        }
        return name
    }

    /** Safely close a raw fd obtained via detachFd() */
    private fun closeFdSafely(fd: Int) {
        try {
            ParcelFileDescriptor.adoptFd(fd).close()
        } catch (_: Throwable) {}
    }

    private fun checksumDirectory(dir: File): String {
        val digest = MessageDigest.getInstance("SHA-256")

        // Hash directory name
        digest.update(dir.name.toByteArray())

        // Hash key files
        val keyFiles = listOf(
            "unet.bin", "unet.mnn", "vae_decoder.bin", "vae_decoder.mnn", "tokenizer.json"
        )

        keyFiles.forEach { fileName ->
            val file = File(dir, fileName)
            if (file.exists()) {
                digest.update(fileName.toByteArray())
                digest.update(file.length().toString().toByteArray())
            }
        }

        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}

/**
 * Configuration for Diffusion models
 */
data class DiffusionConfig(
    val textEmbeddingSize: Int = 768,
    val runOnCpu: Boolean = false,
    val useCpuClip: Boolean = true,
    val isPony: Boolean = false,
    val httpPort: Int = 8081,
    val safetyMode: Boolean = false,
    val width: Int = 512,
    val height: Int = 512
) {
    fun toJson(): String {
        return JSONObject().apply {
            put("text_embedding_size", textEmbeddingSize)
            put("run_on_cpu", runOnCpu)
            put("use_cpu_clip", useCpuClip)
            put("is_pony", isPony)
            put("http_port", httpPort)
            put("safety_mode", safetyMode)
            put("width", width)
            put("height", height)
        }.toString()
    }

    companion object {
        fun fromJson(jsonString: String?): DiffusionConfig {
            if (jsonString == null) return DiffusionConfig()
            return try {
                val json = JSONObject(jsonString)
                DiffusionConfig(
                    textEmbeddingSize = json.optInt("text_embedding_size", 768),
                    runOnCpu = json.optBoolean("run_on_cpu", false),
                    useCpuClip = json.optBoolean("use_cpu_clip", true),
                    isPony = json.optBoolean("is_pony", false),
                    httpPort = json.optInt("http_port", 8081),
                    safetyMode = json.optBoolean("safety_mode", false),
                    width = json.optInt("width", 512),
                    height = json.optInt("height", 512)
                )
            } catch (_: Exception) {
                DiffusionConfig()
            }
        }
    }
}

/**
 * Result of model loading operation
 */
sealed class ModelLoadResult {
    data class Success(
        val info: ModelInfo, val engine: Any // Can be GGUFEngine, "DiffusionEngine", etc.
    ) : ModelLoadResult()

    data class Error(val message: String) : ModelLoadResult()
}

/**
 * Base interface for model information
 */
interface ModelInfo {
    val providerType: ProviderType
    val architecture: String
    val name: String
    val description: String
    val parameters: Map<String, String>
    val additionalInfo: Map<String, String>?
}

/**
 * GGUF-specific model information
 */
data class GGUFModelInfo(
    override val providerType: ProviderType,
    override val architecture: String,
    override val name: String,
    override val description: String,
    override val parameters: Map<String, String> = emptyMap(),
    val vocabularyInfo: Map<String, String>? = null,
    val systemInfo: String = "",
    val chatTemplate: String = "",
    val templateType: String = ""
) : ModelInfo {
    override val additionalInfo: Map<String, String>?
        get() = vocabularyInfo
}

/**
 * Diffusion-specific model information
 */
data class DiffusionModelInfo(
    override val providerType: ProviderType,
    override val architecture: String,
    override val name: String,
    override val description: String,
    override val parameters: Map<String, String> = emptyMap(),
    val modelConfig: DiffusionConfig,
    val inferenceParams: DiffusionInferenceParams = DiffusionInferenceParams()
) : ModelInfo {
    override val additionalInfo: Map<String, String> = buildMap {
        put("Backend", if (modelConfig.runOnCpu) "CPU" else "NPU/GPU")
        put("CLIP", if (modelConfig.useCpuClip) "CPU (MNN)" else "NPU")
        put("Default Size", "${modelConfig.width}×${modelConfig.height}")

        if (modelConfig.safetyMode) {
            put("Safety Filter", "Enabled")
        }
    }
}


/**
 * Inference parameters for Diffusion models
 */
data class DiffusionInferenceParams(
    val negativePrompt: String = "",
    val steps: Int = 28,
    val cfgScale: Float = 7f,
    val scheduler: String = "dpm",
    val useOpenCL: Boolean = false,
    val denoiseStrength: Float = 0.6f,
    val showDiffusionProcess: Boolean = false,
    val showDiffusionStride: Int = 1
) {
    fun toJson(): String {
        return JSONObject().apply {
            put("negative_prompt", negativePrompt)
            put("steps", steps)
            put("cfg_scale", cfgScale)
            put("scheduler", scheduler)
            put("use_opencl", useOpenCL)
            put("denoise_strength", denoiseStrength)
            put("show_diffusion_process", showDiffusionProcess)
            put("show_diffusion_stride", showDiffusionStride)
        }.toString()
    }

    companion object {
        fun fromJson(jsonString: String?): DiffusionInferenceParams {
            if (jsonString == null) return DiffusionInferenceParams()

            return try {
                val json = JSONObject(jsonString)
                DiffusionInferenceParams(
                    negativePrompt = json.optString("negative_prompt", ""),
                    steps = json.optInt("steps", 28),
                    cfgScale = json.optDouble("cfg_scale", 7.0).toFloat(),
                    scheduler = json.optString("scheduler", "dpm"),
                    useOpenCL = json.optBoolean("use_opencl", false),
                    denoiseStrength = json.optDouble("denoise_strength", 0.6).toFloat(),
                    showDiffusionProcess = json.optBoolean("show_diffusion_process", false),
                    showDiffusionStride = json.optInt("show_diffusion_stride", 1)
                )
            } catch (e: Exception) {
                DiffusionInferenceParams()
            }
        }
    }

    /**
     * Available schedulers for Stable Diffusion
     */
    enum class Scheduler(val value: String) {
        DPM("dpm"), EULER("euler"), EULER_A("euler_a"), DDIM("ddim"), PNDM("pndm");

        companion object {
            fun fromString(value: String): Scheduler {
                return entries.find { it.value == value } ?: DPM
            }
        }
    }
}