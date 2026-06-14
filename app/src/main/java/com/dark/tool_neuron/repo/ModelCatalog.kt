package com.dark.tool_neuron.repo

import android.content.Context
import com.dark.tool_neuron.data.SocBucket
import com.dark.tool_neuron.model.HFRepository
import com.dark.tool_neuron.model.HuggingFaceModel
import com.dark.tool_neuron.util.VlmPaths
import com.dark.tool_neuron.util.extractQuantization
import com.dark.download_manager.formatBytes
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ModelCatalog @Inject constructor(
    @ApplicationContext private val context: Context,
    private val hfApi: HuggingFaceApi,
) {
    private val cacheFile = File(context.filesDir, "cache/model_catalog.json")
    private var memoryCache: List<HuggingFaceModel>? = null

    suspend fun getModels(
        repos: List<HFRepository>,
        forceRefresh: Boolean = false
    ): List<HuggingFaceModel> {
        if (!forceRefresh) {
            memoryCache?.let { return it + getBuiltInModels() }
            loadDiskCache()?.let { memoryCache = it; return it + getBuiltInModels() }
        }
        val models = repos.filter { it.isEnabled }.flatMap { repo ->
            try { fetchRepo(repo) } catch (_: Exception) { emptyList() }
        }
        memoryCache = models
        saveDiskCache(models)
        return models + getBuiltInModels()
    }

    fun getBuiltInModels(): List<HuggingFaceModel> = BUILT_IN_MODELS + imageModels()

    private fun imageModels(): List<HuggingFaceModel> {
        val soc = SocBucket.socModel()
        val bucket = SocBucket.bucket(soc)
        val sdxlOk = SocBucket.isSdxlCapable(soc)
        val list = mutableListOf<HuggingFaceModel>()

        if (bucket != null) {
            list += sdNpuRow("anythingv5", "Anything V5", bucket,
                "masterpiece, best quality, 1girl, solo, cute, white hair,",
                ANIME_NEGATIVE)
            list += sdNpuRow("qteamix", "QteaMix", bucket,
                "chibi, best quality, 1girl, solo, cute, pink hair,",
                ANIME_NEGATIVE)
            list += sdNpuRow("absolutereality", "Absolute Reality", bucket,
                "masterpiece, best quality, ultra-detailed, realistic, 8k, a cat on grass,",
                REAL_NEGATIVE)
            list += sdNpuRow("cuteyukimix", "CuteYukiMix", bucket,
                "masterpiece, best quality, 1girl, solo, cute, white hair,",
                ANIME_NEGATIVE)
            list += sdNpuRow("chilloutmix", "ChilloutMix", bucket,
                "RAW photo, best quality, realistic, photo-realistic, masterpiece, 1girl, upper body,",
                REAL_NEGATIVE)

            if (sdxlOk) {
                list += sdxlRow("sdxl_base", "SDXL Base 1.0",
                    "sdxl_base_qnn2.28_8gen3.zip",
                    "masterpiece, best quality, a majestic cat on a windowsill at sunset,",
                    SDXL_NEGATIVE)
                list += sdxlRow("illustrious_v16", "Illustrious v16",
                    "illustrious_v16_qnn2.28_8gen3.zip",
                    "1girl, solo, blue twintails, very long hair, masterpiece,",
                    ANIME_NEGATIVE)
            }
            list += upscalerRow("upscaler_anime", "Real-ESRGAN x4 Anime",
                "realesrgan_x4plus_anime_6b", bucket)
            list += upscalerRow("upscaler_realistic", "UltraSharp v2 Lite",
                "4x_UltraSharpV2_Lite", bucket)
        } else {
            list += sdCpuRow("anythingv5_cpu", "Anything V5 (CPU)",
                "AnythingV5.zip",
                "masterpiece, best quality, 1girl, solo, cute, white hair,",
                ANIME_NEGATIVE)
            list += sdCpuRow("qteamix_cpu", "QteaMix (CPU)",
                "QteaMix.zip",
                "chibi, best quality, 1girl, solo, cute, pink hair,",
                ANIME_NEGATIVE)
            list += sdCpuRow("absolutereality_cpu", "Absolute Reality (CPU)",
                "AbsoluteReality.zip",
                "masterpiece, best quality, ultra-detailed, realistic, 8k, a cat on grass,",
                REAL_NEGATIVE)
            list += sdCpuRow("cuteyukimix_cpu", "CuteYukiMix (CPU)",
                "CuteYukiMix.zip",
                "masterpiece, best quality, 1girl, solo, cute, white hair,",
                ANIME_NEGATIVE)
            list += sdCpuRow("chilloutmix_cpu", "ChilloutMix (CPU)",
                "ChilloutMix.zip",
                "RAW photo, best quality, realistic, photo-realistic, masterpiece, 1girl, upper body,",
                REAL_NEGATIVE)
        }

        return list
    }

    private fun sdNpuRow(
        id: String,
        name: String,
        bucket: String,
        defaultPrompt: String,
        defaultNegativePrompt: String,
    ): HuggingFaceModel {
        val fileName = sdQnnFile(id, bucket)
        return HuggingFaceModel(
            id = id, name = name,
            fileName = fileName,
            fileUri = "${HuggingFaceApi.BASE}/xororz/sd-qnn/resolve/main/$fileName",
            approximateSize = "~1.1 GB", sizeBytes = 1_100_000_000L,
            repoId = "image-gen-npu",
            tags = listOf("Image Gen", "NPU", "SD 1.5", bucket),
            modelType = "image_gen",
            requiresNpu = true,
            featureLabel = "Image Generation",
            defaultPrompt = defaultPrompt,
            defaultNegativePrompt = defaultNegativePrompt,
            generationSize = 512,
        )
    }

    private fun sdCpuRow(
        id: String,
        name: String,
        zipName: String,
        defaultPrompt: String,
        defaultNegativePrompt: String,
    ): HuggingFaceModel = HuggingFaceModel(
        id = id, name = name,
        fileName = zipName,
        fileUri = "${HuggingFaceApi.BASE}/xororz/sd-mnn/resolve/main/$zipName",
        approximateSize = "~1.2 GB", sizeBytes = 1_200_000_000L,
        repoId = "image-gen-cpu",
        tags = listOf("Image Gen", "CPU", "SD 1.5"),
        modelType = "image_gen",
        requiresNpu = false,
        featureLabel = "Image Generation",
        defaultPrompt = defaultPrompt,
        defaultNegativePrompt = defaultNegativePrompt,
        generationSize = 512,
    )

    private fun sdxlRow(
        id: String,
        name: String,
        zipName: String,
        defaultPrompt: String,
        defaultNegativePrompt: String,
    ): HuggingFaceModel = HuggingFaceModel(
        id = id, name = name,
        fileName = zipName,
        fileUri = "${HuggingFaceApi.BASE}/xororz/sdxl-qnn/resolve/main/$zipName",
        approximateSize = "~4.2 GB", sizeBytes = 4_500_000_000L,
        repoId = "image-gen-sdxl",
        tags = listOf("Image Gen", "NPU", "SDXL", "1024"),
        modelType = "image_gen",
        requiresNpu = true,
        isSdxl = true,
        featureLabel = "Image Generation",
        defaultPrompt = defaultPrompt,
        defaultNegativePrompt = defaultNegativePrompt,
        generationSize = 1024,
    )

    private fun upscalerRow(
        id: String,
        name: String,
        repoDir: String,
        bucket: String,
    ): HuggingFaceModel {
        val fileName = "upscaler_$bucket.bin"
        return HuggingFaceModel(
            id = id, name = name,
            fileName = fileName,
            fileUri = "${HuggingFaceApi.BASE}/xororz/upscaler/resolve/main/$repoDir/$fileName",
            approximateSize = "~70 MB", sizeBytes = 70_000_000L,
            repoId = "image-upscaler",
            tags = listOf("Upscaler", "4x", bucket),
            modelType = "image_upscaler",
            requiresNpu = true,
            isUpscaler = true,
            featureLabel = "Upscale 4x",
        )
    }

    private fun sdQnnFile(id: String, bucket: String): String = when (id) {
        "anythingv5" -> "AnythingV5_qnn2.28_$bucket.zip"
        "qteamix" -> "QteaMix_qnn2.28_$bucket.zip"
        "absolutereality" -> "AbsoluteReality_qnn2.28_$bucket.zip"
        "cuteyukimix" -> "CuteYukiMix_qnn2.28_$bucket.zip"
        "chilloutmix" -> "ChilloutMix_qnn2.28_$bucket.zip"
        else -> "${id}_qnn2.28_$bucket.zip"
    }

    private val ANIME_NEGATIVE = "lowres, bad anatomy, bad hands, missing fingers, extra fingers, " +
        "poorly drawn face, fused face, ugly, worst quality, 2girl, long fingers, disconnected limbs"
    private val REAL_NEGATIVE = "worst quality, low quality, lowres, signature, watermark, " +
        "ugly, blurry, unrealistic, semi realistic, pixelated, cartoon, anime, drawing, censored"
    private val SDXL_NEGATIVE = "lowres, bad anatomy, text, error, missing fingers, cropped, " +
        "worst quality, low quality, jpeg artifacts, signature, watermark, blurry"

    fun clearCache() {
        memoryCache = null
        cacheFile.delete()
    }

    private suspend fun fetchRepo(repo: HFRepository): List<HuggingFaceModel> =
        withContext(Dispatchers.IO) {
            val meta = hfApi.fetchJson(hfApi.modelInfoUrl(repo.repoPath)).getOrNull()
                ?: return@withContext emptyList()
            val tree = hfApi.fetchJsonArray(hfApi.modelTreeUrl(repo.repoPath)).getOrNull()
                ?: return@withContext emptyList()

            val author = meta.optString("author", repo.repoPath.substringBefore("/"))
            val downloads = meta.optInt("downloads", 0)
            val likes = meta.optInt("likes", 0)
            val gated = meta.opt("gated")
            val isGated = gated != null && gated != false && gated.toString() != "false"
            val repoModelType = inferModelType(repo, meta)

            val allFiles = (0 until tree.length()).mapNotNull { i ->
                val file = tree.getJSONObject(i)
                val path = file.optString("path", "")
                if (path.isBlank() || !path.endsWith(".gguf", ignoreCase = true)) return@mapNotNull null
                path to file.optLong("size", 0)
            }
            val mmprojFile = allFiles.firstOrNull { VlmPaths.isMmprojFileName(it.first) }
            val isVlmRepo = mmprojFile != null

            val models = mutableListOf<HuggingFaceModel>()
            for ((path, size) in allFiles) {
                if (path.contains("projector", ignoreCase = true) && !VlmPaths.isMmprojFileName(path)) continue

                val isMmproj = VlmPaths.isMmprojFileName(path)
                val quant = if (isMmproj) "mmproj" else (extractQuantization(path) ?: "")
                val modelType = if (isMmproj) "gguf" else repoModelType

                models.add(HuggingFaceModel(
                    id = "${repo.id}__$path",
                    name = if (isMmproj) "${repo.name} · Projector" else buildDisplayName(repo.name, quant, path),
                    fileName = path,
                    fileUri = hfApi.resolveFileUrl(repo.repoPath, path),
                    approximateSize = if (size > 0) formatBytes(size) else "Unknown",
                    sizeBytes = size,
                    repoId = repo.id,
                    repoPath = repo.repoPath,
                    quantization = quant,
                    tags = buildList {
                        add(author)
                        if (isGated) add("Gated")
                        if (downloads > 1000) add("${downloads / 1000}k+ downloads")
                        if (modelType == "embedding") {
                            add("Embedding")
                            add("RAG")
                        }
                        if (isVlmRepo) add("VLM")
                        if (isMmproj) add("mmproj")
                    },
                    modelType = modelType,
                    isVlm = isVlmRepo,
                    isMmproj = isMmproj,
                    mmprojFileName = mmprojFile?.first.orEmpty(),
                    mmprojFileUri = mmprojFile?.let {
                        hfApi.resolveFileUrl(repo.repoPath, it.first)
                    }.orEmpty(),
                    mmprojSizeBytes = mmprojFile?.second ?: 0L,
                ))
            }
            models.sortedWith(compareByDescending<HuggingFaceModel> { it.isMmproj }.thenBy { it.sizeBytes })
        }

    private fun inferModelType(repo: HFRepository, meta: JSONObject): String {
        val haystack = buildString {
            append(repo.id).append(' ')
            append(repo.name).append(' ')
            append(repo.repoPath).append(' ')
            append(meta.optString("pipeline_tag")).append(' ')
            meta.optJSONArray("tags")?.let { tags ->
                for (i in 0 until tags.length()) append(tags.optString(i)).append(' ')
            }
        }.lowercase()

        val embeddingHints = listOf(
            "sentence-similarity", "feature-extraction", "embedding", "embeddings",
            "embed", "bge", "e5-", "gte-", "nomic-embed", "qwen3-embedding",
        )
        return if (embeddingHints.any { it in haystack }) "embedding" else "gguf"
    }

    private fun buildDisplayName(repoName: String, quant: String, fileName: String): String {
        if (quant.isNotBlank()) return "$repoName $quant"
        return "$repoName (${fileName.removeSuffix(".gguf")})"
    }


    private fun loadDiskCache(): List<HuggingFaceModel>? {
        if (!cacheFile.exists()) return null
        if (System.currentTimeMillis() - cacheFile.lastModified() > CACHE_TTL_MS) return null
        return try {
            val arr = JSONArray(cacheFile.readText())
            (0 until arr.length()).map { arr.getJSONObject(it).toModel() }
        } catch (_: Exception) { null }
    }

    private fun saveDiskCache(models: List<HuggingFaceModel>) {
        try {
            cacheFile.parentFile?.mkdirs()
            val arr = JSONArray()
            models.forEach { arr.put(it.toJson()) }
            cacheFile.writeText(arr.toString())
        } catch (_: Exception) {}
    }

    private fun HuggingFaceModel.toJson(): JSONObject = JSONObject().apply {
        put("id", id); put("name", name); put("fileName", fileName)
        put("fileUri", fileUri); put("approximateSize", approximateSize)
        put("sizeBytes", sizeBytes); put("repoId", repoId)
        put("repoPath", repoPath)
        put("quantization", quantization)
        put("tags", JSONArray(tags))
        put("modelType", modelType)
        put("isVlm", isVlm)
        put("isMmproj", isMmproj)
        put("mmprojFileName", mmprojFileName)
        put("mmprojFileUri", mmprojFileUri)
        put("mmprojSizeBytes", mmprojSizeBytes)
        put("isSdxl", isSdxl)
        put("requiresNpu", requiresNpu)
        put("isUpscaler", isUpscaler)
        put("featureLabel", featureLabel)
        put("defaultPrompt", defaultPrompt)
        put("defaultNegativePrompt", defaultNegativePrompt)
        put("generationSize", generationSize)
    }

    private fun JSONObject.toModel(): HuggingFaceModel = HuggingFaceModel(
        id = getString("id"), name = getString("name"),
        fileName = getString("fileName"), fileUri = getString("fileUri"),
        approximateSize = optString("approximateSize", "Unknown"),
        sizeBytes = optLong("sizeBytes"),
        repoId = optString("repoId"),
        repoPath = optString("repoPath"),
        quantization = optString("quantization"),
        tags = optJSONArray("tags")?.let { arr ->
            (0 until arr.length()).map { arr.getString(it) }
        } ?: emptyList(),
        modelType = optString("modelType", "gguf"),
        isVlm = optBoolean("isVlm", false),
        isMmproj = optBoolean("isMmproj", false),
        mmprojFileName = optString("mmprojFileName"),
        mmprojFileUri = optString("mmprojFileUri"),
        mmprojSizeBytes = optLong("mmprojSizeBytes"),
        isSdxl = optBoolean("isSdxl", false),
        requiresNpu = optBoolean("requiresNpu", false),
        isUpscaler = optBoolean("isUpscaler", false),
        featureLabel = optString("featureLabel"),
        defaultPrompt = optString("defaultPrompt"),
        defaultNegativePrompt = optString("defaultNegativePrompt"),
        generationSize = optInt("generationSize", 512),
    )

    companion object {
        private const val CACHE_TTL_MS = 24 * 60 * 60 * 1000L

        private val BUILT_IN_MODELS = listOf(
            HuggingFaceModel(
                id = "vits-piper-en_US-amy-low",
                name = "Piper Amy · Low (English)",
                fileName = "vits-piper-en_US-amy-low.tar.bz2",
                fileUri = "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/vits-piper-en_US-amy-low.tar.bz2",
                approximateSize = "~30 MB", sizeBytes = 30_547_968L,
                repoId = "built-in-tts", quantization = "",
                tags = listOf("TTS", "English", "Piper", "Fast"),
                modelType = "tts",
            ),
            HuggingFaceModel(
                id = "vits-piper-en_US-libritts-high",
                name = "Piper LibriTTS · High (English, multi-speaker)",
                fileName = "vits-piper-en_US-libritts_r-medium.tar.bz2",
                fileUri = "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/vits-piper-en_US-libritts_r-medium.tar.bz2",
                approximateSize = "~124 MB", sizeBytes = 124_514_304L,
                repoId = "built-in-tts", quantization = "",
                tags = listOf("TTS", "English", "Piper", "Multi-speaker"),
                modelType = "tts",
            ),
            HuggingFaceModel(
                id = "sherpa-onnx-whisper-tiny-en",
                name = "Whisper Tiny (English)",
                fileName = "sherpa-onnx-whisper-tiny.en.tar.bz2",
                fileUri = "https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/sherpa-onnx-whisper-tiny.en.tar.bz2",
                approximateSize = "~75 MB", sizeBytes = 78_200_832L,
                repoId = "built-in-stt", quantization = "",
                tags = listOf("STT", "English", "Whisper", "Fast"),
                modelType = "stt",
            ),
            HuggingFaceModel(
                id = "sherpa-onnx-whisper-tiny",
                name = "Whisper Tiny (Multilingual)",
                fileName = "sherpa-onnx-whisper-tiny.tar.bz2",
                fileUri = "https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/sherpa-onnx-whisper-tiny.tar.bz2",
                approximateSize = "~82 MB", sizeBytes = 86_030_336L,
                repoId = "built-in-stt", quantization = "",
                tags = listOf("STT", "Multilingual", "Whisper"),
                modelType = "stt",
            ),
            HuggingFaceModel(
                id = "nomic-embed-text-v1.5-q4_k_m",
                name = "Nomic Embed Text v1.5 (Q4)",
                fileName = "nomic-embed-text-v1.5.Q4_K_M.gguf",
                fileUri = "${HuggingFaceApi.BASE}/nomic-ai/nomic-embed-text-v1.5-GGUF/resolve/main/nomic-embed-text-v1.5.Q4_K_M.gguf",
                approximateSize = "~84 MB",
                sizeBytes = 84_106_624L,
                repoId = "embedding-built-in",
                quantization = "Q4_K_M",
                tags = listOf("Embedding", "RAG", "Matryoshka", "Apache-2.0", "Nomic"),
                modelType = "embedding",
            ),
        )
    }
}
