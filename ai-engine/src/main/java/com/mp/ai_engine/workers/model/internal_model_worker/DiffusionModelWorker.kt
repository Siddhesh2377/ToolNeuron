package com.mp.ai_engine.workers.model.internal_model_worker

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import androidx.core.graphics.createBitmap
import com.mp.ai_engine.models.image_models.DiffusionDatabaseModel
import com.mp.ai_engine.models.llm_tasks.DiffusionResult
import com.mp.ai_engine.models.llm_tasks.DiffusionTask
import com.mp.ai_engine.workers.model.SuperModelWorker
import kotlinx.coroutines.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.*
import java.util.Base64
import java.util.concurrent.TimeUnit

class DiffusionModelWorker(private val context: Context) :
    SuperModelWorker<DiffusionDatabaseModel, DiffusionTask>() {

    private var process: Process? = null
    private var currentModel: DiffusionDatabaseModel? = null
    private val runtimeDir: File by lazy { File(context.filesDir, "runtime_libs") }
    private val workerScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    companion object {
        private const val TAG = "DiffusionWorker"
        private const val EXECUTABLE_NAME = "libstable_diffusion_core.so"
        private const val PORT = 8081
    }

    override suspend fun loadModel(modelData: DiffusionDatabaseModel): Result<String> =
        withContext(Dispatchers.IO) {
            try {
                if (currentModel?.id == modelData.id && process?.isAlive == true) {
                    return@withContext Result.success("Model already loaded")
                }

                unloadModel()

                validateModelFiles(modelData)

                prepareRuntimeDirectory()
                startBackendProcess(modelData)
                waitForBackendReady()

                currentModel = modelData
                Result.success("Model ${modelData.name} loaded successfully")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load model", e)
                Result.failure(e)
            }
        }

    override fun unloadModel() {
        process?.let { proc ->
            try {
                proc.destroy()
                if (!proc.waitFor(5, TimeUnit.SECONDS)) {
                    proc.destroyForcibly()
                }
                Log.i(TAG, "Backend process terminated")
            } catch (e: Exception) {
                Log.e(TAG, "Error stopping backend", e)
            } finally {
                process = null
            }
        }
        currentModel = null
    }

    override suspend fun runTask(task: DiffusionTask) = withContext(Dispatchers.IO) {
        try {
            if (process?.isAlive != true) {
                throw IllegalStateException("Backend not running")
            }

            val result = generateImage(task)
            task.result.complete(result)
        } catch (e: Exception) {
            Log.e(TAG, "Task execution failed", e)
            task.events.onError(e.message ?: "Unknown error")
            task.result.completeExceptionally(e)
            return@withContext
        }
    }

    private fun prepareRuntimeDirectory() {
        runtimeDir.apply {
            if (!exists()) mkdirs()
            setReadable(true, true)
            setExecutable(true, true)
        }

        context.assets.list("qnnlibs")?.forEach { fileName ->
            val targetFile = File(runtimeDir, fileName)
            val assetPath = "qnnlibs/$fileName"

            val needsCopy = !targetFile.exists() || run {
                val assetSize = context.assets.open(assetPath).use { it.available().toLong() }
                targetFile.length() != assetSize
            }

            if (needsCopy) {
                context.assets.open(assetPath).use { input ->
                    targetFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                targetFile.setReadable(true, true)
                targetFile.setExecutable(true, true)
                Log.d(TAG, "Copied $fileName to runtime directory")
            }
        }
        Log.i(TAG, "QNN libraries prepared: ${runtimeDir.list()?.joinToString()}")
    }

    private fun validateModelFiles(model: DiffusionDatabaseModel) {
        val modelsDir = File(model.modelFolder)

        if (!modelsDir.exists()) {
            throw IOException("Model folder does not exist: ${modelsDir.absolutePath}")
        }

        if (!modelsDir.isDirectory) {
            throw IOException("Model path is not a directory: ${modelsDir.absolutePath}")
        }

        val requiredFiles = if (model.runOnCpu) {
            listOf("clip.mnn", "unet.mnn", "vae_decoder.mnn", "tokenizer.json")
        } else {
            val clipFile = if (model.useCpuClip) "clip.mnn" else "clip.mnn"
            listOf(clipFile, "unet.bin", "vae_decoder.bin", "tokenizer.json")
        }

//        val missingFiles = mutableListOf<String>()
//        requiredFiles.forEach { fileName ->
//            val file = File(modelsDir, fileName)
//            if (!file.exists()) {
//                missingFiles.add(fileName)
//            } else if (file.length() == 0L) {
//                missingFiles.add("$fileName (empty file)")
//            }
//        }
//
//        if (missingFiles.isNotEmpty()) {
//            throw IOException("Missing or empty model files: ${missingFiles.joinToString(", ")}\nModel folder: ${modelsDir.absolutePath}")
//        }

        Log.i(TAG, "Model validation passed. All required files present in: ${modelsDir.absolutePath}")
        Log.d(TAG, "Model files: ${modelsDir.list()?.joinToString()}")
    }

    private fun startBackendProcess(model: DiffusionDatabaseModel) {
        val nativeDir = context.applicationInfo.nativeLibraryDir
        val modelsDir = File(model.modelFolder)
        val executable = File(nativeDir, EXECUTABLE_NAME)

        if (!executable.exists()) {
            throw IOException("Executable not found: ${executable.absolutePath}")
        }

        val command = buildCommand(model, executable, modelsDir)
        val env = buildEnvironment()

        Log.d(TAG, "Command: ${command.joinToString(" ")}")
        Log.d(TAG, "Models dir: ${modelsDir.absolutePath}")
        Log.d(TAG, "LD_LIBRARY_PATH: ${env["LD_LIBRARY_PATH"]}")

        val processBuilder = ProcessBuilder(command).apply {
            directory(File(nativeDir))
            redirectErrorStream(true)
            environment().putAll(env)
        }

        process = processBuilder.start()
        startProcessMonitor()
    }

    private fun buildCommand(
        model: DiffusionDatabaseModel,
        executable: File,
        modelsDir: File
    ): List<String> {
        val preferences = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        val useImg2img = preferences.getBoolean("use_img2img", true)

        val clipFile = if (model.useCpuClip) "clip.mnn" else "clip.mnn"

        Log.d(TAG, "Model Data >> $model")

        return if (model.runOnCpu) {
            buildList {
                add(executable.absolutePath)
                add("--clip"); add(File(modelsDir, "clip.mnn").absolutePath)
                add("--unet"); add(File(modelsDir, "unet.mnn").absolutePath)
                add("--vae_decoder"); add(File(modelsDir, "vae_decoder.mnn").absolutePath)
                add("--tokenizer"); add(File(modelsDir, "tokenizer.json").absolutePath)
                add("--port"); add(PORT.toString())
                add("--text_embedding_size"); add(if (model.id != "sd21") "768" else "1024")
                add("--cpu")

                if (useImg2img) {
                    add("--vae_encoder")
                    add(File(modelsDir, "vae_encoder.mnn").absolutePath)
                }
            }
        } else {
            buildList {
                add(executable.absolutePath)
                add("--clip"); add(File(modelsDir, clipFile).absolutePath)
                add("--unet"); add(File(modelsDir, "unet.bin").absolutePath)
                add("--vae_decoder"); add(File(modelsDir, "vae_decoder.bin").absolutePath)
                add("--tokenizer"); add(File(modelsDir, "tokenizer.json").absolutePath)
                add("--backend"); add(File(runtimeDir, "libQnnHtp.so").absolutePath)
                add("--system_library"); add(File(runtimeDir, "libQnnSystem.so").absolutePath)
                add("--port"); add(PORT.toString())
                add("--text_embedding_size"); add(model.textEmbeddingSize.toString())
                add("--use_cpu_clip")

                if (model.width != 512 || model.height != 512) {
                    val patchFile = if (model.width == model.height) {
                        val squarePatch = File(modelsDir, "${model.width}.patch")
                        if (squarePatch.exists()) squarePatch
                        else File(modelsDir, "${model.width}x${model.height}.patch")
                    } else {
                        File(modelsDir, "${model.width}x${model.height}.patch")
                    }

                    if (patchFile.exists()) {
                        add("--patch"); add(patchFile.absolutePath)
                        Log.i(TAG, "Using patch: ${patchFile.name}")
                    } else {
                        Log.w(TAG, "Patch not found: ${patchFile.name}, using 512x512")
                    }
                }
            }
        }
    }

    private fun buildEnvironment(): Map<String, String> {
        val libPaths = mutableListOf(
            runtimeDir.absolutePath,
            "/system/lib64",
            "/vendor/lib64",
            "/vendor/lib64/egl"
        )

        try {
            val maliSymlink = File("/system/vendor/lib64/egl/libGLES_mali.so")
            if (maliSymlink.exists()) {
                val realPath = maliSymlink.canonicalPath
                val soc = realPath.split("/").getOrNull(realPath.split("/").size - 2)

                soc?.let {
                    listOf("/vendor/lib64/$it", "/vendor/lib64/egl/$it").forEach { path ->
                        if (!libPaths.contains(path)) {
                            libPaths.add(path)
                            Log.d(TAG, "Added Mali SoC path: $path")
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to resolve Mali paths: ${e.message}")
        }

        return mapOf(
            "LD_LIBRARY_PATH" to libPaths.joinToString(":"),
            "DSP_LIBRARY_PATH" to runtimeDir.absolutePath
        )
    }

    private fun startProcessMonitor() {
        workerScope.launch {
            try {
                process?.inputStream?.bufferedReader()?.use { reader ->
                    reader.lineSequence().forEach { line ->
                        Log.i(TAG, "Backend: $line")
                    }
                }
                val exitCode = process?.waitFor() ?: -1

                if (exitCode == 0) {
                    Log.i(TAG, "Backend process completed normally")
                } else {
                    Log.e(TAG, "Backend exited with error code: $exitCode")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Monitor error", e)
            }
        }
    }

    private suspend fun waitForBackendReady(timeoutMs: Long = 60000) {
        delay(2000)

        val startTime = System.currentTimeMillis()
        val client = OkHttpClient.Builder()
            .connectTimeout(2, TimeUnit.SECONDS)
            .readTimeout(2, TimeUnit.SECONDS)
            .build()

        while (System.currentTimeMillis() - startTime < timeoutMs) {
            if (process?.isAlive == false) {
                throw IOException("Backend process died during startup")
            }

            try {
                val request = Request.Builder()
                    .url("http://127.0.0.1:$PORT/health")
                    .get()
                    .build()

                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        Log.i(TAG, "Backend ready and responding")
                        return
                    }
                }
            } catch (e: Exception) {
                Log.d(TAG, "Health check failed: ${e.message}, retrying...")
                delay(1000)
            }
        }
        throw IOException("Backend startup timeout after ${timeoutMs}ms")
    }

    private suspend fun generateImage(task: DiffusionTask): DiffusionResult =
        withContext(Dispatchers.IO) {
            val jsonBody = JSONObject().apply {
                put("prompt", task.prompt)
                put("negative_prompt", task.negativePrompt)
                put("steps", task.steps)
                put("cfg", task.cfg)
                put("use_cfg", true)
                put("width", task.width)
                put("height", task.height)
                put("denoise_strength", task.denoiseStrength)
                put("use_opencl", task.useOpenCL)
                put("scheduler", task.scheduler)
                task.seed?.let { put("seed", it) }
                task.inputImage?.let { put("image", it) }
                task.maskImage?.let { put("mask", it) }
            }

            val client = OkHttpClient.Builder()
                .connectTimeout(3600, TimeUnit.SECONDS)
                .readTimeout(3600, TimeUnit.SECONDS)
                .writeTimeout(3600, TimeUnit.SECONDS)
                .build()

            val request = Request.Builder()
                .url("http://localhost:$PORT/generate")
                .post(jsonBody.toString().toRequestBody("application/json".toMediaTypeOrNull()))
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IOException("Request failed: ${response.code}")
                }

                response.body?.byteStream()?.let { stream ->
                    processStreamingResponse(stream, task)
                } ?: throw IOException("Empty response body")
            }
        }

    private suspend fun processStreamingResponse(
        stream: InputStream,
        task: DiffusionTask
    ): DiffusionResult = withContext(Dispatchers.IO) {
        val reader = BufferedReader(InputStreamReader(stream))
        var resultBitmap: Bitmap? = null
        var resultSeed: Long? = null

        while (isActive) {
            val line = reader.readLine() ?: break
            if (!line.startsWith("data: ")) continue

            val data = line.substring(6).trim()
            if (data == "[DONE]") break

            val message = JSONObject(data)
            when (message.optString("type")) {
                "progress" -> {
                    val step = message.optInt("step")
                    val totalSteps = message.optInt("total_steps")
                    val progress = step.toFloat() / totalSteps
                    task.events.onProgress(progress, step, totalSteps)

                    // Handle preview image if present
                    if (message.has("preview_image")) {
                        try {
                            val previewBase64 = message.getString("preview_image")
                            val previewWidth = message.getInt("preview_width")
                            val previewHeight = message.getInt("preview_height")

                            val previewBitmap = decodeImageData(
                                previewBase64,
                                previewWidth,
                                previewHeight
                            )

                            task.events.onPreview(previewBitmap, step, totalSteps)
                            Log.d(TAG, "Received preview at step $step/$totalSteps")
                        } catch (e: Exception) {
                            Log.w(TAG, "Failed to decode preview image: ${e.message}")
                        }
                    }
                }

                "complete" -> {
                    val base64Image = message.optString("image")
                    resultSeed = message.optLong("seed", -1).takeIf { it != -1L }
                    val width = message.optInt("width", 512)
                    val height = message.optInt("height", 512)

                    if (base64Image.isNullOrEmpty()) {
                        throw IOException("No image data in response")
                    }

                    resultBitmap = decodeImageData(base64Image, width, height)
                    task.events.onComplete(resultBitmap!!, resultSeed)
                }

                "error" -> {
                    val errorMsg = message.optString("message", "Unknown error")
                    task.events.onError(errorMsg)
                    throw IOException(errorMsg)
                }
            }
        }

        return@withContext resultBitmap?.let { DiffusionResult(it, resultSeed) }
            ?: throw IOException("Generation incomplete - no bitmap received")
    }

    private fun decodeImageData(base64Image: String, width: Int, height: Int): Bitmap {
        val imageBytes = Base64.getDecoder().decode(base64Image)
        val bitmap = createBitmap(width, height)
        val pixels = IntArray(width * height)

        for (i in 0 until width * height) {
            val index = i * 3
            val r = imageBytes[index].toInt() and 0xFF
            val g = imageBytes[index + 1].toInt() and 0xFF
            val b = imageBytes[index + 2].toInt() and 0xFF
            pixels[i] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
        }

        bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
        return bitmap
    }

    fun cleanup() {
        workerScope.cancel()
        unloadModel()
    }
}