package com.mp.updatemanager

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import org.json.JSONObject
import java.net.URL

class UpdateCheckWorker(
    context: Context, params: WorkerParameters
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val jsonUrl =
            "https://raw.githubusercontent.com/Siddhesh2377/NeuroVerse/refs/heads/fresh-new/repo/AppUpdate.json"
        return try {
            val text = URL(jsonUrl).readText()
            val json = JSONObject(text)

            val hasUpdate = json.getBoolean("hasUpdate")
            val version = json.getString("version")

            Log.d("UpdateCheck", "Has update: $hasUpdate, version: $version")

            if (hasUpdate) {
                Log.d("UpdateCheck", "Update available: $version")

                // Optional: Notify UI or store flag
                showUpdateNotification(applicationContext, version)
            }

            Result.success()
        } catch (e: Exception) {
            Log.e("UpdateCheck", "Failed to check update", e)
            Result.retry()
        }
    }

    @SuppressLint("MissingPermission")
    private fun showUpdateNotification(context: Context, version: String) {
        val channelId = "update_channel"

        val channel = NotificationChannel(
            channelId,
            "App Updates",
            NotificationManager.IMPORTANCE_HIGH
        )
        (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
            .createNotificationChannel(channel)

        val downloadIntent = Intent(context, UpdateActionReceiver::class.java).apply {
            action = "com.mp.ACTION_DOWNLOAD_UPDATE"
            putExtra("version", version)
        }

        val downloadPendingIntent = PendingIntent.getBroadcast(
            context,
            101,
            downloadIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )


        val builder = NotificationCompat.Builder(context, channelId)
            .setContentTitle("New Update Available")
            .setContentText("Version $version is ready to download.")
            .setSmallIcon(R.mipmap.ic_launcher_monochrome)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .addAction(R.mipmap.ic_launcher_monochrome, "Download", downloadPendingIntent)

        NotificationManagerCompat.from(context).notify(999, builder.build())
    }

}