package com.dark.tool_neuron.repo

import android.content.Context
import android.os.Build
import android.util.Log
import com.dark.tool_neuron.models.data.HFModelRepository
import com.dark.tool_neuron.models.data.HuggingFaceModel
import com.dark.tool_neuron.models.data.ModelType
import com.dark.tool_neuron.network.HuggingFaceClient

class ModelStoreRepository(private val context: Context) {

    // In-memory cache for model lists
    private var cachedModels: List<HuggingFaceModel>? = null
    private var cacheTimestamp: Long = 0
    private val CACHE_TTL_MS = 5 * 60 * 1000L // 5 minutes

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

    fun isDeviceSupported(): Boolean {
        val soc = getDeviceSoc()
        return getChipsetSuffix(soc) != null
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

    suspend fun getAvailableModels(
        repositories: List<HFModelRepository>,
        forceRefresh: Boolean = false
    ): Result<List<HuggingFaceModel>> {
        // Return cached models if still fresh
        if (!forceRefresh && cachedModels != null &&
            System.currentTimeMillis() - cacheTimestamp < CACHE_TTL_MS
        ) {
            return Result.success(cachedModels!!)
        }

        return try {
            val models = mutableListOf<HuggingFaceModel>()

            val sdModels = getSDModels()
            val ggufModels =
                getGGUFModels(repositories.filter { it.modelType == ModelType.GGUF && it.isEnabled })
            val ttsModels = getTTSModels()

            // Filter NPU models based on device support
            val filteredSDModels = if (isQualcommDevice()) {
                sdModels
            } else {
                sdModels.filter { it.runOnCpu }
            }

            models.addAll(filteredSDModels)
            models.addAll(ggufModels)
            models.addAll(ttsModels)

            // Update cache
            cachedModels = models.toList()
            cacheTimestamp = System.currentTimeMillis()

            Result.success(models)
        } catch (e: Exception) {
            Log.e("ModelStoreRepository", "Error loading models", e)
            Result.failure(e)
        }
    }

    fun invalidateCache() {
        cachedModels = null
        cacheTimestamp = 0
    }

    private suspend fun getSDModels(): List<HuggingFaceModel> {
        val models = mutableListOf<HuggingFaceModel>()
        val soc = getDeviceSoc()
        val suffix = getChipsetSuffix(soc) ?: "min"
        val isQualcomm = isQualcommDevice()

        // 4 NPU models - only for Qualcomm devices
        if (isQualcomm) {
            models.add(
                HuggingFaceModel(
                    id = "anythingv5-npu",
                    name = "Anything V5.0",
                    description = "Anime-style image generation optimized for Qualcomm NPU",
                    fileUri = "xororz/sd-qnn/resolve/main/AnythingV5_qnn2.28_${suffix}.zip",
                    approximateSize = "1.1 GB",
                    modelType = ModelType.SD,
                    isZip = true,
                    chipsetSuffix = suffix,
                    runOnCpu = false,
                    textEmbeddingSize = 768,
                    tags = listOf("NPU", "Anime", "Art"),
                    requiresNPU = true,
                    repositoryUrl = "xororz/sd-qnn"
                )
            )

            models.add(
                HuggingFaceModel(
                    id = "qteamix-npu",
                    name = "QteaMix",
                    description = "Chibi-style image generation for Qualcomm NPU",
                    fileUri = "xororz/sd-qnn/resolve/main/QteaMix_qnn2.28_${suffix}.zip",
                    approximateSize = "1.1 GB",
                    modelType = ModelType.SD,
                    isZip = true,
                    chipsetSuffix = suffix,
                    runOnCpu = false,
                    textEmbeddingSize = 768,
                    tags = listOf("NPU", "Chibi", "Cute"),
                    requiresNPU = true,
                    repositoryUrl = "xororz/sd-qnn"
                )
            )

            models.add(
                HuggingFaceModel(
                    id = "absolutereality-npu",
                    name = "Absolute Reality",
                    description = "Photorealistic image generation for Qualcomm NPU",
                    fileUri = "xororz/sd-qnn/resolve/main/AbsoluteReality_qnn2.28_${suffix}.zip",
                    approximateSize = "1.1 GB",
                    modelType = ModelType.SD,
                    isZip = true,
                    chipsetSuffix = suffix,
                    runOnCpu = false,
                    textEmbeddingSize = 768,
                    tags = listOf("NPU", "Realistic", "Photo"),
                    requiresNPU = true,
                    repositoryUrl = "xororz/sd-qnn"
                )
            )

            models.add(
                HuggingFaceModel(
                    id = "chilloutmix-npu",
                    name = "ChilloutMix",
                    description = "Realistic portraits for Qualcomm NPU",
                    fileUri = "xororz/sd-qnn/resolve/main/ChilloutMix_qnn2.28_${suffix}.zip",
                    approximateSize = "1.1 GB",
                    modelType = ModelType.SD,
                    isZip = true,
                    chipsetSuffix = suffix,
                    runOnCpu = false,
                    textEmbeddingSize = 768,
                    tags = listOf("NPU", "Portrait", "Realistic"),
                    requiresNPU = true,
                    repositoryUrl = "xororz/sd-qnn"
                )
            )
        }

        // 4 CPU models - available for all devices
        models.add(
            HuggingFaceModel(
                id = "anythingv5-cpu",
                name = "Anything V5.0",
                description = "Anime-style image generation for CPU",
                fileUri = "xororz/sd-mnn/resolve/main/AnythingV5.zip",
                approximateSize = "1.2 GB",
                modelType = ModelType.SD,
                isZip = true,
                runOnCpu = true,
                textEmbeddingSize = 768,
                tags = listOf("CPU", "Anime", "Art"),
                requiresNPU = false,
                repositoryUrl = "xororz/sd-mnn"
            )
        )

        models.add(
            HuggingFaceModel(
                id = "qteamix-cpu",
                name = "QteaMix",
                description = "Chibi-style image generation for CPU",
                fileUri = "xororz/sd-mnn/resolve/main/QteaMix.zip",
                approximateSize = "1.2 GB",
                modelType = ModelType.SD,
                isZip = true,
                runOnCpu = true,
                textEmbeddingSize = 768,
                tags = listOf("CPU", "Chibi", "Cute"),
                requiresNPU = false,
                repositoryUrl = "xororz/sd-mnn"
            )
        )

        models.add(
            HuggingFaceModel(
                id = "absolutereality-cpu",
                name = "Absolute Reality",
                description = "Photorealistic image generation for CPU",
                fileUri = "xororz/sd-mnn/resolve/main/AbsoluteReality.zip",
                approximateSize = "1.2 GB",
                modelType = ModelType.SD,
                isZip = true,
                runOnCpu = true,
                textEmbeddingSize = 768,
                tags = listOf("CPU", "Realistic", "Photo"),
                requiresNPU = false,
                repositoryUrl = "xororz/sd-mnn"
            )
        )

        models.add(
            HuggingFaceModel(
                id = "chilloutmix-cpu",
                name = "ChilloutMix",
                description = "Realistic portraits for CPU",
                fileUri = "xororz/sd-mnn/resolve/main/ChilloutMix.zip",
                approximateSize = "1.2 GB",
                modelType = ModelType.SD,
                isZip = true,
                runOnCpu = true,
                textEmbeddingSize = 768,
                tags = listOf("CPU", "Portrait", "Realistic"),
                requiresNPU = false,
                repositoryUrl = "xororz/sd-mnn"
            )
        )

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
                            val sizeStr = formatFileSize(file.size ?: 0)

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

    private fun formatFileSize(bytes: Long): String {
        return when {
            bytes >= 1_000_000_000 -> String.format("%.2f GB", bytes / 1_000_000_000.0)
            bytes >= 1_000_000 -> String.format("%.2f MB", bytes / 1_000_000.0)
            bytes >= 1_000 -> String.format("%.2f KB", bytes / 1_000.0)
            else -> "$bytes B"
        }
    }
}