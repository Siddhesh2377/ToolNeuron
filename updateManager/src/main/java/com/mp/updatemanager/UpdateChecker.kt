package com.mp.updatemanager

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URL

object UpdateChecker {

    data class UpdateResult(
        val hasUpdate: Boolean,
        val version: String,
        val whatsNew: List<String> = emptyList()
    )

    suspend fun checkForUpdate(context: Context, url: String): UpdateResult? = withContext(Dispatchers.IO) {
        return@withContext try {
            val jsonText = URL(url).readText()
            val json = JSONObject(jsonText)

            val hasUpdate = json.getBoolean("hasUpdate")
            val version = json.getString("version")
            BuildConfig.VERSION_NAME.let {
                if (it == version) {
                    return@let
                }
                Log.d("UpdateChecker", "New version available: $version")
                Log.d("UpdateChecker", "Current version: $it")
            }
            val whatsNew = json.optJSONArray("whatsNew")?.let { array ->
                List(array.length()) { array.getString(it) }
            } ?: emptyList()

            Log.d("UpdateChecker", "Update check: $hasUpdate, version: $version")
            if (hasUpdate) {
                showUpdateNotification(context, version)
            }

            UpdateResult(hasUpdate, version, whatsNew)
        } catch (e: Exception) {
            Log.e("UpdateChecker", "Update check failed", e)
            null
        }
    }

    // Optional reuse from worker
    fun showUpdateNotification(context: Context, version: String) {
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
