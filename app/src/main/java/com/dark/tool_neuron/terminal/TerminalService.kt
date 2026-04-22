package com.dark.tool_neuron.terminal

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.dark.tool_neuron.R
import com.dark.tool_neuron.activity.MainActivity

class TerminalService : Service() {

    private val binder = TerminalBinder()
    private var activeSessions = 0

    inner class TerminalBinder : Binder() {
        val service: TerminalService get() = this@TerminalService
    }

    override fun onBind(intent: Intent?): IBinder = binder
    override fun onCreate() {
        super.onCreate()
        ensureChannel()
        startForeground(NOTIFICATION_ID, buildNotification(0))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
            else -> {
                ensureChannel()
                startForeground(NOTIFICATION_ID, buildNotification(activeSessions))
            }
        }
        return START_STICKY
    }

    fun notifySessionCountChanged(count: Int) {
        activeSessions = count
        val nm = getSystemService(NotificationManager::class.java)
        if (count <= 0) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        } else {
            nm.notify(NOTIFICATION_ID, buildNotification(count))
        }
    }

    private fun ensureChannel() {
        val nm = getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Terminal sessions",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "Keeps your shell sessions running while the app is in the background."
            setShowBadge(false)
        }
        nm.createNotificationChannel(channel)
    }

    private fun buildNotification(count: Int): Notification {
        val openPending = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val stopIntent = Intent(this, TerminalService::class.java).apply { action = ACTION_STOP }
        val stopPending = PendingIntent.getService(
            this,
            1,
            stopIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Terminal")
            .setContentText(if (count <= 0) "Idle" else "$count active session${if (count == 1) "" else "s"}")
            .setContentIntent(openPending)
            .addAction(0, "Stop", stopPending)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    companion object {
        const val CHANNEL_ID = "toolneuron.terminal"
        const val NOTIFICATION_ID = 4242
        const val ACTION_STOP = "com.dark.tool_neuron.terminal.STOP"

        fun start(context: Context) {
            val intent = Intent(context, TerminalService::class.java)
            context.startForegroundService(intent)
        }

        fun stop(context: Context) {
            context.startService(Intent(context, TerminalService::class.java).apply {
                action = ACTION_STOP
            })
        }
    }
}
