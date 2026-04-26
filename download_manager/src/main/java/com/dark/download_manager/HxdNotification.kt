package com.dark.download_manager

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat

internal object HxdNotification {

    const val NOTIFICATION_ID = 0x48584400

    private const val CHANNEL_ID   = "hxd_downloads"
    private const val CHANNEL_NAME = "Downloads"

    fun createChannel(context: Context) {
        val channel = NotificationChannel(
            CHANNEL_ID,
            CHANNEL_NAME,
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description  = "Active file download progress"
            setShowBadge(false)
            enableVibration(false)
            enableLights(false)
        }
        context.getSystemService(NotificationManager::class.java)
            .createNotificationChannel(channel)
    }

    fun build(context: Context, active: List<HxdState>): Notification {
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.download_icon)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setVisibility(NotificationCompat.VISIBILITY_SECRET)

        return when {
            active.isEmpty() -> builder
                .setContentTitle("Downloads")
                .setContentText("All downloads complete")
                .setProgress(0, 0, false)
                .setOngoing(false)
                .build()

            active.size == 1 -> {
                val s       = active[0]
                val name    = s.destPath.substringAfterLast('/')
                val pct     = if (s.totalBytes > 0)
                    ((s.downloadedBytes * 100L) / s.totalBytes).toInt() else 0
                val indeterminate = s.totalBytes <= 0
                builder
                    .setContentTitle(name)
                    .setContentText(s.speedFormatted.ifEmpty { "Connecting…" })
                    .setProgress(100, pct, indeterminate)
                    .setSubText(formatBytes(s.downloadedBytes) +
                            if (s.totalBytes > 0) " / ${formatBytes(s.totalBytes)}" else "")
                    .build()
            }

            else -> {
                val totalDown = active.sumOf { it.downloadedBytes }
                val totalSize = active.fold(0L) { acc, s ->
                    if (s.totalBytes > 0) acc + s.totalBytes else acc
                }
                val pct           = if (totalSize > 0) ((totalDown * 100L) / totalSize).toInt() else 0
                val indeterminate = totalSize <= 0
                val totalSpeed    = active.sumOf { it.speedBps }

                val inboxStyle = NotificationCompat.InboxStyle()
                active.forEach { s ->
                    val name = s.destPath.substringAfterLast('/').take(32)
                    val line = if (s.totalBytes > 0)
                        "$name — ${(s.downloadedBytes * 100L / s.totalBytes)}%"
                    else
                        "$name — ${formatBytes(s.downloadedBytes)}"
                    inboxStyle.addLine(line)
                }

                builder
                    .setContentTitle("${active.size} downloads active")
                    .setContentText(formatSpeed(totalSpeed))
                    .setProgress(100, pct, indeterminate)
                    .setStyle(inboxStyle)
                    .build()
            }
        }
    }

    private fun formatSpeed(bps: Long): String = when {
        bps >= 1_000_000L -> "%.1f MB/s".format(bps / 1_000_000f)
        bps >= 1_000L     -> "%.1f KB/s".format(bps / 1_000f)
        bps > 0L          -> "$bps B/s"
        else              -> ""
    }

}
