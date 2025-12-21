package com.mp.ai_engine.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.mp.ai_engine.IModelOperatorService
import com.mp.ai_engine.R
import com.mp.ai_engine.diffusion.IDiffusionOperations
import com.mp.ai_engine.gguf.IGGUFOperations
import com.mp.ai_engine.installer.IModelInstaller
import com.mp.ai_engine.openrouter.IOpenRouterOperations
import com.mp.ai_engine.workers.aidl.DiffusionOperationsImpl
import com.mp.ai_engine.workers.aidl.GGUFOperationsImpl
import com.mp.ai_engine.workers.model.internal_model_worker.DiffusionModelWorker
import com.mp.ai_engine.workers.model.internal_model_worker.GGUFModelWorker
import org.json.JSONObject

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

    private lateinit var notificationManager: NotificationManager

    // Worker instances
    private val ggufWorker = GGUFModelWorker()
    // TODO: Initialize other workers when implementing
    // private val openRouterWorker = OpenRouterWorker()
     private val diffusionWorker = DiffusionModelWorker(this)
    // private val modelInstaller = ModelInstaller(this)

    // AIDL Binder
    private val binder = ModelOperatorBinder()

    override fun onCreate() {
        super.onCreate()
        notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        createNotificationChannel()
        startForegroundService()
        Log.i(TAG, "ModelOperatorService created")
    }

    override fun onBind(intent: Intent?): IBinder {
        Log.i(TAG, "Client binding to ModelOperatorService")
        return binder
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        // Cleanup all workers
        try {
            ggufWorker.unloadModel()
            // TODO: Cleanup other workers
            // openRouterWorker.unloadModel()
             diffusionWorker.unloadModel()
            Log.i(TAG, "All models unloaded successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error during cleanup", e)
        }
        Log.i(TAG, "ModelOperatorService destroyed")
    }

    /**
     * AIDL Binder Implementation
     */
    inner class ModelOperatorBinder : IModelOperatorService.Stub() {

        override fun getGGUFOperations(): IGGUFOperations {
            Log.d(TAG, "Client requested GGUF operations interface")
            return GGUFOperationsImpl(ggufWorker)
        }

        override fun getOpenRouterOperations(): IOpenRouterOperations {
            Log.d(TAG, "Client requested OpenRouter operations interface")
            // TODO: Implement when OpenRouter worker is ready
            throw UnsupportedOperationException("OpenRouter operations not yet implemented")
            // return OpenRouterOperationsImpl(openRouterWorker)
        }

        override fun getDiffusionOperations(): IDiffusionOperations {
            Log.d(TAG, "Client requested Diffusion operations interface")
            // TODO: Implement when Diffusion worker is ready
            return DiffusionOperationsImpl(diffusionWorker)
        }

        override fun getInstaller(): IModelInstaller {
            Log.d(TAG, "Client requested Installer interface")
            // TODO: Implement when Installer is ready
            throw UnsupportedOperationException("Model installer not yet implemented")
            // return ModelInstallerImpl(modelInstaller)
        }

        override fun getServiceStatus(): String {
            return try {
                JSONObject().apply {
                    put("service_running", true)
                    put("timestamp", System.currentTimeMillis())
                }.toString()
            } catch (e: Exception) {
                Log.e(TAG, "Error getting service status", e)
                JSONObject().apply {
                    put("error", e.message ?: "Unknown error")
                }.toString()
            }
        }

        override fun shutdown() {
            Log.i(TAG, "Shutdown requested by client")
            try {
                ggufWorker.unloadModel()
                // TODO: Unload other workers
                // Stop the service
                stopSelf()
                Log.i(TAG, "Service shutdown completed")
            } catch (e: Exception) {
                Log.e(TAG, "Error during shutdown", e)
            }
        }
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
     * Can be called by workers to show progress
     */
    fun updateNotification(
        title: String,
        text: String,
        showProgress: Boolean = false,
        progress: Int = 0
    ) {
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