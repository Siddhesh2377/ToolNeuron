package com.mp.ai_engine.models.llm_models

import android.util.Log
import com.mp.ai_engine.models.image_models.DiffusionDatabaseModel
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

@Serializable
data class CloudModel(
    val modelName: String = "",
    val modelDescription: String = "",
    val providerName: String = "",
    val modelType: ModelType = ModelType.NONE,
    val modelFileSize: String = "",
    val metaData: Map<String, String> = emptyMap()
)

/**
 * Convert CloudModel to GGUFModel
 */
fun CloudModel.toGGUFModel(baseDir: File): GGUFDatabaseModel {
    val meta = metaData
    return GGUFDatabaseModel(
        modelName = modelName,
        modelDescription = modelDescription,
        modelType = modelType,
        modelPath = File(baseDir, "$modelName.gguf").absolutePath,
        modelFileSize = modelFileSize,
        architecture = meta["architecture"]?.let { 
            Architecture.valueOf(it.uppercase()) 
        } ?: Architecture.LLAMA,
        threads = meta["threads"]?.toIntOrNull() ?: 4,
        gpuLayers = meta["gpu-layers"]?.toIntOrNull() ?: 0,
        ctxSize = meta["ctxSize"]?.toIntOrNull() ?: 4096,
        useMMAP = meta["useMMAP"]?.toBooleanStrictOrNull() ?: true,
        useMLOCK = meta["useMLOCK"]?.toBooleanStrictOrNull() ?: false,
        maxTokens = meta["maxTokens"]?.toIntOrNull() ?: 2048,
        temp = meta["temp"]?.toFloatOrNull() ?: 0.7f,
        topK = meta["topK"]?.toIntOrNull() ?: 20,
        topP = meta["topP"]?.toFloatOrNull() ?: 0.5f,
        minP = meta["min-p"]?.toFloatOrNull() ?: 0.0f,
        mirostat = meta["mirostat"]?.toIntOrNull() ?: 0,
        mirostatTau = meta["mirostatTau"]?.toFloatOrNull() ?: 5.0f,
        mirostatEta = meta["mirostatEta"]?.toFloatOrNull() ?: 0.1f,
        seed = meta["seed"]?.toIntOrNull() ?: -1,
        systemPrompt = meta["systemPrompt"] ?: "You are a helpful assistant.",
        chatTemplate = meta["chatTemplate"] ?: "",
        tags = meta["modelTags"] ?: ""
    )
}

/**
 * Convert CloudModel to OpenRouterModel
 */
fun CloudModel.toOpenRouterModel(): OpenRouterDatabaseModel {
    val meta = metaData
    return OpenRouterDatabaseModel(
        id = meta["modelId"] ?: modelName,
        modelName = modelName,
        modelDescription = modelDescription,
        modelType = modelType,
        endpoint = meta["apiEndpoint"] ?: "https://openrouter.ai/api/v1/chat/completions",
        modelId = meta["modelId"] ?: modelName,
        maxTokens = meta["maxTokens"]?.toIntOrNull() ?: 2048,
        temperature = meta["temperature"]?.toFloatOrNull() ?: 0.7f,
        topP = meta["topP"]?.toFloatOrNull() ?: 0.9f,
        frequencyPenalty = meta["frequencyPenalty"]?.toFloatOrNull() ?: 0.0f,
        presencePenalty = meta["presencePenalty"]?.toFloatOrNull() ?: 0.0f,
        ctxSize = meta["ctxSize"]?.toIntOrNull() ?: 4096,
        supportsTools = meta["supportsTools"]?.toBooleanStrictOrNull() ?: false,
        supportsVision = meta["supportsVision"]?.toBooleanStrictOrNull() ?: false,
        supportsStreaming = meta["supportsStreaming"]?.toBooleanStrictOrNull() ?: true,
        promptCostPer1M = meta["promptCostPer1M"]?.toFloatOrNull() ?: 0.0f,
        completionCostPer1M = meta["completionCostPer1M"]?.toFloatOrNull() ?: 0.0f,
        tags = meta["tags"] ?: ""
    )
}

/**
 * Convert CloudModel to SherpaTTSModel
 */
fun CloudModel.toSherpaTTSModel(baseDir: File): SherpaTTSDatabaseModel {
    val meta = metaData
    val modelDir = File(baseDir, modelName)

    val voicesList = meta["voices"]?.let { voicesJson ->
        try {
            Json.decodeFromString<List<Voices>>(voicesJson)
        } catch (e: Exception) {
            Log.e("SherpaTTSDatabaseModel", "Error parsing voices JSON: ${e.message}")
            listOf()
        }
    } ?: listOf()

    return SherpaTTSDatabaseModel(
        modelName = modelName,
        modelDescription = modelDescription,
        modelFileSize = modelFileSize,
        modelDir = modelDir.absolutePath,
        modelFileName = meta["modelFileName"] ?: "",
        voicesFileName = meta["voicesFileName"] ?: "",
        dataDirName = meta["dataDirName"] ?: "",
        voices = voicesList,
    )
}

/**
 * Convert CloudModel to SherpaSTTModel
 */
fun CloudModel.toSherpaSTTModel(baseDir: File): SherpaSTTDatabaseModel {
    val meta = metaData
    val modelDir = File(baseDir, modelName)

    return SherpaSTTDatabaseModel(
        modelName = modelName,
        modelDescription = modelDescription,
        modelFileSize = modelFileSize,
        modelDir = modelDir.absolutePath,
        encoder = meta["encoder"] ?: "",
        decoder = meta["decoder"] ?: "",
        tokens = meta["tokens"] ?: meta["tokens-txt"] ?: ""
    )
}

fun CloudModel.toDiffusionModel(baseDir: File): DiffusionDatabaseModel {
    val meta = metaData
    val modelDir = File(baseDir, modelName)
    val runOnCPU = meta["run-on-cpu"].toBoolean()


    return DiffusionDatabaseModel(
        name = modelName,
        description = modelDescription,
        modelFolder = modelDir.absolutePath,
        runOnCpu = runOnCPU,
    )
}