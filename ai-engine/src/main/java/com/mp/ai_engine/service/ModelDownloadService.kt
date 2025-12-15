package com.mp.ai_engine.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.mp.ai_engine.models.llm_models.CloudModel
import com.mp.ai_engine.workers.installer.DownloadEvents
import com.mp.ai_engine.workers.installer.InstallerFactory
import com.mp.ai_engine.workers.installer.SuperInstaller
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Refactored service using dedicated installers for scalable model downloads
 */
class ModelDownloadService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val activeDownloads = mutableMapOf<String, DownloadTask>()
    private var notificationId = NOTIFICATION_ID_START
    private lateinit var notificationManager: NotificationManager
    private val json = Json { ignoreUnknownKeys = true }

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

        // Get appropriate installer
        val installer = InstallerFactory.getInstaller(cloudModel)
        if (installer == null) {
            Log.e(TAG, "No installer found for model type: ${cloudModel.modelType}")
            showDownloadErrorNotification(
                notificationId++,
                cloudModel.modelName,
                "Unsupported model type: ${cloudModel.modelType}"
            )
            return
        }

        // Determine output location
        val outputLocation = installer.determineOutputLocation(cloudModel, baseDir)
        val currentNotificationId = notificationId++

        // Create download task
        val job = serviceScope.launch {
            executeDownload(
                installer = installer,
                cloudModel = cloudModel,
                downloadUrl = downloadUrl,
                outputLocation = outputLocation,
                baseDir = baseDir,
                notificationId = currentNotificationId
            )
        }

        activeDownloads[downloadUrl] = DownloadTask(
            job = job,
            installer = installer,
            outputLocation = outputLocation
        )
    }

    private suspend fun executeDownload(
        installer: SuperInstaller,
        cloudModel: CloudModel,
        downloadUrl: String,
        outputLocation: File,
        baseDir: File,
        notificationId: Int
    ) {
        try {
            Log.i(TAG, "Starting download for: ${cloudModel.modelName}")
            showDownloadNotification(notificationId, cloudModel.modelName, 0f)

            // Download phase
            installer.downloadModel(
                cloudModel = cloudModel,
                downloadUrl = downloadUrl,
                outputLocation = outputLocation,
                downloadEvents = object : DownloadEvents {
                    override fun onProgress(progress: Float) {
                        if (progress > 0) {
                            showDownloadNotification(
                                notificationId,
                                cloudModel.modelName,
                                progress * 100
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
                                notificationId = notificationId,
                                downloadUrl = downloadUrl
                            )
                        }
                    }

                    override fun onError(error: Throwable) {
                        handleDownloadError(
                            installer = installer,
                            cloudModel = cloudModel,
                            outputLocation = outputLocation,
                            notificationId = notificationId,
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
                notificationId = notificationId,
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
        notificationId: Int,
        downloadUrl: String
    ) {
        try {
            // Installation phase
            val result = installer.installModel(cloudModel, outputLocation, baseDir)

            result.onSuccess {
                showDownloadCompleteNotification(notificationId, cloudModel.modelName)
                Log.i(TAG, "Successfully installed: ${cloudModel.modelName}")
            }.onFailure { error ->
                showDownloadErrorNotification(
                    notificationId,
                    cloudModel.modelName,
                    error.message ?: "Installation failed"
                )
                installer.cleanup(outputLocation)
                Log.e(TAG, "Installation failed for ${cloudModel.modelName}", error)
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error during installation: ${e.message}", e)
            showDownloadErrorNotification(
                notificationId,
                cloudModel.modelName,
                e.message ?: "Installation error"
            )
            installer.cleanup(outputLocation)
        } finally {
            activeDownloads.remove(downloadUrl)
            checkAndStopService()
        }
    }

    private fun handleDownloadError(
        installer: SuperInstaller,
        cloudModel: CloudModel,
        outputLocation: File,
        notificationId: Int,
        downloadUrl: String,
        error: Throwable
    ) {
        showDownloadErrorNotification(
            notificationId,
            cloudModel.modelName,
            error.message ?: "Download failed"
        )
        installer.cleanup(outputLocation)
        Log.e(TAG, "Download failed for ${cloudModel.modelName}", error)

        activeDownloads.remove(downloadUrl)
        checkAndStopService()
    }

    private fun cancelModelDownload(url: String) {
        activeDownloads[url]?.let { task ->
            task.job.cancel()
            task.installer.cleanup(task.outputLocation)
            activeDownloads.remove(url)
            Log.i(TAG, "Cancelled download: $url")

            checkAndStopService()
        }
    }

    private fun checkAndStopService() {
        if (activeDownloads.isEmpty()) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
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

    private fun showDownloadNotification(id: Int, modelName: String, progress: Float) {
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Downloading: $modelName")
            .setContentText("${progress.toInt()}%")
            .setSmallIcon(android.R.drawable.stat_sys_download)
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
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(id, notification)
    }

    private fun showDownloadErrorNotification(id: Int, modelName: String, error: String) {
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Download Failed")
            .setContentText("$modelName: $error")
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(id, notification)
    }

    private data class DownloadTask(
        val job: Job,
        val installer: SuperInstaller,
        val outputLocation: File
    )

    companion object {
        private const val TAG = "ModelDownloadService"
        private const val ACTION_START_DOWNLOAD = "com.mp.ai_engine.START_DOWNLOAD"
        private const val ACTION_CANCEL_DOWNLOAD = "com.mp.ai_engine.CANCEL_DOWNLOAD"
        private const val EXTRA_CLOUD_MODEL = "extra_cloud_model"
        private const val EXTRA_DOWNLOAD_URL = "extra_download_url"
        private const val EXTRA_BASE_DIR = "extra_base_dir"
        private const val CHANNEL_ID = "model_download_channel"
        private const val NOTIFICATION_ID_START = 1000

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