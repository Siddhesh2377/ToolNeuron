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
import com.mp.ai_engine.workers.DownloadState
import com.mp.ai_engine.workers.installer.DownloadEvents
import com.mp.ai_engine.workers.installer.DownloadProgressManager
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
 * Optimized foreground service for downloading and installing models
 * Handles multiple concurrent downloads with proper progress tracking
 */
class ModelDownloadService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val activeJobs = mutableMapOf<String, Job>()
    private lateinit var notificationManager: NotificationManager
    private val json = Json { ignoreUnknownKeys = true }

    private var notificationUpdateJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        createNotificationChannel()
        startNotificationUpdates()
        Log.i(TAG, "ModelDownloadService created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        intent?.let { handleIntent(it) }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        notificationUpdateJob?.cancel()
        serviceScope.cancel()
        activeJobs.values.forEach { it.cancel() }
        activeJobs.clear()
        Log.i(TAG, "ModelDownloadService destroyed")
        super.onDestroy()
    }

    private fun handleIntent(intent: Intent) {
        when (intent.action) {
            ACTION_START_DOWNLOAD -> {
                val cloudModel = extractCloudModel(intent) ?: return
                val baseDir = intent.getStringExtra(EXTRA_BASE_DIR)?.let { File(it) } ?: return
                startModelDownload(cloudModel, baseDir)
            }

            ACTION_CANCEL_DOWNLOAD -> {
                val downloadId = intent.getStringExtra(EXTRA_DOWNLOAD_ID)
                downloadId?.let { cancelDownload(it) }
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

    private fun startModelDownload(cloudModel: CloudModel, baseDir: File) {
        val downloadId = cloudModel.modelFileLink

        // Check if download is already active
        if (DownloadProgressManager.isActive(downloadId)) {
            Log.w(TAG, "Download already in progress: ${cloudModel.modelName}")
            return
        }

        // Get appropriate installer
        val installer = InstallerFactory.getInstaller(cloudModel)
        if (installer == null) {
            val error = "Unsupported model type: ${cloudModel.modelType}"
            Log.e(TAG, error)
            DownloadProgressManager.markFailed(downloadId, error)
            showErrorNotification(cloudModel.modelName, error)
            return
        }

        // Initialize download tracking
        DownloadProgressManager.startDownload(downloadId, cloudModel.modelName)

        // Determine output location
        val outputLocation = installer.determineOutputLocation(cloudModel, baseDir)

        // Start download job
        val job = serviceScope.launch {
            executeDownload(
                downloadId = downloadId,
                installer = installer,
                cloudModel = cloudModel,
                outputLocation = outputLocation,
                baseDir = baseDir
            )
        }

        activeJobs[downloadId] = job
        Log.i(TAG, "Started download: ${cloudModel.modelName}")
    }

    private suspend fun executeDownload(
        downloadId: String,
        installer: SuperInstaller,
        cloudModel: CloudModel,
        outputLocation: File,
        baseDir: File
    ) {
        try {
            // Download phase
            installer.downloadModel(
                cloudModel = cloudModel,
                downloadUrl = cloudModel.modelFileLink,
                outputLocation = outputLocation,
                downloadEvents = object : DownloadEvents {
                    override fun onProgress(progress: Float) {
                        DownloadProgressManager.updateProgress(downloadId, progress)
                    }

                    override fun onComplete() {
                        serviceScope.launch {
                            handleDownloadComplete(
                                downloadId = downloadId,
                                installer = installer,
                                cloudModel = cloudModel,
                                outputLocation = outputLocation,
                                baseDir = baseDir
                            )
                        }
                    }

                    override fun onError(error: Throwable) {
                        handleDownloadError(
                            downloadId = downloadId,
                            installer = installer,
                            cloudModel = cloudModel,
                            outputLocation = outputLocation,
                            error = error
                        )
                    }
                })

        } catch (e: Exception) {
            Log.e(TAG, "Download exception: ${e.message}", e)
            handleDownloadError(
                downloadId = downloadId,
                installer = installer,
                cloudModel = cloudModel,
                outputLocation = outputLocation,
                error = e
            )
        }
    }

    private suspend fun handleDownloadComplete(
        downloadId: String,
        installer: SuperInstaller,
        cloudModel: CloudModel,
        outputLocation: File,
        baseDir: File
    ) {
        try {
            // Mark as installing
            DownloadProgressManager.markInstalling(downloadId)

            // Installation phase
            val result = installer.installModel(cloudModel, outputLocation, baseDir)

            result.onSuccess {
                DownloadProgressManager.markComplete(downloadId, outputLocation.absolutePath)
                showCompletionNotification(cloudModel.modelName)
                Log.i(TAG, "Successfully installed: ${cloudModel.modelName}")
            }.onFailure { error ->
                val errorMsg = error.message ?: "Installation failed"
                DownloadProgressManager.markFailed(downloadId, errorMsg)
                showErrorNotification(cloudModel.modelName, errorMsg)
                installer.cleanup(outputLocation)
                Log.e(TAG, "Installation failed: ${cloudModel.modelName}", error)
            }

        } catch (e: Exception) {
            val errorMsg = e.message ?: "Installation error"
            Log.e(TAG, "Installation exception: ${errorMsg}", e)
            DownloadProgressManager.markFailed(downloadId, errorMsg)
            showErrorNotification(cloudModel.modelName, errorMsg)
            installer.cleanup(outputLocation)
        } finally {
            activeJobs.remove(downloadId)
            checkAndStopService()
        }
    }

    private fun handleDownloadError(
        downloadId: String,
        installer: SuperInstaller,
        cloudModel: CloudModel,
        outputLocation: File,
        error: Throwable
    ) {
        val errorMsg = error.message ?: "Download failed"
        DownloadProgressManager.markFailed(downloadId, errorMsg)
        showErrorNotification(cloudModel.modelName, errorMsg)
        installer.cleanup(outputLocation)
        Log.e(TAG, "Download failed: ${cloudModel.modelName}", error)

        activeJobs.remove(downloadId)
        checkAndStopService()
    }

    private fun cancelDownload(downloadId: String) {
        activeJobs[downloadId]?.let { job ->
            job.cancel()
            DownloadProgressManager.markCancelled(downloadId)
            activeJobs.remove(downloadId)
            Log.i(TAG, "Cancelled download: $downloadId")
            checkAndStopService()
        }
    }

    private fun cancelAllDownloads() {
        activeJobs.keys.toList().forEach { downloadId ->
            cancelDownload(downloadId)
        }
    }

    private fun checkAndStopService() {
        if (activeJobs.isEmpty()) {
            notificationManager.cancel(SUMMARY_NOTIFICATION_ID)
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    // Notification Management

    private fun startNotificationUpdates() {
        notificationUpdateJob = serviceScope.launch {
            while (true) {
                val activeDownloads = DownloadProgressManager.getActiveDownloads()
                if (activeDownloads.isNotEmpty()) {
                    updateSummaryNotification(activeDownloads)
                }
                delay(500) // Update every 500ms
            }
        }
    }

    private fun updateSummaryNotification(activeDownloads: List<DownloadState>) {
        if (activeDownloads.isEmpty()) return

        val downloadCount = activeDownloads.size

        // Calculate average progress
        val avgProgress =
            activeDownloads.filterIsInstance<DownloadState.Downloading>().takeIf { it.isNotEmpty() }
                ?.map { it.progress }?.average()?.toFloat() ?: 0f

        val contentText = when {
            downloadCount == 1 -> {
                when (val download = activeDownloads.first()) {
                    is DownloadState.Downloading -> "${download.modelName} (${download.progressPercent}%)"
                    is DownloadState.Installing -> "${download.modelName} (Installing...)"
                    else -> download.modelName
                }
            }

            else -> "$downloadCount models downloading"
        }

        // Cancel all intent
        val cancelIntent = Intent(this, ModelDownloadService::class.java).apply {
            action = ACTION_CANCEL_ALL
        }
        val cancelPendingIntent = PendingIntent.getService(
            this, 0, cancelIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification =
            NotificationCompat.Builder(this, CHANNEL_ID).setContentTitle("Model Downloads")
                .setContentText(contentText).setSmallIcon(android.R.drawable.stat_sys_download)
                .setProgress(100, (avgProgress * 100).toInt(), false).setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setCategory(NotificationCompat.CATEGORY_PROGRESS).setOnlyAlertOnce(true)
                .setGroup(NOTIFICATION_GROUP).setGroupSummary(true).addAction(
                    android.R.drawable.ic_delete, "Cancel All", cancelPendingIntent
                ).build()

        startForeground(SUMMARY_NOTIFICATION_ID, notification)
    }

    private fun showCompletionNotification(modelName: String) {
        val notification =
            NotificationCompat.Builder(this, CHANNEL_ID).setContentTitle("Download Complete")
                .setContentText(modelName).setSmallIcon(android.R.drawable.stat_sys_download_done)
                .setPriority(NotificationCompat.PRIORITY_LOW).setAutoCancel(true)
                .setGroup(NOTIFICATION_GROUP).build()

        notificationManager.notify(modelName.hashCode(), notification)
    }

    private fun showErrorNotification(modelName: String, error: String) {
        val notification =
            NotificationCompat.Builder(this, CHANNEL_ID).setContentTitle("Download Failed")
                .setContentText("$modelName: $error")
                .setSmallIcon(android.R.drawable.stat_notify_error)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT).setAutoCancel(true)
                .setGroup(NOTIFICATION_GROUP).build()

        notificationManager.notify(modelName.hashCode(), notification)
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID, "Model Downloads", NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Notifications for AI model downloads"
            setShowBadge(false)
        }
        notificationManager.createNotificationChannel(channel)
    }

    companion object {
        private const val TAG = "ModelDownloadService"
        private const val ACTION_START_DOWNLOAD = "com.mp.ai_engine.START_DOWNLOAD"
        private const val ACTION_CANCEL_DOWNLOAD = "com.mp.ai_engine.CANCEL_DOWNLOAD"
        private const val ACTION_CANCEL_ALL = "com.mp.ai_engine.CANCEL_ALL"
        private const val EXTRA_CLOUD_MODEL = "extra_cloud_model"
        private const val EXTRA_DOWNLOAD_ID = "extra_download_id"
        private const val EXTRA_BASE_DIR = "extra_base_dir"
        private const val CHANNEL_ID = "model_download_channel"
        private const val SUMMARY_NOTIFICATION_ID = 1000
        private const val NOTIFICATION_GROUP = "model_downloads"

        fun startDownload(
            context: Context, cloudModel: CloudModel, baseDir: File
        ) {
            val json = Json { ignoreUnknownKeys = true }
            val intent = Intent(context, ModelDownloadService::class.java).apply {
                action = ACTION_START_DOWNLOAD
                putExtra(
                    EXTRA_CLOUD_MODEL,
                    json.encodeToString(CloudModel.serializer(), cloudModel)
                )
                putExtra(EXTRA_BASE_DIR, baseDir.absolutePath)
            }
            context.startForegroundService(intent)
        }

        fun cancelDownload(context: Context, downloadId: String) {
            val intent = Intent(context, ModelDownloadService::class.java).apply {
                action = ACTION_CANCEL_DOWNLOAD
                putExtra(EXTRA_DOWNLOAD_ID, downloadId)
            }
            context.startService(intent)
        }

        fun cancelAll(context: Context) {
            val intent = Intent(context, ModelDownloadService::class.java).apply {
                action = ACTION_CANCEL_ALL
            }
            context.startService(intent)
        }
    }
}