package com.dark.tool_neuron.repo

import android.content.Context
import com.dark.tool_neuron.global.formatDecimalBytes
import android.os.Build
import android.util.Log
import com.dark.tool_neuron.models.data.HFModelRepository
import com.dark.tool_neuron.models.data.HuggingFaceModel
import com.dark.tool_neuron.models.data.ModelCategory
import com.dark.tool_neuron.models.data.ModelType
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import java.io.File
import com.dark.tool_neuron.network.HuggingFaceClient
import com.dark.tool_neuron.network.HuggingFaceFileResponse

@Serializable
data class ModelStoreCache(
    val models: List<HuggingFaceModel>,
    val timestamp: Long,
    val cacheVersion: Int = 0
) {
    companion object {
        // Bump this when filtering logic changes to auto-invalidate stale caches
        const val CURRENT_VERSION = 2
    }
}

class ModelStoreRepository(private val context: Context) {

    private val json = Json { ignoreUnknownKeys = true; prettyPrint = false }
    private val cacheDir = File(context.filesDir, "cache").apply { mkdirs() }
    private val cacheFile = File(cacheDir, "model_store_cache.json")

    @Volatile
    private var cachedModels: List<HuggingFaceModel>? = null

    private val chipsetModelSuffixes = mapOf(
        "SM8475" to "8gen1",
        "SM8450" to "8gen1",
        "SM8550" to "8gen2",
        "SM8550P" to "8gen2",
        "QCS8550" to "8gen2",
        "QCM8550" to "8gen2",
        "SM8650" to "8gen3",
        "SM8650P" to "8gen3",
        "SM8750" to "8elite",
        "SM8750P" to "8elite",
        "SM8850" to "8elite",
        "SM8850P" to "8elite",
        "SM8735" to "8gen3",
        "SM8845" to "8gen3",
    )

    private fun getDeviceSoc(): String {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Build.SOC_MODEL
        } else {
            "UNKNOWN"
        }
    }

    fun isQualcommDevice(): Boolean {
        val soc = getDeviceSoc()
        return soc.startsWith("SM") || soc.startsWith("QCS") || soc.startsWith("QCM")
    }

    fun getChipsetSuffix(soc: String): String? {
        if (soc in chipsetModelSuffixes) {
            return chipsetModelSuffixes[soc]
        }
        if (soc.startsWith("SM")) {
            return "min"
        }
        return null
    }

    fun getDeviceInfo(): Map<String, String> {
        val soc = getDeviceSoc()
        return mapOf(
            "soc" to soc,
            "chipset" to (getChipsetSuffix(soc) ?: "Not Supported"),
            "npu" to if (isQualcommDevice()) "Available" else "Not Available",
            "device" to "${Build.MANUFACTURER} ${Build.MODEL}"
        )
    }

    /**
     * Build the ordered list of QNN zip suffixes this device should try.
     * - 8gen1+: prefer exact chipset, fall back through older gens, then "min"
     * - SM7* / unknown SM8*: "min" only (NPU too weak for gen-specific builds)
     * - Non-Qualcomm: null (skip NPU repos entirely)
     */
    private fun getNpuSuffixChain(): List<String>? {
        val soc = getDeviceSoc()
        if (!isQualcommDevice()) return null

        val suffix = getChipsetSuffix(soc)
        val genChain = listOf("8elite", "8gen3", "8gen2", "8gen1", "min")
        val idx = genChain.indexOf(suffix)

        // Known 8gen* suffix → slice from that point to include all compatible older builds
        return if (idx >= 0) genChain.subList(idx, genChain.size) else listOf("min")
    }

    suspend fun getAvailableModels(
        repositories: List<HFModelRepository>,
        forceRefresh: Boolean = false
    ): Result<List<HuggingFaceModel>> {
        // Return in-memory cache if available and not force-refresh
        if (!forceRefresh && cachedModels != null) {
            return Result.success(cachedModels!!)
        }

        // Load from disk cache if not force-refresh
        if (!forceRefresh) {
            loadDiskCache()?.let { cached ->
                cachedModels = cached
                return Result.success(cached)
            }
        }

        return fetchAndCache(repositories)
    }

    suspend fun refreshModels(
        repositories: List<HFModelRepository>
    ): Result<List<HuggingFaceModel>> {
        return fetchAndCache(repositories)
    }

    private suspend fun fetchAndCache(
        repositories: List<HFModelRepository>
    ): Result<List<HuggingFaceModel>> {
        return try {
            val models = mutableListOf<HuggingFaceModel>()

            val sdModels = getSDModels(repositories.filter { it.modelType == ModelType.SD && it.isEnabled })
            val ggufModels = getGGUFModels(repositories.filter { it.modelType == ModelType.GGUF && it.isEnabled })
            val ttsModels = getTTSModels()

            models.addAll(sdModels)
            models.addAll(ggufModels)
            models.addAll(ttsModels)

            val modelList = models.toList()
            cachedModels = modelList
            writeDiskCache(modelList)

            Result.success(modelList)
        } catch (e: Exception) {
            Log.e("ModelStoreRepository", "Error loading models", e)
            Result.failure(e)
        }
    }

    private fun loadDiskCache(): List<HuggingFaceModel>? {
        return try {
            if (!cacheFile.exists()) return null
            val cache = json.decodeFromString<ModelStoreCache>(cacheFile.readText())
            // Invalidate cache if filtering logic has changed
            if (cache.cacheVersion < ModelStoreCache.CURRENT_VERSION) {
                cacheFile.delete()
                return null
            }
            cache.models.ifEmpty { null }
        } catch (e: Exception) {
            Log.e("ModelStoreRepository", "Failed to load disk cache", e)
            null
        }
    }

    private fun writeDiskCache(models: List<HuggingFaceModel>) {
        try {
            val cache = ModelStoreCache(
                models = models,
                timestamp = System.currentTimeMillis(),
                cacheVersion = ModelStoreCache.CURRENT_VERSION
            )
            cacheFile.writeText(json.encodeToString(cache))
        } catch (e: Exception) {
            Log.e("ModelStoreRepository", "Failed to write disk cache", e)
        }
    }

    private suspend fun getSDModels(repositories: List<HFModelRepository>): List<HuggingFaceModel> {
        val models = mutableListOf<HuggingFaceModel>()
        val npuSuffixChain = getNpuSuffixChain() // null = non-Qualcomm

        repositories.forEach { repo ->
            try {
                val isNpuRepo = repo.repoPath.contains("qnn", ignoreCase = true)
                // Skip NPU repos on non-Qualcomm devices
                if (isNpuRepo && npuSuffixChain == null) return@forEach

                val response = HuggingFaceClient.api.getRepoFiles(repo.repoPath)
                if (!response.isSuccessful) {
                    Log.e("ModelStoreRepository", "Failed to fetch SD repo ${repo.repoPath}: ${response.code()}")
                    return@forEach
                }

                val files = response.body() ?: emptyList()
                val zipFiles = files.filter { it.path.endsWith(".zip", ignoreCase = true) }

                if (isNpuRepo) {
                    val suffixChain = npuSuffixChain ?: return@forEach
                    val isMinOnly = suffixChain.size == 1 && suffixChain[0] == "min"

                    // Find the best available suffix from the fallback chain
                    var matchingFiles = emptyList<HuggingFaceFileResponse>()
                    var matchedSuffix = suffixChain.last()
                    var matchedPattern = Regex("[_-]${Regex.escape(matchedSuffix)}\\.zip$", RegexOption.IGNORE_CASE)

                    for (suffix in suffixChain) {
                        val pattern = Regex("[_-]${Regex.escape(suffix)}\\.zip$", RegexOption.IGNORE_CASE)
                        val matches = zipFiles.filter { file ->
                            pattern.containsMatchIn(file.path.substringAfterLast("/"))
                        }
                        if (matches.isNotEmpty()) {
                            matchingFiles = matches
                            matchedSuffix = suffix
                            matchedPattern = pattern
                            break
                        }
                    }

                    matchingFiles.forEach { file ->
                        val fileName = file.path.substringAfterLast("/")
                        val baseName = fileName
                            .replace(matchedPattern, "")
                            .replace(Regex("[_-]qnn[\\d.]*$", RegexOption.IGNORE_CASE), "")
                        val sizeStr = formatDecimalBytes(file.size ?: 0)

                        val tags = mutableListOf("NPU", repo.name)
                        if (repo.category == ModelCategory.UNCENSORED) tags.add("NSFW")

                        models.add(
                            HuggingFaceModel(
                                id = "${repo.id}-${baseName.lowercase()}",
                                name = baseName,
                                description = "$baseName image generation for Qualcomm NPU",
                                fileUri = "${repo.repoPath}/resolve/main/${file.path}",
                                approximateSize = sizeStr,
                                modelType = ModelType.SD,
                                isZip = true,
                                chipsetSuffix = matchedSuffix,
                                runOnCpu = isMinOnly,
                                textEmbeddingSize = 768,
                                tags = tags,
                                requiresNPU = !isMinOnly,
                                repositoryUrl = repo.repoPath
                            )
                        )
                    }
                } else {
                    // CPU repo: show all zips
                    zipFiles.forEach { file ->
                        val fileName = file.path.substringAfterLast("/")
                        val baseName = fileName.removeSuffix(".zip").removeSuffix(".ZIP")
                        val sizeStr = formatDecimalBytes(file.size ?: 0)

                        models.add(
                            HuggingFaceModel(
                                id = "${repo.id}-${baseName.lowercase()}",
                                name = baseName,
                                description = "$baseName image generation (CPU)",
                                fileUri = "${repo.repoPath}/resolve/main/${file.path}",
                                approximateSize = sizeStr,
                                modelType = ModelType.SD,
                                isZip = true,
                                runOnCpu = true,
                                textEmbeddingSize = 768,
                                tags = mutableListOf("CPU", repo.name).apply {
                                    if (repo.category == ModelCategory.UNCENSORED) add("NSFW")
                                },
                                requiresNPU = false,
                                repositoryUrl = repo.repoPath
                            )
                        )
                    }
                }
            } catch (e: Exception) {
                Log.e("ModelStoreRepository", "Error fetching SD models from ${repo.repoPath}", e)
            }
        }

        return models
    }

    private fun getTTSModels(): List<HuggingFaceModel> {
        return listOf(
            HuggingFaceModel(
                id = "supertonic-v2-tts",
                name = "Supertonic v2 (Multilingual TTS)",
                description = "On-device TTS engine: 5 languages (EN/KO/ES/PT/FR), 10 voices, 44.1kHz, 66M params",
                fileUri = "Supertone/supertonic-2/resolve/main",
                approximateSize = "263 MB",
                modelType = ModelType.TTS,
                isZip = false,
                runOnCpu = true,
                textEmbeddingSize = 0,
                tags = listOf("TTS", "Multilingual", "EN", "KO", "ES", "PT", "FR", "10 Voices"),
                requiresNPU = false,
                repositoryUrl = "Supertone/supertonic-2"
            )
        )
    }

    private suspend fun getGGUFModels(repositories: List<HFModelRepository>): List<HuggingFaceModel> {
        val models = mutableListOf<HuggingFaceModel>()

        repositories.forEach { repo ->
            try {
                val response = HuggingFaceClient.api.getRepoFiles(repo.repoPath)

                if (response.isSuccessful) {
                    val files = response.body() ?: emptyList()

                    // Detect if this repo supports tool calling (Qwen/ChatML models)
                    val supportsToolCalling = repo.repoPath.contains("qwen", ignoreCase = true) ||
                            repo.repoPath.contains("Qwen", ignoreCase = false) ||
                            repo.name.contains("qwen", ignoreCase = true)

                    files.filter { file ->
                        file.path.endsWith(".gguf") &&
                                // Filter out mmproj/vision projection files - these are not standalone models
                                !file.path.contains("mmproj", ignoreCase = true) &&
                                !file.path.contains("vision-adapter", ignoreCase = true) &&
                                !file.path.contains("projector", ignoreCase = true)
                    }.forEach { file ->
                            val fileName = file.path.substringAfterLast("/")
                            val sizeStr = formatDecimalBytes(file.size ?: 0)

                            // Extract quantization type from filename
                            val quantType =
                                fileName.substringAfterLast("-").removeSuffix(".gguf").uppercase()

                            val baseTags = mutableListOf("GGUF", quantType, repo.name)
                            if (supportsToolCalling) {
                                baseTags.add("Tool Calling")
                            }

                            models.add(
                                HuggingFaceModel(
                                    id = "${repo.id}-${fileName.removeSuffix(".gguf")}",
                                    name = "${repo.name} - $quantType",
                                    description = "${repo.name} model with $quantType quantization",
                                    fileUri = "${repo.repoPath}/resolve/main/${file.path}",
                                    approximateSize = sizeStr,
                                    modelType = ModelType.GGUF,
                                    isZip = false,
                                    runOnCpu = false,
                                    textEmbeddingSize = 0,
                                    tags = baseTags,
                                    requiresNPU = false,
                                    repositoryUrl = repo.repoPath
                                )
                            )
                        }
                } else {
                    Log.e(
                        "ModelStoreRepository",
                        "Failed to fetch from ${repo.repoPath}: ${response.code()}"
                    )
                }
            } catch (e: Exception) {
                Log.e("ModelStoreRepository", "Error fetching GGUF models from ${repo.repoPath}", e)
            }
        }

        return models
    }

}