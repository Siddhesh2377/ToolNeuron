package com.mp.ai_engine.workers.aidl

import android.graphics.Bitmap
import android.util.Log
import com.mp.ai_engine.diffusion.IDiffusionCallback
import com.mp.ai_engine.diffusion.IDiffusionOperations
import com.mp.ai_engine.models.image_models.DiffusionDatabaseModel
import com.mp.ai_engine.models.llm_models.GGUFDatabaseModel
import com.mp.ai_engine.models.llm_models.ModelType
import com.mp.ai_engine.models.llm_tasks.DMStreamEvents
import com.mp.ai_engine.models.llm_tasks.DiffusionTask
import com.mp.ai_engine.workers.model.internal_model_worker.DiffusionModelWorker
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.io.File
import java.util.UUID

/**
 * AIDL implementation for Diffusion operations
 * Wraps DiffusionModelWorker and provides HardwareBuffer image transfer
 */
class DiffusionOperationsImpl(
    private val worker: DiffusionModelWorker
) : IDiffusionOperations.Stub() {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var currentModelId: String? = null
    private var currentModelConfig: DiffusionDatabaseModel? = null

    companion object {
        private const val TAG = "DiffusionOperationsImpl"
    }


    override fun loadModel(
        config: String
    ): Boolean {
        val result = CompletableDeferred<Result<String>>()

        scope.launch {
            try {
                val modelData = DiffusionDatabaseModel.fromJson(config)
                val loadResult = worker.loadModel(modelData)
                if (loadResult.isSuccess){
                    currentModelId = modelData.id
                    currentModelConfig = modelData
                }
                result.complete(loadResult)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load model", e)
                result.complete(Result.failure(Exception("Failed to load model", e)))
            }
        }

        // This *suspends* internally, does NOT block a thread
        return runBlocking {
            if (result.await().isSuccess) {
                Log.i(TAG, "EMBEDDING model loaded successfully")
                true
            } else {
                Log.e(TAG, "Failed to load EMBEDDING model: ${result.await().exceptionOrNull()?.message}")
                false
            }
        }
    }

    override fun unloadModel() {
        worker.unloadModel()
        scope.cancel()
    }

    override fun generateImage(
        prompt: String,
        negativePrompt: String,
        steps: Int,
        cfg: Float,
        width: Int,
        height: Int,
        denoiseStrength: Float,
        useOpenCL: Boolean,
        scheduler: String,
        seed: Long,
        callback: IDiffusionCallback
    ) {
        if (currentModelConfig == null) {
            callback.onError("No model loaded")
            return
        }

        val task = DiffusionTask(
            id = UUID.randomUUID().toString(),
            prompt = prompt,
            negativePrompt = negativePrompt,
            steps = steps,
            cfg = cfg,
            width = width,
            height = height,
            denoiseStrength = denoiseStrength,
            useOpenCL = useOpenCL,
            scheduler = scheduler,
            seed = seed,
            result = CompletableDeferred(),
            events = object : DMStreamEvents {
                override fun onProgress(p: Float, step: Int, totalSteps: Int) {
                    callback.onProgress(p, step, totalSteps)
                }

                override fun onPreview(
                    previewBitmap: Bitmap,
                    step: Int,
                    totalSteps: Int
                ) {
                    callback.onPreview(previewBitmap, step, totalSteps)
                }

                override fun onComplete(bitmap: Bitmap, seed: Long?) {
                    callback.onComplete(bitmap, seed ?: 0)
                }

                override fun onError(error: String) {
                    callback.onError(error)
                }
            }
        )

        scope.launch {
            try {
                worker.runTask(task)
                val output = task.result.await()
                callback.onComplete(output.bitmap, output.seed ?: 0)
            }catch (ex: Exception){
                Log.e(TAG, "Error generating text: ${ex.message}")
                callback.onError(ex.message ?: "Unknown error")
            }
        }
    }

    override fun generateImageFromImage(
        prompt: String?,
        negativePrompt: String?,
        inputImageBase64: String?,
        maskImageBase64: String?,
        steps: Int,
        cfg: Float,
        width: Int,
        height: Int,
        denoiseStrength: Float,
        useOpenCL: Boolean,
        scheduler: String?,
        seed: Long,
        callback: IDiffusionCallback?
    ) {
        TODO("Not yet implemented")
    }

    override fun isModelLoaded(): Boolean {
        TODO("Not yet implemented")
    }

    override fun getCurrentModelId(): String {
        TODO("Not yet implemented")
    }
}