package com.dark.tool_neuron.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.dark.tool_neuron.data.AppSettingsDataStore
import com.dark.tool_neuron.di.AppContainer
import com.dark.tool_neuron.global.AppPaths
import com.dark.tool_neuron.global.DeviceTuner
import com.dark.tool_neuron.global.HardwareScanner
import com.dark.tool_neuron.models.engine_schema.GgufEngineSchema
import com.dark.tool_neuron.models.enums.PathType
import com.dark.tool_neuron.models.enums.ProviderType
import com.dark.tool_neuron.models.table_schema.Model
import com.dark.tool_neuron.models.table_schema.ModelConfig
import com.dark.tool_neuron.worker.DiffusionConfig
import com.dark.tool_neuron.worker.DiffusionInferenceParams
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.zip.ZipInputStream

class ModelDownloadService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val downloadJobs = ConcurrentHashMap<String, Job>()
    private val notificationIdCounter = java.util.concurrent.atomic.AtomicInteger(NOTIFICATION_ID)

    private val notificationManager by lazy {
        getSystemService(NOTIFICATION_SERVICE) as NotificationManager
    }

    private val client = OkHttpClient.Builder().connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS).build()

    companion object {
        private const val NOTIFICATION_CHANNEL_ID = "model_download_channel"
        private const val NOTIFICATION_ID = 3001

        private val _downloadStates = MutableStateFlow<Map<String, DownloadState>>(emptyMap())
        val downloadStates: StateFlow<Map<String, DownloadState>> = _downloadStates

        const val ACTION_START_DOWNLOAD = "action_start_download"
        const val ACTION_CANCEL_DOWNLOAD = "action_cancel_download"

        const val EXTRA_MODEL_ID = "model_id"
        const val EXTRA_MODEL_NAME = "model_name"
        const val EXTRA_FILE_URL = "file_url"
        const val EXTRA_IS_ZIP = "is_zip"
        const val EXTRA_MODEL_TYPE = "model_type"
        const val EXTRA_RUN_ON_CPU = "run_on_cpu"
        const val EXTRA_TEXT_EMBEDDING_SIZE = "text_embedding_size"
    }

    sealed class DownloadState {
        data class Downloading(
            val modelId: String,
            val progress: Float,
            val downloadedBytes: Long,
            val totalBytes: Long,
            val speedBytesPerSec: Long = 0,
            val etaSeconds: Long = -1
        ) : DownloadState()

        data class Extracting(
            val modelId: String,
            val currentFile: String = "",
            val extractedCount: Int = 0,
            val totalFiles: Int = 0
        ) : DownloadState()
        data class Processing(val modelId: String) : DownloadState()
        data class Success(val modelId: String) : DownloadState()
        data class Error(val modelId: String, val message: String) : DownloadState()
        data class Cancelled(val modelId: String) : DownloadState()
    }

    private fun updateDownloadState(modelId: String, state: DownloadState?) {
        _downloadStates.value = if (state == null) {
            _downloadStates.value - modelId
        } else {
            _downloadStates.value + (modelId to state)
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_DOWNLOAD -> {
                val modelId = intent.getStringExtra(EXTRA_MODEL_ID) ?: return START_NOT_STICKY
                val modelName = intent.getStringExtra(EXTRA_MODEL_NAME) ?: modelId
                val fileUrl = intent.getStringExtra(EXTRA_FILE_URL) ?: return START_NOT_STICKY
                val isZip = intent.getBooleanExtra(EXTRA_IS_ZIP, false)
                val modelType = intent.getStringExtra(EXTRA_MODEL_TYPE) ?: "GGUF"
                val runOnCpu = intent.getBooleanExtra(EXTRA_RUN_ON_CPU, false)
                val textEmbeddingSize = intent.getIntExtra(EXTRA_TEXT_EMBEDDING_SIZE, 768)

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    ServiceCompat.startForeground(
                        this@ModelDownloadService, NOTIFICATION_ID,
                        createNotification(modelName, 0f),
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                    )
                } else {
                    startForeground(NOTIFICATION_ID, createNotification(modelName, 0f))
                }
                startDownload(
                    modelId,
                    modelName,
                    fileUrl,
                    isZip,
                    modelType,
                    runOnCpu,
                    textEmbeddingSize
                )
            }

            ACTION_CANCEL_DOWNLOAD -> {
                val modelId = intent.getStringExtra(EXTRA_MODEL_ID)
                if (modelId != null) {
                    cancelDownload(modelId)
                }
            }
        }
        return START_NOT_STICKY
    }

    private fun startDownload(
        modelId: String,
        modelName: String,
        fileUrl: String,
        isZip: Boolean,
        modelType: String,
        runOnCpu: Boolean,
        textEmbeddingSize: Int
    ) {
        // Skip if this model is already downloading
        if (downloadJobs[modelId]?.isActive == true) {
            Log.w("DownloadService", "Download already in progress for $modelId, skipping duplicate")
            return
        }
        downloadJobs[modelId]?.cancel()

        val notificationId = notificationIdCounter.incrementAndGet()
        val job = serviceScope.launch {
            var tempFile: File? = null
            var extractTempDir: File? = null
            try {
                updateDownloadState(modelId, DownloadState.Downloading(modelId, 0f, 0, 0))

                val tempDir = AppPaths.tempDownloads(applicationContext, modelId)
                if (tempDir.exists()) {
                    tempDir.deleteRecursively()
                }
                tempDir.mkdirs()

                // TTS downloads files directly, skip single-file download
                if (modelType != "TTS") {
                    tempFile = File(tempDir, "${modelId}_${System.currentTimeMillis()}.tmp")
                    downloadFile(fileUrl, tempFile, modelId, modelName, notificationId)
                }

                when (modelType) {
                    "SD" -> {
                        val modelsDir = AppPaths.models(applicationContext)
                        modelsDir.mkdirs()

                        val modelDir = AppPaths.modelDir(applicationContext, modelId)

                        if (isZip) {
                            if (modelDir.exists()) {
                                modelDir.deleteRecursively()
                            }
                            modelDir.mkdirs()

                            extractTempDir = File(tempDir, "${modelId}_extract")
                            extractTempDir.mkdirs()

                            updateDownloadState(modelId, DownloadState.Extracting(modelId))
                            updateNotification(modelName, 0f, notificationId, isExtracting = true)

                            unzipFile(tempFile!!, extractTempDir, modelId)

                            extractTempDir.listFiles()?.forEach { file ->
                                file.copyRecursively(File(modelDir, file.name), overwrite = true)
                            }
                            extractTempDir.deleteRecursively()
                            extractTempDir = null
                        } else {
                            if (!modelDir.exists()) {
                                modelDir.mkdirs()
                            }
                            tempFile?.copyTo(File(modelDir, tempFile.name), overwrite = true)
                        }

                        updateDownloadState(modelId, DownloadState.Processing(modelId))
                        updateNotification(modelName, 0f, notificationId, isProcessing = true)

                        insertModelToDatabase(
                            modelId = modelId,
                            modelName = modelName,
                            modelPath = modelDir.absolutePath,
                            modelType = modelType,
                            runOnCpu = runOnCpu,
                            textEmbeddingSize = textEmbeddingSize
                        )
                    }

                    "GGUF" -> {
                        AppPaths.models(applicationContext).mkdirs()

                        val targetFile = AppPaths.modelFile(applicationContext, modelId)

                        if (targetFile.exists()) {
                            targetFile.delete()
                        }

                        tempFile?.copyTo(targetFile, overwrite = true)

                        updateDownloadState(modelId, DownloadState.Processing(modelId))
                        updateNotification(modelName, 0f, notificationId, isProcessing = true)

                        insertModelToDatabase(
                            modelId = modelId,
                            modelName = modelName,
                            modelPath = targetFile.absolutePath,
                            modelType = modelType,
                            runOnCpu = false,
                            textEmbeddingSize = 0
                        )
                    }

                    "TTS" -> {
                        AppPaths.models(applicationContext).mkdirs()

                        val ttsModelDir = AppPaths.ttsModel(applicationContext)
                        if (ttsModelDir.exists()) ttsModelDir.deleteRecursively()
                        ttsModelDir.mkdirs()

                        updateDownloadState(modelId, DownloadState.Processing(modelId))
                        updateNotification(modelName, 0f, notificationId, isProcessing = true)

                        // Download all TTS model files
                        downloadTTSModelFiles(ttsModelDir, modelId, modelName, notificationId)

                        insertModelToDatabase(
                            modelId = modelId,
                            modelName = modelName,
                            modelPath = ttsModelDir.absolutePath,
                            modelType = modelType,
                            runOnCpu = true,
                            textEmbeddingSize = 0
                        )
                    }

                    "IMAGE_TOOL" -> {
                        val toolDir = AppPaths.imageTools(applicationContext)
                        toolDir.mkdirs()

                        val targetFile = File(toolDir, modelId)
                        targetFile.parentFile?.mkdirs()

                        if (isZip) {
                            extractTempDir = File(tempDir, "${modelId}_extract")
                            extractTempDir.mkdirs()

                            updateDownloadState(modelId, DownloadState.Extracting(modelId))
                            updateNotification(modelName, 0f, notificationId, isExtracting = true)

                            unzipFile(tempFile!!, extractTempDir, modelId)

                            extractTempDir.listFiles()?.forEach { file ->
                                file.copyTo(File(toolDir, file.name), overwrite = true)
                            }
                            extractTempDir.deleteRecursively()
                            extractTempDir = null
                        } else {
                            tempFile?.copyTo(targetFile, overwrite = true)
                        }

                        // No database entry — image tool models are managed by ImageToolsViewModel
                    }
                }

                tempFile?.delete()
                tempFile = null
                tempDir.deleteRecursively()

                updateDownloadState(modelId, DownloadState.Success(modelId))
                updateNotification(modelName, 100f, notificationId, isSuccess = true)

                withContext(Dispatchers.Main) {
                    kotlinx.coroutines.delay(2000)
                    updateDownloadState(modelId, null)
                    downloadJobs.remove(modelId)

                    if (downloadJobs.isEmpty()) {
                        stopForeground(STOP_FOREGROUND_REMOVE)
                        stopSelf()
                    }
                }

            } catch (e: kotlinx.coroutines.CancellationException) {
                tempFile?.delete()
                extractTempDir?.deleteRecursively()
                AppPaths.tempDownloads(applicationContext, modelId).deleteRecursively()

                updateDownloadState(modelId, DownloadState.Cancelled(modelId))
                updateNotification(modelName, 0f, notificationId, isCancelled = true)

                withContext(Dispatchers.Main) {
                    kotlinx.coroutines.delay(2000)
                    updateDownloadState(modelId, null)
                    downloadJobs.remove(modelId)

                    if (downloadJobs.isEmpty()) {
                        stopForeground(STOP_FOREGROUND_REMOVE)
                        stopSelf()
                    }
                }
            } catch (e: Exception) {
                tempFile?.delete()
                extractTempDir?.deleteRecursively()
                AppPaths.tempDownloads(applicationContext, modelId).deleteRecursively()

                updateDownloadState(modelId, DownloadState.Error(modelId, e.message ?: "Unknown error"))
                updateNotification(modelName, 0f, notificationId, error = e.message)

                withContext(Dispatchers.Main) {
                    kotlinx.coroutines.delay(3000)
                    updateDownloadState(modelId, null)
                    downloadJobs.remove(modelId)

                    if (downloadJobs.isEmpty()) {
                        stopForeground(STOP_FOREGROUND_REMOVE)
                        stopSelf()
                    }
                }
            }
        }

        downloadJobs[modelId] = job
    }

    private suspend fun downloadFile(
        url: String, destFile: File, modelId: String, modelName: String, notificationId: Int
    ) = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(url).build()
        val call = client.newCall(request)

        try {
            call.execute().use { response ->
                if (!response.isSuccessful) {
                    throw Exception("Download failed with code: ${response.code}")
                }

                val body = response.body
                val totalBytes = body.contentLength()
                var downloadedBytes = 0L
                var lastUpdateTime = 0L

                // Speed tracking: rolling window of last 5 samples
                val speedSamples = mutableListOf<Long>()
                var lastSpeedBytes = 0L
                var lastSpeedTime = System.currentTimeMillis()

                FileOutputStream(destFile).buffered().use { output ->
                    body.byteStream().buffered().use { input ->
                        val buffer = ByteArray(64 * 1024) // 64KB for better throughput
                        var bytes: Int

                        while (input.read(buffer).also { bytes = it } != -1) {
                            if (!downloadJobs.containsKey(modelId) || downloadJobs[modelId]?.isCancelled == true) {
                                call.cancel()
                                throw kotlinx.coroutines.CancellationException("Download cancelled")
                            }

                            output.write(buffer, 0, bytes)
                            downloadedBytes += bytes

                            val currentTime = System.currentTimeMillis()
                            if (currentTime - lastUpdateTime >= 500 || downloadedBytes == totalBytes) {
                                // Calculate speed
                                val elapsed = currentTime - lastSpeedTime
                                if (elapsed > 0) {
                                    val bytesInInterval = downloadedBytes - lastSpeedBytes
                                    val speedSample = bytesInInterval * 1000 / elapsed
                                    speedSamples.add(speedSample)
                                    if (speedSamples.size > 5) speedSamples.removeAt(0)
                                    lastSpeedBytes = downloadedBytes
                                    lastSpeedTime = currentTime
                                }

                                val avgSpeed = if (speedSamples.isNotEmpty()) {
                                    speedSamples.average().toLong()
                                } else 0L

                                val eta = if (avgSpeed > 0 && totalBytes > 0) {
                                    (totalBytes - downloadedBytes) / avgSpeed
                                } else -1L

                                lastUpdateTime = currentTime
                                val progress = if (totalBytes > 0) {
                                    downloadedBytes.toFloat() / totalBytes
                                } else 0f

                                updateDownloadState(modelId, DownloadState.Downloading(
                                    modelId, progress, downloadedBytes, totalBytes, avgSpeed, eta
                                ))

                                updateNotification(modelName, progress, notificationId)
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            call.cancel()
            throw e
        }
    }

    private suspend fun unzipFile(zipFile: File, destDir: File, modelId: String) = withContext(Dispatchers.IO) {
        // First pass: count valid entries
        val totalFiles = ZipInputStream(zipFile.inputStream().buffered()).use { zis ->
            var count = 0
            var entry = zis.nextEntry
            while (entry != null) {
                if (!entry.isDirectory) {
                    val name = entry.name.substringAfterLast('/')
                    if (name.isNotEmpty() && !name.startsWith(".") && !entry.name.contains("__MACOSX")) {
                        count++
                    }
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
            count
        }

        // Second pass: extract with per-file progress
        var extractedCount = 0
        ZipInputStream(zipFile.inputStream().buffered()).use { zis ->
            var entry = zis.nextEntry

            while (entry != null) {
                // Check for cancellation
                if (!downloadJobs.containsKey(modelId) || downloadJobs[modelId]?.isCancelled == true) {
                    throw kotlinx.coroutines.CancellationException("Extraction cancelled")
                }

                if (!entry.isDirectory) {
                    val fileName = entry.name.substringAfterLast('/')
                    if (fileName.isNotEmpty() && !fileName.startsWith(".") && !entry.name.contains("__MACOSX")) {
                        updateDownloadState(modelId, DownloadState.Extracting(
                            modelId = modelId,
                            currentFile = fileName,
                            extractedCount = extractedCount,
                            totalFiles = totalFiles
                        ))

                        val file = File(destDir, fileName)
                        require(file.canonicalPath.startsWith(destDir.canonicalPath + File.separator)) {
                            "Zip entry path traversal detected: ${entry.name}"
                        }
                        FileOutputStream(file).buffered().use { output ->
                            zis.copyTo(output)
                        }
                        extractedCount++
                    }
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }
    }

    private suspend fun downloadTTSModelFiles(
        ttsModelDir: File, modelId: String, modelName: String, notificationId: Int
    ) = withContext(Dispatchers.IO) {
        val baseUrl = "https://huggingface.co/Supertone/supertonic-2/resolve/main"

        val onnxDir = File(ttsModelDir, "onnx")
        onnxDir.mkdirs()
        val voiceDir = File(ttsModelDir, "voice_styles")
        voiceDir.mkdirs()

        val onnxFiles = listOf(
            "onnx/duration_predictor.onnx",
            "onnx/text_encoder.onnx",
            "onnx/vector_estimator.onnx",
            "onnx/vocoder.onnx",
            "onnx/tts.json",
            "onnx/unicode_indexer.json"
        )

        val voiceFiles = listOf(
            "voice_styles/F1.json", "voice_styles/F2.json", "voice_styles/F3.json",
            "voice_styles/F4.json", "voice_styles/F5.json",
            "voice_styles/M1.json", "voice_styles/M2.json", "voice_styles/M3.json",
            "voice_styles/M4.json", "voice_styles/M5.json"
        )

        val allFiles = onnxFiles + voiceFiles
        var filesDownloaded = 0

        for (filePath in allFiles) {
            if (!downloadJobs.containsKey(modelId) || downloadJobs[modelId]?.isCancelled == true) {
                throw kotlinx.coroutines.CancellationException("TTS download cancelled")
            }

            val url = "$baseUrl/$filePath"
            val destFile = File(ttsModelDir, filePath)
            destFile.parentFile?.mkdirs()

            val request = Request.Builder().url(url).build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw Exception("Failed to download $filePath: ${response.code}")
                }
                response.body.byteStream().use { input ->
                    FileOutputStream(destFile).use { output ->
                        input.copyTo(output)
                    }
                }
            }

            filesDownloaded++
            val progress = filesDownloaded.toFloat() / allFiles.size
            updateDownloadState(modelId, DownloadState.Downloading(
                modelId, progress, filesDownloaded.toLong(), allFiles.size.toLong()
            ))
            updateNotification(modelName, progress, notificationId)
        }
    }

    private suspend fun insertModelToDatabase(
        modelId: String,
        modelName: String,
        modelPath: String,
        modelType: String,
        runOnCpu: Boolean,
        textEmbeddingSize: Int
    ) = withContext(Dispatchers.IO) {
        val repository = AppContainer.getModelRepository()

        // Use the store model ID as primary key so the UI can match
        // installed models against store listings. SHA256 is still computed
        // for integrity but not used as the DB key.
        val providerType = when (modelType) {
            "SD" -> ProviderType.DIFFUSION
            "GGUF" -> ProviderType.GGUF
            "TTS" -> ProviderType.TTS
            else -> ProviderType.GGUF
        }

        val pathType = when (modelType) {
            "SD", "TTS" -> PathType.DIRECTORY
            "GGUF" -> PathType.FILE
            else -> PathType.FILE
        }

        val fileSize = when (modelType) {
            "GGUF" -> File(modelPath).length()
            "TTS" -> {
                val dir = File(modelPath)
                if (dir.isDirectory) dir.walkTopDown().sumOf { it.length() } else 0L
            }
            else -> 0L
        }

        val model = Model(
            id = modelId,
            modelName = modelName,
            modelPath = modelPath,
            pathType = pathType,
            providerType = providerType,
            fileSize = fileSize,
            isActive = true
        )

        repository.insertModel(model)

        val config = when (providerType) {
            ProviderType.DIFFUSION -> {
                val diffusionConfig = DiffusionConfig(
                    textEmbeddingSize = textEmbeddingSize,
                    runOnCpu = runOnCpu,
                    useCpuClip = true,
                    isPony = false,
                    httpPort = 8081,
                    safetyMode = false,
                    width = 512,
                    height = 512
                )
                val inferenceParams = DiffusionInferenceParams()
                ModelConfig(
                    modelId = modelId,
                    modelLoadingParams = diffusionConfig.toJson(),
                    modelInferenceParams = inferenceParams.toJson()
                )
            }

            ProviderType.GGUF -> {
                val appSettings = AppSettingsDataStore(this@ModelDownloadService)
                val tuningEnabled = appSettings.hardwareTuningEnabled.firstOrNull() ?: true
                val loadingParams = if (tuningEnabled) {
                    val perfMode = appSettings.performanceMode.firstOrNull() ?: com.dark.tool_neuron.global.PerformanceMode.BALANCED
                    val modelSizeMB = (fileSize / (1024 * 1024)).toInt()
                    val profile = HardwareScanner.scan(this@ModelDownloadService)
                    DeviceTuner.tune(profile, modelSizeMB, modelName, perfMode)
                } else {
                    com.dark.tool_neuron.models.engine_schema.GgufLoadingParams()
                }
                val ggufSchema = GgufEngineSchema(loadingParams = loadingParams)
                ModelConfig(
                    modelId = modelId,
                    modelLoadingParams = ggufSchema.toLoadingJson(),
                    modelInferenceParams = ggufSchema.toInferenceJson()
                )
            }

            ProviderType.TTS -> {
                ModelConfig(
                    modelId = modelId,
                    modelLoadingParams = """{"type":"tts","useNNAPI":false}""",
                    modelInferenceParams = """{"voice":"F1","speed":1.05,"steps":2,"language":"en"}"""
                )
            }
        }

        repository.insertConfig(config)
    }

    private fun cancelDownload(modelId: String) {
        downloadJobs[modelId]?.cancel()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            NOTIFICATION_CHANNEL_ID, "Model Downloads", NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Shows progress of model downloads"
        }
        notificationManager.createNotificationChannel(channel)
    }

    private fun createNotification(
        modelName: String,
        progress: Float,
        isExtracting: Boolean = false,
        isProcessing: Boolean = false
    ): android.app.Notification {
        val title = when {
            isProcessing -> "Processing $modelName"
            isExtracting -> "Extracting $modelName"
            else -> "Downloading $modelName"
        }

        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID).setContentTitle(title)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setProgress(100, (progress * 100).toInt(), isExtracting || isProcessing)
            .setOngoing(true).build()
    }

    private fun updateNotification(
        modelName: String,
        progress: Float,
        notificationId: Int,
        isSuccess: Boolean = false,
        error: String? = null,
        isExtracting: Boolean = false,
        isProcessing: Boolean = false,
        isCancelled: Boolean = false
    ) {
        val notification = when {
            isSuccess -> {
                NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
                    .setContentTitle("Download Complete").setContentText(modelName)
                    .setSmallIcon(android.R.drawable.stat_sys_download_done).setOngoing(false)
                    .build()
            }

            isCancelled -> {
                NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
                    .setContentTitle("Download Cancelled").setContentText(modelName)
                    .setSmallIcon(android.R.drawable.ic_menu_close_clear_cancel).setOngoing(false)
                    .build()
            }

            error != null -> {
                NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
                    .setContentTitle("Download Failed").setContentText(error)
                    .setSmallIcon(android.R.drawable.stat_notify_error).setOngoing(false).build()
            }

            else -> {
                createNotification(modelName, progress, isExtracting, isProcessing)
            }
        }

        notificationManager.notify(notificationId, notification)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }
}