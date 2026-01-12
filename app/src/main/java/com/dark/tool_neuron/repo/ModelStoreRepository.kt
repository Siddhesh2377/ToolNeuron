package com.dark.tool_neuron.repo

import android.content.Context
import android.os.Build
import android.util.Log
import com.dark.tool_neuron.models.data.HuggingFaceModel
import com.dark.tool_neuron.models.data.ModelType
import com.dark.tool_neuron.network.HuggingFaceClient

class ModelStoreRepository(private val context: Context) {
    
    private val chipsetModelSuffixes = mapOf(
        "SM8475" to "8gen1",
        "SM8450" to "8gen1",
        "SM8550" to "8gen2",
        "SM8550P" to "8gen2",
        "QCS8550" to "8gen2",
        "QCM8550" to "8gen2",
        "SM8650" to "8gen2",
        "SM8650P" to "8gen2",
        "SM8750" to "8gen2",
        "SM8750P" to "8gen2",
        "SM8850" to "8gen2",
        "SM8850P" to "8gen2",
        "SM8735" to "8gen2",
        "SM8845" to "8gen2",
    )
    
    private fun getDeviceSoc(): String {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Build.SOC_MODEL
        } else {
            "CPU"
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
    
    suspend fun getAvailableModels(): Result<List<HuggingFaceModel>> {
        return try {
            val models = mutableListOf<HuggingFaceModel>()
            
            val sdModels = getSDModels()
            val ggufModels = getGGUFModels()
            
            models.addAll(sdModels)
            models.addAll(ggufModels)
            
            Result.success(models)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    private suspend fun getSDModels(): List<HuggingFaceModel> {
        val models = mutableListOf<HuggingFaceModel>()
        val soc = getDeviceSoc()
        val suffix = getChipsetSuffix(soc) ?: "min"
        val isQualcomm = isQualcommDevice()
        
        if (isQualcomm) {
            models.add(
                HuggingFaceModel(
                    id = "anythingv5",
                    name = "Anything V5.0 (NPU)",
                    description = "Anime-style image generation optimized for Qualcomm NPU",
                    fileUri = "xororz/sd-qnn/resolve/main/AnythingV5_qnn2.28_${suffix}.zip",
                    approximateSize = "1.1GB",
                    modelType = ModelType.SD,
                    isZip = true,
                    chipsetSuffix = suffix,
                    runOnCpu = false,
                    textEmbeddingSize = 768
                )
            )
            
            models.add(
                HuggingFaceModel(
                    id = "qteamix",
                    name = "QteaMix (NPU)",
                    description = "Chibi-style image generation for Qualcomm NPU",
                    fileUri = "xororz/sd-qnn/resolve/main/QteaMix_qnn2.28_${suffix}.zip",
                    approximateSize = "1.1GB",
                    modelType = ModelType.SD,
                    isZip = true,
                    chipsetSuffix = suffix,
                    runOnCpu = false,
                    textEmbeddingSize = 768
                )
            )
            
            models.add(
                HuggingFaceModel(
                    id = "cuteyukimix",
                    name = "CuteYukiMix (NPU)",
                    description = "Cute anime characters for Qualcomm NPU",
                    fileUri = "xororz/sd-qnn/resolve/main/CuteYukiMix_qnn2.28_${suffix}.zip",
                    approximateSize = "1.1GB",
                    modelType = ModelType.SD,
                    isZip = true,
                    chipsetSuffix = suffix,
                    runOnCpu = false,
                    textEmbeddingSize = 768
                )
            )
            
            models.add(
                HuggingFaceModel(
                    id = "absolutereality",
                    name = "Absolute Reality (NPU)",
                    description = "Photorealistic image generation for Qualcomm NPU",
                    fileUri = "xororz/sd-qnn/resolve/main/AbsoluteReality_qnn2.28_${suffix}.zip",
                    approximateSize = "1.1GB",
                    modelType = ModelType.SD,
                    isZip = true,
                    chipsetSuffix = suffix,
                    runOnCpu = false,
                    textEmbeddingSize = 768
                )
            )
            
            models.add(
                HuggingFaceModel(
                    id = "chilloutmix",
                    name = "ChilloutMix (NPU)",
                    description = "Realistic portraits for Qualcomm NPU",
                    fileUri = "xororz/sd-qnn/resolve/main/ChilloutMix_qnn2.28_${suffix}.zip",
                    approximateSize = "1.1GB",
                    modelType = ModelType.SD,
                    isZip = true,
                    chipsetSuffix = suffix,
                    runOnCpu = false,
                    textEmbeddingSize = 768
                )
            )
        }
        
        models.add(
            HuggingFaceModel(
                id = "anythingv5cpu",
                name = "Anything V5.0 (CPU)",
                description = "Anime-style image generation for CPU",
                fileUri = "xororz/sd-mnn/resolve/main/AnythingV5.zip",
                approximateSize = "1.2GB",
                modelType = ModelType.SD,
                isZip = true,
                runOnCpu = true,
                textEmbeddingSize = 768
            )
        )
        
        models.add(
            HuggingFaceModel(
                id = "qteamixcpu",
                name = "QteaMix (CPU)",
                description = "Chibi-style image generation for CPU",
                fileUri = "xororz/sd-mnn/resolve/main/QteaMix.zip",
                approximateSize = "1.2GB",
                modelType = ModelType.SD,
                isZip = true,
                runOnCpu = true,
                textEmbeddingSize = 768
            )
        )
        
        models.add(
            HuggingFaceModel(
                id = "cuteyukimixcpu",
                name = "CuteYukiMix (CPU)",
                description = "Cute anime characters for CPU",
                fileUri = "xororz/sd-mnn/resolve/main/CuteYukiMix.zip",
                approximateSize = "1.2GB",
                modelType = ModelType.SD,
                isZip = true,
                runOnCpu = true,
                textEmbeddingSize = 768
            )
        )
        
        models.add(
            HuggingFaceModel(
                id = "absoluterealitycpu",
                name = "Absolute Reality (CPU)",
                description = "Photorealistic image generation for CPU",
                fileUri = "xororz/sd-mnn/resolve/main/AbsoluteReality.zip",
                approximateSize = "1.2GB",
                modelType = ModelType.SD,
                isZip = true,
                runOnCpu = true,
                textEmbeddingSize = 768
            )
        )
        
        models.add(
            HuggingFaceModel(
                id = "chilloutmixcpu",
                name = "ChilloutMix (CPU)",
                description = "Realistic portraits for CPU",
                fileUri = "xororz/sd-mnn/resolve/main/ChilloutMix.zip",
                approximateSize = "1.2GB",
                modelType = ModelType.SD,
                isZip = true,
                runOnCpu = true,
                textEmbeddingSize = 768
            )
        )
        
        return models
    }

    private suspend fun getGGUFModels(): List<HuggingFaceModel> {
        val models = mutableListOf<HuggingFaceModel>()

        val response = HuggingFaceClient.api.getRepoFiles("Qwen/Qwen2.5-0.5B-Instruct-GGUF")

        Log.i("ModelStoreRepository", "getGGUFModels: $response")

        if (response.isSuccessful) {
            val files = response.body() ?: emptyList()

            files
                .filter { it.path.endsWith(".gguf") }
                .forEach { file ->
                    val fileName = file.path.substringAfterLast("/")
                    val sizeStr = formatFileSize(file.size ?: 0)

                    models.add(
                        HuggingFaceModel(
                            id = "qwen-${fileName.removeSuffix(".gguf")}",
                            name = "Qwen 2.5 0.5B - $fileName",
                            description = "Qwen2.5 0.5B Instruct quantized model",
                            fileUri = "Qwen/Qwen2.5-0.5B-Instruct-GGUF/resolve/main/${file.path}",
                            approximateSize = sizeStr,
                            modelType = ModelType.GGUF,
                            isZip = false,
                            runOnCpu = false,
                            textEmbeddingSize = 0
                        )
                    )
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