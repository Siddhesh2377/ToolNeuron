package com.mp.ai_engine.workers.aidl

import android.util.Log
import com.mp.ai_engine.gguf.IGGUFCallback
import com.mp.ai_engine.gguf.IGGUFOperations
import com.mp.ai_engine.models.llm_models.GGUFDatabaseModel
import com.mp.ai_engine.models.llm_models.ModelType
import com.mp.ai_engine.models.llm_tasks.GGUFStreamEvents
import com.mp.ai_engine.models.llm_tasks.GGUFTask
import com.mp.ai_engine.models.llm_tasks.GGUFTaskType
import com.mp.ai_engine.workers.model.internal_model_worker.GGUFModelWorker
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.io.File
import java.util.UUID

class GGUFOperationsImpl(private val worker: GGUFModelWorker) : IGGUFOperations.Stub() {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var currentModelPath: String? = null
    private var currentModelType: ModelType? = null

    companion object {
        private const val TAG = "GGUFOperationsImpl"
    }

    override fun loadTextModel(
        config: String
    ): Boolean {

        val result = CompletableDeferred<Result<String>>()

        scope.launch {
            try {
                val modelData = GGUFDatabaseModel.fromJson(config)
                val loadResult = worker.loadModel(modelData)
                result.complete(loadResult)
                currentModelPath = modelData.modelPath
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load model", e)
                result.complete(Result.failure(Exception("Failed to load model", e)))
            }
        }

        // This *suspends* internally, does NOT block a thread
        return runBlocking {
            if (result.await().isSuccess) {
                currentModelType = ModelType.TEXT
                Log.i(TAG, "Text model loaded successfully")
                true
            } else {
                Log.e(TAG, "Failed to load text model: ${result.await().exceptionOrNull()?.message}")
                false
            }
        }
    }


    override fun loadEmbeddingModel(
        modelPath: String, threads: Int, ctxSize: Int
    ): Boolean {
        val result = CompletableDeferred<Result<String>>()

        scope.launch {
            try {
                val modelData = GGUFDatabaseModel(
                    modelName = File(modelPath).name,
                    modelPath = modelPath,
                    modelType = ModelType.TEXT,
                    threads = threads,
                    ctxSize = ctxSize,
                )
                val loadResult = worker.loadModel(modelData)
                result.complete(loadResult)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load model", e)
                result.complete(Result.failure(Exception("Failed to load model", e)))
            }
        }

        // This *suspends* internally, does NOT block a thread
        return runBlocking {
            if (result.await().isSuccess) {
                currentModelPath = modelPath
                currentModelType = ModelType.EMBEDDING
                Log.i(TAG, "EMBEDDING model loaded successfully")
                true
            } else {
                Log.e(TAG, "Failed to load EMBEDDING model: ${result.await().exceptionOrNull()?.message}")
                false
            }
        }
    }

    override fun unloadModel() {
        try {
            Log.i(TAG, "Unloading model")
            worker.unloadModel()
            currentModelPath = null
            currentModelType = null
            Log.i(TAG, "Model unloaded successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error unloading model", e)
        }
    }

    override fun generateText(
        prompt: String, maxTokens: Int, toolsJson: String, callback: IGGUFCallback
    ) {
        if (currentModelType != ModelType.TEXT) {
            callback.onError("No text model loaded")
            return
        }

        val task = GGUFTask(
            taskType = GGUFTaskType.GENERATE,
            input = prompt,
            maxTokens = maxTokens,
            toolJson = toolsJson,
            result = CompletableDeferred(),
            resultEmbedded = CompletableDeferred(),
            events = object : GGUFStreamEvents {
                override fun onToken(token: String) {
                    callback.onNewToken(token)
                }

                override fun onTool(toolName: String, toolArgs: String) {
                    callback.onToolCall(toolName, toolArgs)
                }
            }
        )

        scope.launch {
            try {
                worker.runTask(task)
                val output = task.result.await()
                callback.onComplete(output)
            }catch (ex: Exception){
                Log.e(TAG, "Error generating text: ${ex.message}")
                callback.onError(ex.message ?: "Unknown error")
            }
        }
    }

    override fun generateEmbedding(input: String?): FloatArray {
        TODO("Not yet implemented")
    }

    override fun isModelLoaded(): Boolean {
        return currentModelPath != null
    }

    override fun getCurrentModelPath(): String? {
        return currentModelPath
    }
}