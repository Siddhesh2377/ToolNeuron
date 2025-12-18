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
import com.mp.ai_engine.R

class ModelOperatorService : Service() {
    companion object {
        private const val TAG = "ModelOperatorService"
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "model_operator_channel"

        /**
         * Start the ModelOperatorService
         */
        fun start(context: Context) {
            val intent = Intent(context, ModelOperatorService::class.java)
            context.startForegroundService(intent)
            Log.i(TAG, "Starting ModelOperatorService")
        }

        /**
         * Stop the ModelOperatorService
         */
        fun stop(context: Context) {
            val intent = Intent(context, ModelOperatorService::class.java)
            context.stopService(intent)
            Log.i(TAG, "Stopping ModelOperatorService")
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private lateinit var notificationManager: NotificationManager

    override fun onCreate() {
        super.onCreate()
        notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        createNotificationChannel()
        startForegroundService()
        Log.i(TAG, "ModelOperatorService created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Handle any intent actions here if needed
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.i(TAG, "ModelOperatorService destroyed")
    }

    private fun startForegroundService() {
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Model Operator")
            .setContentText("Managing AI models")
            .setSmallIcon(R.drawable.ai_model)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()

        startForeground(NOTIFICATION_ID, notification)
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Model Operator",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Notification for AI model operations"
            setShowBadge(false)
        }
        notificationManager.createNotificationChannel(channel)
    }

    /**
     * Update the notification with new content
     */
    fun updateNotification(title: String, text: String, showProgress: Boolean = false, progress: Int = 0) {
        val notificationBuilder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(R.drawable.ai_model)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setOngoing(true)
            .setOnlyAlertOnce(true)

        if (showProgress) {
            notificationBuilder.setProgress(100, progress, false)
        }

        notificationManager.notify(NOTIFICATION_ID, notificationBuilder.build())
    }
}