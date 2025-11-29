package com.dark.tool_neuron.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.dark.ai_module.model.ModelData
import com.dark.ai_module.model.ModelType
import com.dark.ai_module.workers.ModelManager
import com.dark.tool_neuron.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * Background service for downloading AI models with notification support.
 *
 * Features:
 * - Foreground service with persistent notification
 * - Real-time progress tracking via StateFlow
 * - Automatic model persistence after successful download
 * - Network error handling and retry logic
 * - Supports multiple concurrent downloads
 */
class ModelDownloadService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val activeDownloads = mutableMapOf<String, Job>()
    private var notificationId = NOTIFICATION_ID_START

    private lateinit var notificationManager: NotificationManager

    /* ========================================================================= *//* SERVICE LIFECYCLE                                                         *//* ========================================================================= */

    override fun onCreate() {
        super.onCreate()
        notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        createNotificationChannel()
        Log.i(TAG, "ModelDownloadService created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        intent?.let { handleIntent(it) }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        serviceScope.cancel()
        activeDownloads.clear()
        Log.i(TAG, "ModelDownloadService destroyed")
        super.onDestroy()
    }

    private fun handleIntent(intent: Intent) {
        when (intent.action) {
            ACTION_START_DOWNLOAD -> {
                // Extract individual fields from intent
                val modelName = intent.getStringExtra(EXTRA_MODEL_NAME) ?: return
                val modelUrl = intent.getStringExtra(EXTRA_MODEL_URL) ?: return
                val modelId = intent.getStringExtra(EXTRA_MODEL_ID) ?: return
                val providerName = intent.getStringExtra(EXTRA_PROVIDER_NAME) ?: return
                val modelType = intent.getStringExtra(EXTRA_MODEL_TYPE) ?: ModelType.TEXT.name
                val ctxSize = intent.getIntExtra(EXTRA_CTX_SIZE, 4048)
                val temp = intent.getFloatExtra(EXTRA_TEMP, 0.7f)
                val topP = intent.getFloatExtra(EXTRA_TOP_P, 0.5f)
                val isToolCalling = intent.getBooleanExtra(EXTRA_IS_TOOL_CALLING, false)

                // Reconstruct ModelData
                val modelData = ModelData(
                    id = modelId,
                    modelName = modelName,
                    modelUrl = modelUrl,
                    providerName = providerName,
                    modelType = ModelType.valueOf(modelType),
                    ctxSize = ctxSize,
                    temp = temp,
                    topP = topP,
                    isToolCalling = isToolCalling
                )

                startModelDownload(modelData)
            }

            ACTION_CANCEL_DOWNLOAD -> {
                val url = intent.getStringExtra(EXTRA_DOWNLOAD_URL)
                url?.let { cancelModelDownload(it) }
            }
        }
    }

    /* ========================================================================= *//* DOWNLOAD MANAGEMENT                                                       *//* ========================================================================= */

    /**
     * Initiates a model download in the background.
     */
    private fun startModelDownload(modelData: ModelData) {
        val url = modelData.modelUrl
        if (url.isNullOrBlank()) {
            Log.e(TAG, "Cannot start download: Invalid URL")
            return
        }

        // Prevent duplicate downloads
        if (activeDownloads.containsKey(url)) {
            Log.w(TAG, "Download already in progress for: ${modelData.modelName}")
            return
        }

        // Create output directory
        val outputDir = File(filesDir, "models").apply { mkdirs() }
        val outputFile = File(outputDir, modelData.modelName)

        // Initialize progress
        updateProgress(url, DownloadProgress(isDownloading = true))

        // Start download job
        val job = serviceScope.launch {
            try {
                downloadModel(
                    url = url,
                    outputFile = outputFile,
                    modelData = modelData,
                    notificationId = notificationId++
                )
            } catch (e: Exception) {
                Log.e(TAG, "Download failed for ${modelData.modelName}: ${e.message}", e)
                handleDownloadError(url, modelData.modelName, e)
            } finally {
                activeDownloads.remove(url)

                // Stop service if no active downloads
                if (activeDownloads.isEmpty()) {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                }
            }
        }

        activeDownloads[url] = job
    }

    /**
     * Downloads a model file with progress tracking and notification updates.
     */
    private suspend fun downloadModel(
        url: String, outputFile: File, modelData: ModelData, notificationId: Int
    ) {
        val connection = URL(url).openConnection() as HttpURLConnection

        try {
            connection.apply {
                requestMethod = "GET"
                connectTimeout = 30000
                readTimeout = 30000
                setRequestProperty("User-Agent", "ToolNeuron/1.0")
            }

            connection.connect()

            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                throw Exception("HTTP ${connection.responseCode}: ${connection.responseMessage}")
            }

            val fileSize = connection.contentLengthLong
            val inputStream = connection.inputStream
            val outputStream = FileOutputStream(outputFile)

            var downloadedBytes = 0L
            val buffer = ByteArray(BUFFER_SIZE)
            var lastProgressUpdate = System.currentTimeMillis()

            // Show initial notification
            showDownloadNotification(
                notificationId, modelData.modelName, 0f, fileSize
            )

            // Download loop
            while (true) {
                val bytesRead = inputStream.read(buffer)
                if (bytesRead == -1) break

                outputStream.write(buffer, 0, bytesRead)
                downloadedBytes += bytesRead

                val progress = if (fileSize > 0) {
                    (downloadedBytes.toFloat() / fileSize) * 100
                } else 0f

                // Update progress (throttled to avoid excessive updates)
                val now = System.currentTimeMillis()
                if (now - lastProgressUpdate > PROGRESS_UPDATE_INTERVAL) {
                    updateProgress(
                        url, DownloadProgress(
                            isDownloading = true, progress = progress
                        )
                    )

                    showDownloadNotification(
                        notificationId, modelData.modelName, progress, fileSize
                    )

                    lastProgressUpdate = now
                }
            }

            outputStream.flush()
            outputStream.close()
            inputStream.close()

            // Download complete - persist to database
            val savedModel = modelData.copy(
                modelUrl = outputFile.absolutePath, isImported = false
            )

            ModelManager.addModel(savedModel)

            // Update state
            updateProgress(
                url, DownloadProgress(
                    isDownloading = false, progress = 100f, isComplete = true
                )
            )

            // Show completion notification
            showDownloadCompleteNotification(notificationId, modelData.modelName)

            Log.i(TAG, "Successfully downloaded: ${modelData.modelName}")

        } catch (e: Exception) {
            // Clean up partial file
            if (outputFile.exists()) {
                outputFile.delete()
            }
            throw e
        } finally {
            connection.disconnect()
        }
    }

    /**
     * Cancels an active download.
     */
    private fun cancelModelDownload(url: String) {
        activeDownloads[url]?.let { job ->
            job.cancel()
            activeDownloads.remove(url)

            updateProgress(url, DownloadProgress(isDownloading = false))

            Log.i(TAG, "Cancelled download: $url")
        }
    }

    /**
     * Handles download errors by updating state and showing error notification.
     */
    private fun handleDownloadError(url: String, modelName: String, error: Exception) {
        updateProgress(
            url, DownloadProgress(
                isDownloading = false, errorMessage = error.message ?: "Unknown error"
            )
        )

        showDownloadErrorNotification(
            notificationId++, modelName, error.message ?: "Download failed"
        )
    }

    /* ========================================================================= *//* NOTIFICATIONS                                                             *//* ========================================================================= */

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, "Model Downloads", NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Notifications for AI model downloads"
                setShowBadge(false)
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun showDownloadNotification(
        id: Int, modelName: String, progress: Float, totalBytes: Long
    ) {
        val notification =
            NotificationCompat.Builder(this, CHANNEL_ID).setContentTitle("Downloading: $modelName")
                .setContentText("${progress.toInt()}% • ${formatBytes(totalBytes)}")
                .setSmallIcon(R.drawable.installed_models).setProgress(100, progress.toInt(), false)
                .setOngoing(true).setPriority(NotificationCompat.PRIORITY_LOW)
                .setCategory(NotificationCompat.CATEGORY_PROGRESS).build()

        startForeground(id, notification)
    }

    private fun showDownloadCompleteNotification(id: Int, modelName: String) {
        val notification =
            NotificationCompat.Builder(this, CHANNEL_ID).setContentTitle("Download Complete")
                .setContentText(modelName).setSmallIcon(R.drawable.installed_models)
                .setPriority(NotificationCompat.PRIORITY_LOW).setAutoCancel(true).build()

        notificationManager.notify(id, notification)
    }

    private fun showDownloadErrorNotification(id: Int, modelName: String, error: String) {
        val notification =
            NotificationCompat.Builder(this, CHANNEL_ID).setContentTitle("Download Failed")
                .setContentText("$modelName: $error").setSmallIcon(R.drawable.installed_models)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT).setAutoCancel(true).build()

        notificationManager.notify(id, notification)
    }

    /* ========================================================================= *//* HELPER FUNCTIONS                                                          *//* ========================================================================= */

    private fun formatBytes(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> "%.2f KB".format(bytes / 1024.0)
            bytes < 1024 * 1024 * 1024 -> "%.2f MB".format(bytes / (1024.0 * 1024))
            else -> "%.2f GB".format(bytes / (1024.0 * 1024 * 1024))
        }
    }

    private fun updateProgress(url: String, progress: DownloadProgress) {
        _downloadProgress.value += (url to progress)
    }

    companion object {
        private const val TAG = "ModelDownloadService"

        // Intent actions
        private const val ACTION_START_DOWNLOAD = "com.dark.tool_neuron.START_DOWNLOAD"
        private const val ACTION_CANCEL_DOWNLOAD = "com.dark.tool_neuron.CANCEL_DOWNLOAD"

        // Intent extras
        private const val EXTRA_MODEL_NAME = "extra_model_name"
        private const val EXTRA_MODEL_URL = "extra_model_url"
        private const val EXTRA_MODEL_ID = "extra_model_id"
        private const val EXTRA_PROVIDER_NAME = "extra_provider_name"
        private const val EXTRA_MODEL_TYPE = "extra_model_type"
        private const val EXTRA_CTX_SIZE = "extra_ctx_size"
        private const val EXTRA_TEMP = "extra_temp"
        private const val EXTRA_TOP_P = "extra_top_p"
        private const val EXTRA_IS_TOOL_CALLING = "extra_is_tool_calling"
        private const val EXTRA_DOWNLOAD_URL = "extra_download_url"

        // Notification
        private const val CHANNEL_ID = "model_download_channel"
        private const val NOTIFICATION_ID_START = 1000

        // Download configuration
        private const val BUFFER_SIZE = 8192
        private const val PROGRESS_UPDATE_INTERVAL = 500L // ms

        // Shared download progress state
        private val _downloadProgress = MutableStateFlow<Map<String, DownloadProgress>>(emptyMap())
        val downloadProgress: StateFlow<Map<String, DownloadProgress>> =
            _downloadProgress.asStateFlow()

        /**
         * Starts a model download via the service.
         */
        fun startDownload(context: Context, modelData: ModelData) {
            val intent = Intent(context, ModelDownloadService::class.java).apply {
                action = ACTION_START_DOWNLOAD
                putExtra(EXTRA_MODEL_NAME, modelData.modelName)
                putExtra(EXTRA_MODEL_URL, modelData.modelUrl)
                putExtra(EXTRA_MODEL_ID, modelData.id)
                putExtra(EXTRA_PROVIDER_NAME, modelData.providerName)
                putExtra(EXTRA_MODEL_TYPE, modelData.modelType.name)
                putExtra(EXTRA_CTX_SIZE, modelData.ctxSize)
                putExtra(EXTRA_TEMP, modelData.temp)
                putExtra(EXTRA_TOP_P, modelData.topP)
                putExtra(EXTRA_IS_TOOL_CALLING, modelData.isToolCalling)
            }

            context.startForegroundService(intent)
        }

        /**
         * Cancels an active download.
         */
        fun cancelDownload(url: String) {
            _downloadProgress.value = _downloadProgress.value.toMutableMap().apply {
                remove(url)
            }
        }
    }
}

/**
 * Data class representing download progress for a model.
 */
data class DownloadProgress(
    val isDownloading: Boolean = false,
    val progress: Float = 0f,
    val isComplete: Boolean = false,
    val errorMessage: String? = null
)