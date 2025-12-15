package com.mp.ai_engine.workers.model.internal_model_worker

import com.mp.ai_core.NativeLib
import com.mp.ai_engine.models.llm_models.GGUFDatabaseModel
import com.mp.ai_engine.models.llm_tasks.GGUFInferenceEvent
import com.mp.ai_engine.models.llm_tasks.GGUFTask
import com.mp.ai_engine.workers.model.SuperModelWorker
import java.io.File

class GGUFModelWorker : SuperModelWorker<GGUFDatabaseModel, Pair<GGUFTask, GGUFInferenceEvent>>() {

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

    override suspend fun runTask(task: Pair<GGUFTask, GGUFInferenceEvent>) {

    }

}