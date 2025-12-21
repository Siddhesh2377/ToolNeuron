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
import com.dark.ai_module.workers.ModelInstallationManager
import com.dark.tool_neuron.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Foreground service wrapper for ModelInstallationManager
 * Provides notification support for background downloads
 */
class ModelDownloadService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val activeDownloads = mutableMapOf<String, Job>()
    private var notificationId = NOTIFICATION_ID_START

    private lateinit var notificationManager: NotificationManager

    override fun onCreate() {
        super.onCreate()
        notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        createNotificationChannel()
        ModelInstallationManager.init(applicationContext)
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
                val modelData = extractModelData(intent) ?: return
                startModelDownload(modelData)
            }
            ACTION_CANCEL_DOWNLOAD -> {
                val url = intent.getStringExtra(EXTRA_DOWNLOAD_URL)
                url?.let { cancelModelDownload(it) }
            }
        }
    }

    private fun extractModelData(intent: Intent): ModelData? {
        return try {
            ModelData(
                id = intent.getStringExtra(EXTRA_MODEL_ID) ?: return null,
                modelName = intent.getStringExtra(EXTRA_MODEL_NAME) ?: return null,
                modelPath = intent.getStringExtra(EXTRA_MODEL_PATH) ?: return null,
                modelUrl = intent.getStringExtra(EXTRA_MODEL_URL) ?: return null,
                providerName = intent.getStringExtra(EXTRA_PROVIDER_NAME) ?: return null,
                modelType = ModelType.valueOf(
                    intent.getStringExtra(EXTRA_MODEL_TYPE) ?: ModelType.TEXT.name
                ),
                ctxSize = intent.getIntExtra(EXTRA_CTX_SIZE, 4048),
                temp = intent.getFloatExtra(EXTRA_TEMP, 0.7f),
                topP = intent.getFloatExtra(EXTRA_TOP_P, 0.5f),
                isToolCalling = intent.getBooleanExtra(EXTRA_IS_TOOL_CALLING, false)
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to extract ModelData", e)
            null
        }
    }

    /* ========================================================================= */
    /* DOWNLOAD MANAGEMENT                                                       */
    /* ========================================================================= */

    private fun startModelDownload(modelData: ModelData) {
        val url = modelData.modelUrl
        if (url.isNullOrBlank()) {
            Log.e(TAG, "Cannot start download: Invalid URL")
            return
        }

        if (activeDownloads.containsKey(url)) {
            Log.w(TAG, "Download already in progress for: ${modelData.modelName}")
            return
        }

        val currentNotificationId = notificationId++

        val job = serviceScope.launch {
            try {
                // Show initial notification
                showDownloadNotification(currentNotificationId, modelData.modelName, 0f, 0L)

                // Use ModelInstallationManager for download and install
                val result = ModelInstallationManager.downloadAndInstallModel(modelData) { progress ->
                    // Update notification with progress
                    showDownloadNotification(
                        currentNotificationId,
                        modelData.modelName,
                        progress,
                        0L // File size not directly available, but notification shows percentage
                    )
                }

                result.onSuccess {
                    showDownloadCompleteNotification(currentNotificationId, modelData.modelName)
                    Log.i(TAG, "Successfully downloaded and installed: ${modelData.modelName}")
                }.onFailure { error ->
                    showDownloadErrorNotification(
                        currentNotificationId,
                        modelData.modelName,
                        error.message ?: "Download failed"
                    )
                    Log.e(TAG, "Download failed for ${modelData.modelName}", error)
                }

            } catch (e: Exception) {
                Log.e(TAG, "Unexpected error during download: ${e.message}", e)
                showDownloadErrorNotification(
                    currentNotificationId,
                    modelData.modelName,
                    e.message ?: "Unknown error"
                )
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

    private fun cancelModelDownload(url: String) {
        activeDownloads[url]?.let { job ->
            job.cancel()
            activeDownloads.remove(url)
            Log.i(TAG, "Cancelled download: $url")

            if (activeDownloads.isEmpty()) {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
    }

    /* ========================================================================= */
    /* NOTIFICATIONS                                                             */
    /* ========================================================================= */

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Model Downloads",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Notifications for AI model downloads"
            setShowBadge(false)
        }
        notificationManager.createNotificationChannel(channel)
    }

    private fun showDownloadNotification(
        id: Int,
        modelName: String,
        progress: Float,
        totalBytes: Long
    ) {
        val progressText = if (totalBytes > 0) {
            "${progress.toInt()}% • ${formatBytes(totalBytes)}"
        } else {
            "${progress.toInt()}%"
        }

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Downloading: $modelName")
            .setContentText(progressText)
            .setSmallIcon(R.drawable.installed_models)
            .setProgress(100, progress.toInt(), false)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .build()

        startForeground(id, notification)
    }

    private fun showDownloadCompleteNotification(id: Int, modelName: String) {
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Download Complete")
            .setContentText(modelName)
            .setSmallIcon(R.drawable.installed_models)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(id, notification)
    }

    private fun showDownloadErrorNotification(id: Int, modelName: String, error: String) {
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Download Failed")
            .setContentText("$modelName: $error")
            .setSmallIcon(R.drawable.installed_models)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(id, notification)
    }

    private fun formatBytes(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> "%.2f KB".format(bytes / 1024.0)
            bytes < 1024 * 1024 * 1024 -> "%.2f MB".format(bytes / (1024.0 * 1024))
            else -> "%.2f GB".format(bytes / (1024.0 * 1024 * 1024))
        }
    }

    companion object {
        private const val TAG = "ModelDownloadService"

        // Intent actions
        private const val ACTION_START_DOWNLOAD = "com.dark.tool_neuron.START_DOWNLOAD"
        private const val ACTION_CANCEL_DOWNLOAD = "com.dark.tool_neuron.CANCEL_DOWNLOAD"

        // Intent extras
        private const val EXTRA_MODEL_NAME = "extra_model_name"
        private const val EXTRA_MODEL_PATH = "extra_model_path"
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

        /**
         * Starts a model download via the service
         */
        fun startDownload(context: Context, modelData: ModelData) {
            val intent = Intent(context, ModelDownloadService::class.java).apply {
                action = ACTION_START_DOWNLOAD
                putExtra(EXTRA_MODEL_NAME, modelData.modelName)
                putExtra(EXTRA_MODEL_PATH, modelData.modelPath)
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
         * Cancels an active download
         */
        fun cancelDownload(context: Context, url: String) {
            val intent = Intent(context, ModelDownloadService::class.java).apply {
                action = ACTION_CANCEL_DOWNLOAD
                putExtra(EXTRA_DOWNLOAD_URL, url)
            }

            context.startService(intent)
        }
    }
}