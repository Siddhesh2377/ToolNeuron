package com.mp.ai_engine.workers.model.internal_model_worker

import android.os.IBinder
import com.mp.ai_core.NativeLib
import com.mp.ai_core.services.IGenerationCallback
import com.mp.ai_engine.models.llm_models.GGUFDatabaseModel
import com.mp.ai_engine.models.llm_tasks.GGUFTask
import com.mp.ai_engine.workers.model.SuperModelWorker
import java.io.File

class GGUFModelWorker : SuperModelWorker<GGUFDatabaseModel, GGUFTask>() {

    val nativeLib = NativeLib.getInstance()

    override fun loadModel(modelData: GGUFDatabaseModel): Result<String> {

        val modelFile = File(modelData.modelPath)
        if (!modelFile.exists()){
            return Result.failure(Exception("Model file not found"))
        }

        val result = nativeLib.init(
            modelData.modelPath,
            modelData.threads,
            modelData.ctxSize,
            modelData.temp,
            modelData.topK,
            modelData.topP,
            modelData.minP,
            modelData.mirostat,
            modelData.mirostatTau,
            modelData.mirostatEta,
            modelData.seed,
        )

        return if (result) {
            Result.success("Model Loaded Successfully")
        } else {
            Result.failure(Exception("Failed to load model"))
        }
    }

    override fun unloadModel() {
        nativeLib.nativeRelease()
    }

    override suspend fun runTask(task: GGUFTask) {

        val buffer = StringBuilder()

        try {
            nativeLib.generateStreaming(
                task.input,
                task.maxTokens,
                toolsJson = task.toolJson,
                callback = object : IGenerationCallback {

                    override fun onToken(p0: String?) {
                        val token = p0.orEmpty()
                        buffer.append(token)
                        task.events.onToken(token)
                    }

                    override fun onToolCall(p0: String?, p1: String?) {
                        task.events.onTool(p0.orEmpty(), p1.orEmpty())
                    }

                    override fun onDone() {
                        task.result.complete(buffer.toString())
                    }

                    override fun onError(p0: String?) {
                        task.result.completeExceptionally(
                            RuntimeException(p0 ?: "Unknown error")
                        )
                    }

                    override fun asBinder(): IBinder? = null
                }
            )
        } catch (e: Throwable) {
            task.result.completeExceptionally(e)
        }
    }
}