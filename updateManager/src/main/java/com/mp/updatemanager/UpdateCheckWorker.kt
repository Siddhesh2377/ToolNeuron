package com.mp.updatemanager

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
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
            "https://raw.githubusercontent.com/Siddhesh2377/NeuroVerse/fresh-new/repo/AppUpdate.json?ts=${System.currentTimeMillis()}"
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

        // Create channel if needed
        val channel =
            NotificationChannel(channelId, "App Updates", NotificationManager.IMPORTANCE_HIGH)
        (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(
                channel
            )

        val builder =
            NotificationCompat.Builder(context, channelId).setContentTitle("New Update Available")
                .setContentText("Version $version is ready to download.")
                .setSmallIcon(R.mipmap.ic_launcher_monochrome)
                .setPriority(NotificationCompat.PRIORITY_HIGH)

        NotificationManagerCompat.from(context).notify(999, builder.build())
    }
}