package com.dark.tool_neuron.worker

import android.content.Context
import com.dark.ai_module.model.*
import com.mp.ai_engine.models.llm_models.ModelProvider
import kotlinx.coroutines.Dispatchers
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
        Result.success(ModelData())
//        try {
//            when (provider) {
//                ModelProvider.OpenRouter -> installOpenRouterModel(
//                    name, url, modelType, onComplete
//                )
//
//                ModelProvider.LocalGGUF -> installLocalGGUFModel(
//                    context, name, url, fileName, modelType, onProgress, onComplete, onError
//                )
//
//                ModelProvider.HuggingFace -> installLocalGGUFModel(
//                    context, name, url, fileName, modelType, onProgress, onComplete, onError
//                )
//
//                ModelProvider.SherpaONNX -> installSherpaModel(
//                    context, name, url, fileName, modelType, onProgress, onComplete, onError
//                )
//            }
//        } catch (e: Exception) {
//            onError(e)
//            Result.failure(e)
//        }
    }

    // ---------------------
    //  LOCAL GGUF MODELS
    // ---------------------


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
