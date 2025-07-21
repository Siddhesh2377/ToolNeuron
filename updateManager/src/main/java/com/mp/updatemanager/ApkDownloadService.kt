package com.mp.updatemanager

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.FileProvider
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread

class ApkDownloadService : Service() {
    private val channelId = "apk_download_channel"
    private val notificationId = 1001

    override fun onCreate() {
        super.onCreate()
        val channel = NotificationChannel(
            channelId,
            "APK Download",
            NotificationManager.IMPORTANCE_LOW
        )
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager)
            .createNotificationChannel(channel)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val version = intent?.getStringExtra("version") ?: "unknown"
        Log.d("ApkDownloadService", "onStartCommand received")
        startForeground(notificationId, buildNotification(0))

        downloadApk(
            url = "https://raw.githubusercontent.com/Siddhesh2377/NeuroVerse/fresh-new/repo/app-neuroV-release.apk",
            onProgress = { percent ->
                val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                nm.notify(notificationId, buildNotification(percent))
            },
            onDone = { file ->
                installApk(file)
                stopSelf()
            }
        )

        return START_NOT_STICKY
    }

    private fun buildNotification(progress: Int): Notification {
        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("Downloading Update...")
            .setContentText("$progress% downloaded")
            .setSmallIcon(R.mipmap.ic_launcher_monochrome)
            .setProgress(100, progress, false)
            .build()
    }

    private fun downloadApk(url: String, onProgress: (Int) -> Unit, onDone: (File) -> Unit) {
        thread {
            try {
                val connection = URL(url).openConnection() as HttpURLConnection
                connection.connect()
                val totalSize = connection.contentLength

                val file = File(applicationContext.cacheDir, "update.apk")
                val input = BufferedInputStream(connection.inputStream)
                val output = FileOutputStream(file)

                val data = ByteArray(1024)
                var downloaded = 0
                var count: Int

                while (input.read(data).also { count = it } != -1) {
                    downloaded += count
                    output.write(data, 0, count)
                    val progress = (downloaded * 100) / totalSize
                    onProgress(progress)
                }

                output.flush()
                output.close()
                input.close()

                onDone(file)

            } catch (e: Exception) {
                Log.e("ApkDownload", "Failed", e)
            }
        }
    }

    private fun installApk(file: File) {
        val uri = FileProvider.getUriForFile(
            this,
            "${packageName}.provider",
            file
        )

        val installIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK
        }

        val pendingIntent = PendingIntent.getActivity(
            this,
            200,
            installIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val installChannelId = "install_channel"

        val installChannel = NotificationChannel(
            installChannelId,
            "Installer",
            NotificationManager.IMPORTANCE_HIGH
        )
        nm.createNotificationChannel(installChannel)

        val notification = NotificationCompat.Builder(this, installChannelId)
            .setContentTitle("Update Ready")
            .setContentText("Tap to install the new version")
            .setSmallIcon(R.mipmap.ic_launcher_monochrome)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        nm.notify(2002, notification)
    }


    override fun onBind(intent: Intent?): IBinder? = null
}
