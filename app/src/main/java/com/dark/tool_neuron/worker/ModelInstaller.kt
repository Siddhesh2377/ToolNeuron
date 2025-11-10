package com.dark.tool_neuron.worker

import android.content.Context
import com.dark.ai_module.model.*
import com.dark.ai_module.workers.ModelManager
import com.dark.ai_module.workers.downloadFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.*
import java.util.zip.ZipInputStream

object ModelInstaller {

    suspend fun installModel(
        context: Context,
        name: String,
        url: String,
        fileName: String,
        provider: ModelProvider,
        modelType: ModelType,
        onProgress: (Float) -> Unit,
        onComplete: () -> Unit,
        onError: (Exception) -> Unit
    ): Result<ModelData> = withContext(Dispatchers.IO) {
        try {
            when (provider) {
                ModelProvider.OpenRouter -> installOpenRouterModel(
                    name, url, modelType, onComplete
                )

                ModelProvider.LocalGGUF -> installLocalGGUFModel(
                    context, name, url, fileName, modelType, onProgress, onComplete, onError
                )

                ModelProvider.HuggingFace -> installLocalGGUFModel(
                    context, name, url, fileName, modelType, onProgress, onComplete, onError
                )

                ModelProvider.SherpaONNX -> installSherpaModel(
                    context, name, url, fileName, modelType, onProgress, onComplete, onError
                )
            }
        } catch (e: Exception) {
            onError(e)
            Result.failure(e)
        }
    }

    // ---------------------
    //  LOCAL GGUF MODELS
    // ---------------------
    private suspend fun installLocalGGUFModel(
        context: Context,
        name: String,
        url: String,
        fileName: String,
        modelType: ModelType,
        onProgress: (Float) -> Unit,
        onComplete: () -> Unit,
        onError: (Exception) -> Unit
    ): Result<ModelData> = withContext(Dispatchers.IO) {
        val modelsDir = File(context.filesDir, "models")
        if (!modelsDir.exists()) modelsDir.mkdirs()

        val outputFile = File(modelsDir, fileName)
        downloadFile(url, outputFile, onProgress, {
            val modelData = ModelData(
                modelName = name,
                providerName = ModelProvider.LocalGGUF.toString(),
                modelType = modelType,
                modelPath = outputFile.absolutePath,
                modelUrl = url,
                isImported = true
            )
            this.launch {
                ModelManager.addModel(modelData)
            }
            onComplete()
        }, onError)

        Result.success(
            ModelData(
                modelName = name,
                providerName = ModelProvider.LocalGGUF.toString(),
                modelType = modelType,
                modelPath = outputFile.absolutePath,
                modelUrl = url,
                isImported = true
            )
        )
    }

    // ---------------------
    //  SHERPA ONNX MODELS
    // ---------------------
    private suspend fun installSherpaModel(
        context: Context,
        name: String,
        url: String,
        fileName: String,
        modelType: ModelType,
        onProgress: (Float) -> Unit,
        onComplete: () -> Unit,
        onError: (Exception) -> Unit
    ): Result<ModelData> = withContext(Dispatchers.IO) {
        val modelsDir = File(context.filesDir, "models/${modelType.name.lowercase()}")
        if (!modelsDir.exists()) modelsDir.mkdirs()

        val archiveFile = File(modelsDir, fileName)
        downloadFile(url, archiveFile, onProgress, {
            val extractDir = File(modelsDir, name.replace(" ", "_"))
            unzipFlatten(archiveFile, extractDir)
            archiveFile.delete()

            val modelData = ModelData(
                modelName = name,
                providerName = ModelProvider.SherpaONNX.toString(),
                modelType = modelType,
                modelPath = extractDir.absolutePath,
                modelUrl = url,
                isImported = true
            )
            this.launch {
                ModelManager.addModel(modelData)
            }
            onComplete()
        }, onError)

        Result.success(
            ModelData(
                modelName = name,
                providerName = ModelProvider.SherpaONNX.toString(),
                modelType = modelType,
                modelPath = archiveFile.absolutePath,
                modelUrl = url,
                isImported = true
            )
        )
    }

    // ---------------------
    //  OPENROUTER MODELS
    // ---------------------
    private suspend fun installOpenRouterModel(
        name: String,
        url: String,
        modelType: ModelType,
        onComplete: () -> Unit
    ): Result<ModelData> = withContext(Dispatchers.IO) {
        val modelData = ModelData(
            modelName = name,
            providerName = ModelProvider.OpenRouter.toString(),
            modelType = modelType,
            modelUrl = url,
            isImported = false
        )

        ModelManager.addModel(modelData)
        onComplete()
        Result.success(modelData)
    }

    // ---------------------
    //  UTILITIES
    // ---------------------
    private fun unzipFlatten(zipFile: File, destDir: File) {
        if (!destDir.exists()) destDir.mkdirs()
        ZipInputStream(BufferedInputStream(FileInputStream(zipFile))).use { zipIn ->
            var entry = zipIn.nextEntry
            val buffer = ByteArray(8192)
            while (entry != null) {
                val normalizedName = entry.name.substringAfter('/')
                if (normalizedName.isEmpty()) {
                    entry = zipIn.nextEntry
                    continue
                }

                val outputFile = File(destDir, normalizedName)
                if (entry.isDirectory) {
                    outputFile.mkdirs()
                } else {
                    outputFile.parentFile?.mkdirs()
                    BufferedOutputStream(FileOutputStream(outputFile)).use { out ->
                        var len: Int
                        while (zipIn.read(buffer).also { len = it } != -1) out.write(buffer, 0, len)
                    }
                }
                zipIn.closeEntry()
                entry = zipIn.nextEntry
            }
        }
    }
}
