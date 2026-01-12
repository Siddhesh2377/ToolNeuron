package com.dark.tool_neuron.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.dark.tool_neuron.R
import com.dark.tool_neuron.di.AppContainer
import com.dark.tool_neuron.models.engine_schema.GgufEngineSchema
import com.dark.tool_neuron.models.enums.PathType
import com.dark.tool_neuron.models.enums.ProviderType
import com.dark.tool_neuron.models.table_schema.Model
import com.dark.tool_neuron.models.table_schema.ModelConfig
import com.dark.tool_neuron.repo.ModelRepository
import com.dark.tool_neuron.worker.DiffusionConfig
import com.dark.tool_neuron.worker.ModelDataParser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit
import java.util.zip.ZipInputStream

class ModelDownloadService : Service() {
    
    private val serviceScope = CoroutineScope(Dispatchers.IO + Job())
    private var downloadJob: Job? = null
    
    private val notificationManager by lazy {
        getSystemService(NOTIFICATION_SERVICE) as NotificationManager
    }
    
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()
    
    companion object {
        private const val NOTIFICATION_CHANNEL_ID = "model_download_channel"
        private const val NOTIFICATION_ID = 3001
        
        private val _downloadState = MutableStateFlow<DownloadState>(DownloadState.Idle)
        val downloadState: StateFlow<DownloadState> = _downloadState
        
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
        object Idle : DownloadState()
        data class Downloading(
            val modelId: String,
            val progress: Float,
            val downloadedBytes: Long,
            val totalBytes: Long
        ) : DownloadState()
        data class Extracting(val modelId: String) : DownloadState()
        data class Processing(val modelId: String) : DownloadState()
        data class Success(val modelId: String) : DownloadState()
        data class Error(val modelId: String, val message: String) : DownloadState()
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
                
                startForeground(NOTIFICATION_ID, createNotification(modelName, 0f))
                startDownload(modelId, modelName, fileUrl, isZip, modelType, runOnCpu, textEmbeddingSize)
            }
            ACTION_CANCEL_DOWNLOAD -> {
                cancelDownload()
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
        downloadJob?.cancel()
        downloadJob = serviceScope.launch {
            var tempFile: File? = null
            var extractTempDir: File? = null
            try {
                _downloadState.value = DownloadState.Downloading(modelId, 0f, 0, 0)
                
                val tempDir = File(filesDir, "temp_downloads")
                if (tempDir.exists()) {
                    tempDir.deleteRecursively()
                }
                tempDir.mkdirs()
                
                tempFile = File(tempDir, "${modelId}_${System.currentTimeMillis()}.tmp")
                
                downloadFile(fileUrl, tempFile, modelId, modelName)
                
                when (modelType) {
                    "SD" -> {
                        val modelsDir = File(filesDir, "models")
                        modelsDir.mkdirs()
                        
                        val modelDir = File(modelsDir, modelId)
                        
                        if (isZip) {
                            if (modelDir.exists()) {
                                modelDir.deleteRecursively()
                            }
                            modelDir.mkdirs()
                            
                            extractTempDir = File(tempDir, "${modelId}_extract")
                            extractTempDir.mkdirs()
                            
                            _downloadState.value = DownloadState.Extracting(modelId)
                            updateNotification(modelName, 0f, isExtracting = true)
                            
                            unzipFile(tempFile, extractTempDir)
                            
                            extractTempDir.listFiles()?.forEach { file ->
                                file.copyRecursively(File(modelDir, file.name), overwrite = true)
                            }
                            extractTempDir.deleteRecursively()
                            extractTempDir = null
                        } else {
                            if (!modelDir.exists()) {
                                modelDir.mkdirs()
                            }
                            tempFile.copyTo(File(modelDir, tempFile.name), overwrite = true)
                        }
                        
                        _downloadState.value = DownloadState.Processing(modelId)
                        updateNotification(modelName, 0f, isProcessing = true)
                        
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
                        val modelsDir = File(filesDir, "models")
                        modelsDir.mkdirs()
                        
                        val targetFile = File(modelsDir, "$modelId.gguf")
                        
                        if (targetFile.exists()) {
                            targetFile.delete()
                        }
                        
                        tempFile.copyTo(targetFile, overwrite = true)
                        
                        _downloadState.value = DownloadState.Processing(modelId)
                        updateNotification(modelName, 0f, isProcessing = true)
                        
                        insertModelToDatabase(
                            modelId = modelId,
                            modelName = modelName,
                            modelPath = targetFile.absolutePath,
                            modelType = modelType,
                            runOnCpu = false,
                            textEmbeddingSize = 0
                        )
                    }
                }
                
                tempFile?.delete()
                tempFile = null
                
                _downloadState.value = DownloadState.Success(modelId)
                updateNotification(modelName, 100f, isSuccess = true)
                
                withContext(Dispatchers.Main) {
                    kotlinx.coroutines.delay(2000)
                    _downloadState.value = DownloadState.Idle
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                }
                
            } catch (e: Exception) {
                tempFile?.delete()
                extractTempDir?.deleteRecursively()
                
                _downloadState.value = DownloadState.Error(modelId, e.message ?: "Unknown error")
                updateNotification(modelName, 0f, error = e.message)
                
                withContext(Dispatchers.Main) {
                    kotlinx.coroutines.delay(3000)
                    _downloadState.value = DownloadState.Idle
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                }
            }
        }
    }
    
    private suspend fun downloadFile(
        url: String,
        destFile: File,
        modelId: String,
        modelName: String
    ) = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(url)
            .build()
        
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw Exception("Download failed with code: ${response.code}")
            }
            
            val body = response.body ?: throw Exception("Response body is null")
            val totalBytes = body.contentLength()
            var downloadedBytes = 0L
            var lastUpdateTime = 0L
            
            FileOutputStream(destFile).buffered().use { output ->
                body.byteStream().buffered().use { input ->
                    val buffer = ByteArray(32 * 1024)
                    var bytes: Int
                    
                    while (input.read(buffer).also { bytes = it } != -1) {
                        output.write(buffer, 0, bytes)
                        downloadedBytes += bytes
                        
                        val currentTime = System.currentTimeMillis()
                        if (currentTime - lastUpdateTime >= 500 || downloadedBytes == totalBytes) {
                            lastUpdateTime = currentTime
                            val progress = if (totalBytes > 0) {
                                downloadedBytes.toFloat() / totalBytes
                            } else 0f
                            
                            _downloadState.value = DownloadState.Downloading(
                                modelId,
                                progress,
                                downloadedBytes,
                                totalBytes
                            )
                            
                            updateNotification(modelName, progress)
                        }
                    }
                }
            }
        }
    }
    
    private suspend fun unzipFile(zipFile: File, destDir: File) = withContext(Dispatchers.IO) {
        ZipInputStream(zipFile.inputStream().buffered()).use { zis ->
            var entry = zis.nextEntry
            
            while (entry != null) {
                if (!entry.isDirectory) {
                    val fileName = entry.name.substringAfterLast('/')
                    if (fileName.isNotEmpty() && 
                        !fileName.startsWith(".") && 
                        !fileName.startsWith("__MACOSX")) {
                        val file = File(destDir, fileName)
                        
                        FileOutputStream(file).buffered().use { output ->
                            zis.copyTo(output)
                        }
                    }
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
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
        val parser = ModelDataParser()
        
        val checksum = parser.checksumSHA256(modelPath)
        
        val providerType = when (modelType) {
            "SD" -> ProviderType.DIFFUSION
            "GGUF" -> ProviderType.GGUF
            else -> ProviderType.GGUF
        }
        
        val pathType = when (modelType) {
            "SD" -> PathType.DIRECTORY
            "GGUF" -> PathType.FILE
            else -> PathType.FILE
        }
        
        val fileSize = if (modelType == "GGUF") {
            File(modelPath).length()
        } else {
            0L
        }
        
        val model = Model(
            id = checksum,
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
                ModelConfig(
                    modelId = checksum,
                    modelLoadingParams = diffusionConfig.toJson(),
                    modelInferenceParams = null
                )
            }
            ProviderType.GGUF -> {
                val ggufSchema = GgufEngineSchema()
                ModelConfig(
                    modelId = checksum,
                    modelLoadingParams = ggufSchema.toLoadingJson(),
                    modelInferenceParams = ggufSchema.toInferenceJson()
                )
            }
        }
        
        repository.insertConfig(config)
    }
    
    private fun cancelDownload() {
        downloadJob?.cancel()
        _downloadState.value = DownloadState.Idle
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }
    
    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            NOTIFICATION_CHANNEL_ID,
            "Model Downloads",
            NotificationManager.IMPORTANCE_LOW
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
        
        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle(title)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setProgress(100, (progress * 100).toInt(), isExtracting || isProcessing)
            .setOngoing(true)
            .build()
    }
    
    private fun updateNotification(
        modelName: String,
        progress: Float,
        isSuccess: Boolean = false,
        error: String? = null,
        isExtracting: Boolean = false,
        isProcessing: Boolean = false
    ) {
        val notification = when {
            isSuccess -> {
                NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
                    .setContentTitle("Download Complete")
                    .setContentText(modelName)
                    .setSmallIcon(android.R.drawable.stat_sys_download_done)
                    .setOngoing(false)
                    .build()
            }
            error != null -> {
                NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
                    .setContentTitle("Download Failed")
                    .setContentText(error)
                    .setSmallIcon(android.R.drawable.stat_notify_error)
                    .setOngoing(false)
                    .build()
            }
            else -> {
                createNotification(modelName, progress, isExtracting, isProcessing)
            }
        }
        
        notificationManager.notify(NOTIFICATION_ID, notification)
    }
    
    override fun onBind(intent: Intent?): IBinder? = null
    
    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }
}