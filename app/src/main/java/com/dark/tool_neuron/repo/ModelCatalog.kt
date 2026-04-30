package com.dark.tool_neuron.repo

import android.content.Context
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

    fun getBuiltInModels(): List<HuggingFaceModel> = BUILT_IN_MODELS

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
                        if (isVlmRepo) add("VLM")
                        if (isMmproj) add("mmproj")
                    },
                    modelType = "gguf",
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
