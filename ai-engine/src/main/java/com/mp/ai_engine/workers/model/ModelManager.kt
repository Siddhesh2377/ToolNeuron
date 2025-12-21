package com.mp.ai_engine.workers.model

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.util.Log
import com.mp.ai_engine.IModelOperatorService
import com.mp.ai_engine.diffusion.IDiffusionOperations
import com.mp.ai_engine.gguf.IGGUFOperations
import com.mp.ai_engine.installer.IModelInstaller
import com.mp.ai_engine.openrouter.IOpenRouterOperations
import com.mp.ai_engine.service.ModelOperatorService
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeout

/**
 * Client-side manager for accessing ModelOperatorService
 * Provides easy access to all AI model operations
 */
object ModelManager {

    private const val TAG = "ModelManager"
    private const val CONNECTION_TIMEOUT_MS = 10000L

    private var service: IModelOperatorService? = null
    private var isConnected = false
    private var connectionDeferred: CompletableDeferred<Unit>? = null

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, binder: IBinder) {
            Log.i(TAG, "Connected to ModelOperatorService")
            service = IModelOperatorService.Stub.asInterface(binder)
            isConnected = true
            connectionDeferred?.complete(Unit)
        }

        override fun onServiceDisconnected(name: ComponentName) {
            Log.w(TAG, "Disconnected from ModelOperatorService")
            service = null
            isConnected = false
            connectionDeferred?.completeExceptionally(
                IllegalStateException("Service disconnected")
            )
        }
    }

    /**
     * Initialize and bind to the ModelOperatorService
     * Must be called before using any operations
     */
    suspend fun init(context: Context) {
        if (isConnected) {
            Log.d(TAG, "Already connected to service")
            return
        }

        connectionDeferred = CompletableDeferred()

        // Start the service
        ModelOperatorService.start(context)

        // Bind to the service
        val intent = Intent(context, ModelOperatorService::class.java)
        val bound = context.bindService(
            intent,
            connection,
            Context.BIND_AUTO_CREATE or Context.BIND_IMPORTANT
        )

        if (!bound) {
            throw IllegalStateException("Failed to bind to ModelOperatorService")
        }

        // Wait for connection with timeout
        try {
            withTimeout(CONNECTION_TIMEOUT_MS) {
                connectionDeferred?.await()
            }
            Log.i(TAG, "Successfully initialized ModelManager")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to connect to service", e)
            throw e
        }
    }

    /**
     * Disconnect from the service
     */
    fun shutdown(context: Context) {
        try {
            if (isConnected) {
                service?.shutdown()
            }
            context.unbindService(connection)
            service = null
            isConnected = false
            Log.i(TAG, "ModelManager shutdown complete")
        } catch (e: Exception) {
            Log.e(TAG, "Error during shutdown", e)
        }
    }

    /**
     * Get GGUF operations interface
     */
    fun gguf(): IGGUFOperations {
        return service?.getGGUFOperations()
            ?: throw IllegalStateException("Service not connected. Call init() first.")
    }

    /**
     * Get OpenRouter operations interface
     */
    fun openRouter(): IOpenRouterOperations {
        return service?.getOpenRouterOperations()
            ?: throw IllegalStateException("Service not connected. Call init() first.")
    }

    /**
     * Get Diffusion operations interface
     */
    fun diffusion(): IDiffusionOperations {
        return service?.getDiffusionOperations()
            ?: throw IllegalStateException("Service not connected. Call init() first.")
    }

    /**
     * Get Model Installer interface
     */
    fun installer(): IModelInstaller {
        return service?.getInstaller()
            ?: throw IllegalStateException("Service not connected. Call init() first.")
    }

    /**
     * Get service status as JSON string
     */
    fun getStatus(): String {
        return service?.getServiceStatus()
            ?: throw IllegalStateException("Service not connected. Call init() first.")
    }

    /**
     * Check if connected to service
     */
    fun isConnected(): Boolean = isConnected
}