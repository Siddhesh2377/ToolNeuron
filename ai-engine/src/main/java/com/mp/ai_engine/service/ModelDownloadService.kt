package com.mp.ai_engine.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.mp.ai_engine.models.llm_models.CloudModel
import com.mp.ai_engine.workers.installer.DownloadEvents
import com.mp.ai_engine.workers.installer.DownloadProgressManager
import com.mp.ai_engine.workers.installer.DownloadState
import com.mp.ai_engine.workers.installer.InstallerFactory
import com.mp.ai_engine.workers.installer.SuperInstaller
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Enhanced service with integrated progress tracking
 */
class ModelDownloadService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val activeDownloads = mutableMapOf<String, DownloadTask>()
    private lateinit var notificationManager: NotificationManager
    private val json = Json { ignoreUnknownKeys = true }

    // Notification throttling
    private val notificationThrottler = NotificationThrottler()

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
        activeDownloads.values.forEach { it.job.cancel() }
        activeDownloads.clear()
        notificationThrottler.cancel()
        Log.i(TAG, "ModelDownloadService destroyed")
        super.onDestroy()
    }

    private fun handleIntent(intent: Intent) {
        when (intent.action) {
            ACTION_START_DOWNLOAD -> {
                val cloudModel = extractCloudModel(intent) ?: return
                val downloadUrl = intent.getStringExtra(EXTRA_DOWNLOAD_URL) ?: return
                val baseDir = intent.getStringExtra(EXTRA_BASE_DIR)?.let { File(it) } ?: return
                startModelDownload(cloudModel, downloadUrl, baseDir)
            }
            ACTION_CANCEL_DOWNLOAD -> {
                val url = intent.getStringExtra(EXTRA_DOWNLOAD_URL)
                url?.let { cancelModelDownload(it) }
            }
            ACTION_CANCEL_ALL -> {
                cancelAllDownloads()
            }
        }
    }

    private fun extractCloudModel(intent: Intent): CloudModel? {
        return try {
            val jsonString = intent.getStringExtra(EXTRA_CLOUD_MODEL) ?: return null
            json.decodeFromString<CloudModel>(jsonString)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to extract CloudModel", e)
            null
        }
    }

    private fun startModelDownload(cloudModel: CloudModel, downloadUrl: String, baseDir: File) {
        // Check if download is already in progress
        if (activeDownloads.containsKey(downloadUrl)) {
            Log.w(TAG, "Download already in progress for: ${cloudModel.modelName}")
            return
        }

        // Initialize progress tracking
        DownloadProgressManager.updateProgress(downloadUrl, 0f)

        // Get appropriate installer
        val installer = InstallerFactory.getInstaller(cloudModel)
        if (installer == null) {
            Log.e(TAG, "No installer found for model type: ${cloudModel.modelType}")
            DownloadProgressManager.markFailed(
                downloadUrl,
                "Unsupported model type: ${cloudModel.modelType}"
            )
            showErrorNotification(
                cloudModel.modelName,
                "Unsupported model type: ${cloudModel.modelType}"
            )
            return
        }

        // Determine output location
        val outputLocation = installer.determineOutputLocation(cloudModel, baseDir)

        // Create download task
        val job = serviceScope.launch {
            executeDownload(
                installer = installer,
                cloudModel = cloudModel,
                downloadUrl = downloadUrl,
                outputLocation = outputLocation,
                baseDir = baseDir
            )
        }

        activeDownloads[downloadUrl] = DownloadTask(
            job = job,
            installer = installer,
            outputLocation = outputLocation,
            modelName = cloudModel.modelName
        )

        // Update summary notification
        updateSummaryNotification()
    }

    private suspend fun executeDownload(
        installer: SuperInstaller,
        cloudModel: CloudModel,
        downloadUrl: String,
        outputLocation: File,
        baseDir: File
    ) {
        try {
            Log.i(TAG, "Starting download for: ${cloudModel.modelName}")

            // Download phase
            installer.downloadModel(
                cloudModel = cloudModel,
                downloadUrl = downloadUrl,
                outputLocation = outputLocation,
                downloadEvents = object : DownloadEvents {
                    override fun onProgress(progress: Float) {
                        if (progress > 0) {
                            // Update global progress manager
                            DownloadProgressManager.updateProgress(downloadUrl, progress)

                            // Update notification
                            notificationThrottler.updateProgress(
                                downloadUrl,
                                cloudModel.modelName,
                                progress
                            )
                        }
                    }

                    override fun onComplete() {
                        serviceScope.launch {
                            handleDownloadComplete(
                                installer = installer,
                                cloudModel = cloudModel,
                                outputLocation = outputLocation,
                                baseDir = baseDir,
                                downloadUrl = downloadUrl
                            )
                        }
                    }

                    override fun onError(error: Throwable) {
                        handleDownloadError(
                            installer = installer,
                            cloudModel = cloudModel,
                            outputLocation = outputLocation,
                            downloadUrl = downloadUrl,
                            error = error
                        )
                    }
                }
            )

        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error during download: ${e.message}", e)
            handleDownloadError(
                installer = installer,
                cloudModel = cloudModel,
                outputLocation = outputLocation,
                downloadUrl = downloadUrl,
                error = e
            )
        }
    }

    private suspend fun handleDownloadComplete(
        installer: SuperInstaller,
        cloudModel: CloudModel,
        outputLocation: File,
        baseDir: File,
        downloadUrl: String
    ) {
        try {
            // Installation phase
            val result = installer.installModel(cloudModel, outputLocation, baseDir)

            result.onSuccess {
                DownloadProgressManager.markComplete(downloadUrl, outputLocation.absolutePath)
                showCompletionNotification(cloudModel.modelName)
                Log.i(TAG, "Successfully installed: ${cloudModel.modelName}")
            }.onFailure { error ->
                DownloadProgressManager.markFailed(
                    downloadUrl,
                    error.message ?: "Installation failed"
                )
                showErrorNotification(
                    cloudModel.modelName,
                    error.message ?: "Installation failed"
                )
                installer.cleanup(outputLocation)
                Log.e(TAG, "Installation failed for ${cloudModel.modelName}", error)
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error during installation: ${e.message}", e)
            DownloadProgressManager.markFailed(
                downloadUrl,
                e.message ?: "Installation error"
            )
            showErrorNotification(
                cloudModel.modelName,
                e.message ?: "Installation error"
            )
            installer.cleanup(outputLocation)
        } finally {
            activeDownloads.remove(downloadUrl)
            notificationThrottler.removeDownload(downloadUrl)
            updateSummaryNotification()
            checkAndStopService()
        }
    }

    private fun handleDownloadError(
        installer: SuperInstaller,
        cloudModel: CloudModel,
        outputLocation: File,
        downloadUrl: String,
        error: Throwable
    ) {
        val errorMessage = error.message ?: "Download failed"
        DownloadProgressManager.markFailed(downloadUrl, errorMessage)
        showErrorNotification(cloudModel.modelName, errorMessage)
        installer.cleanup(outputLocation)
        Log.e(TAG, "Download failed for ${cloudModel.modelName}", error)

        activeDownloads.remove(downloadUrl)
        notificationThrottler.removeDownload(downloadUrl)
        updateSummaryNotification()
        checkAndStopService()
    }

    private fun cancelModelDownload(url: String) {
        activeDownloads[url]?.let { task ->
            task.job.cancel()
            task.installer.cleanup(task.outputLocation)
            activeDownloads.remove(url)
            notificationThrottler.removeDownload(url)
            DownloadProgressManager.removeDownload(url)
            Log.i(TAG, "Cancelled download: $url")

            updateSummaryNotification()
            checkAndStopService()
        }
    }

    private fun cancelAllDownloads() {
        activeDownloads.keys.toList().forEach { url ->
            cancelModelDownload(url)
        }
    }

    private fun checkAndStopService() {
        if (activeDownloads.isEmpty()) {
            notificationManager.cancel(SUMMARY_NOTIFICATION_ID)
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private fun updateSummaryNotification() {
        if (activeDownloads.isEmpty()) return

        val downloadCount = activeDownloads.size
        val progressData = notificationThrottler.getCurrentProgress()

        // Calculate average progress
        val avgProgress = if (progressData.isNotEmpty()) {
            progressData.values.average().toFloat()
        } else {
            0f
        }

        val contentText = when {
            downloadCount == 1 -> {
                val modelName = activeDownloads.values.first().modelName
                val progress = progressData.values.firstOrNull() ?: 0f
                "$modelName (${progress.toInt()}%)"
            }
            else -> "$downloadCount models downloading"
        }

        // Create cancel all intent
        val cancelIntent = Intent(this, ModelDownloadService::class.java).apply {
            action = ACTION_CANCEL_ALL
        }
        val cancelPendingIntent = PendingIntent.getService(
            this,
            0,
            cancelIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Model Downloads")
            .setContentText(contentText)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setProgress(100, avgProgress.toInt(), false)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setOnlyAlertOnce(true)
            .setGroup(NOTIFICATION_GROUP)
            .setGroupSummary(true)
            .addAction(
                android.R.drawable.ic_delete,
                "Cancel All",
                cancelPendingIntent
            )
            .build()

        startForeground(SUMMARY_NOTIFICATION_ID, notification)
    }

    private fun showCompletionNotification(modelName: String) {
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Download Complete")
            .setContentText(modelName)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setAutoCancel(true)
            .setGroup(NOTIFICATION_GROUP)
            .build()

        notificationManager.notify(modelName.hashCode(), notification)
    }

    private fun showErrorNotification(modelName: String, error: String) {
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Download Failed")
            .setContentText("$modelName: $error")
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setGroup(NOTIFICATION_GROUP)
            .build()

        notificationManager.notify(modelName.hashCode(), notification)
    }

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

    private inner class NotificationThrottler {
        private val progressMap = mutableMapOf<String, Float>()
        private var updateJob: Job? = null
        private val updateInterval = 500L

        fun updateProgress(downloadUrl: String, modelName: String, progress: Float) {
            progressMap[downloadUrl] = progress

            if (updateJob?.isActive != true) {
                updateJob = serviceScope.launch {
                    while (progressMap.isNotEmpty()) {
                        updateSummaryNotification()
                        delay(updateInterval)
                    }
                }
            }
        }

        fun removeDownload(downloadUrl: String) {
            progressMap.remove(downloadUrl)
        }

        fun getCurrentProgress(): Map<String, Float> = progressMap.toMap()

        fun cancel() {
            updateJob?.cancel()
            progressMap.clear()
        }
    }

    private data class DownloadTask(
        val job: Job,
        val installer: SuperInstaller,
        val outputLocation: File,
        val modelName: String
    )

    companion object {
        private const val TAG = "ModelDownloadService"
        private const val ACTION_START_DOWNLOAD = "com.mp.ai_engine.START_DOWNLOAD"
        private const val ACTION_CANCEL_DOWNLOAD = "com.mp.ai_engine.CANCEL_DOWNLOAD"
        private const val ACTION_CANCEL_ALL = "com.mp.ai_engine.CANCEL_ALL"
        private const val EXTRA_CLOUD_MODEL = "extra_cloud_model"
        private const val EXTRA_DOWNLOAD_URL = "extra_download_url"
        private const val EXTRA_BASE_DIR = "extra_base_dir"
        private const val CHANNEL_ID = "model_download_channel"
        private const val SUMMARY_NOTIFICATION_ID = 1000
        private const val NOTIFICATION_GROUP = "model_downloads"

        fun startDownload(
            context: Context,
            cloudModel: CloudModel,
            downloadUrl: String,
            baseDir: File
        ) {
            val json = Json { ignoreUnknownKeys = true }
            val intent = Intent(context, ModelDownloadService::class.java).apply {
                action = ACTION_START_DOWNLOAD
                putExtra(EXTRA_CLOUD_MODEL, json.encodeToString(CloudModel.serializer(), cloudModel))
                putExtra(EXTRA_DOWNLOAD_URL, downloadUrl)
                putExtra(EXTRA_BASE_DIR, baseDir.absolutePath)
            }
            context.startForegroundService(intent)
        }

        fun cancelDownload(context: Context, url: String) {
            val intent = Intent(context, ModelDownloadService::class.java).apply {
                action = ACTION_CANCEL_DOWNLOAD
                putExtra(EXTRA_DOWNLOAD_URL, url)
            }
            context.startService(intent)
        }
    }
}