package com.dark.tool_neuron.di

import android.app.Application
import android.content.Context
import android.util.Log
import com.dark.tool_neuron.data.VaultManager
import com.dark.tool_neuron.database.AppDatabase
import com.dark.tool_neuron.repo.ModelRepository
import com.dark.tool_neuron.repo.ums.UmsMemoryRepository
import com.dark.tool_neuron.repo.ums.UmsPersonaRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

object AppContainer {

    // Room database kept for RAG (FTS4) and migration reads only
    private lateinit var database: AppDatabase
    private var modelRepository: ModelRepository? = null

    private val appScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private lateinit var appContext: Context

    private const val TAG = "AppContainer"

    fun init(context: Context, application: Application) {
        appContext = context.applicationContext

        // Keep Room database for RAG (FTS4) and one-time migration reads
        database = AppDatabase.getDatabase(context)

        // Initialize UMS storage (plaintext by default; VaultGateScreen can switch to encrypted)
        // Don't auto-init here — let the onboarding flow handle vault initialization
        if (VaultManager.isReady.value) {
            initModelRepository()
        } else {
            Log.w(TAG, "VaultManager not ready at init — ModelRepository deferred")
        }

    }

    private fun initModelRepository() {
        val mRepo = VaultManager.modelRepo ?: return
        val cRepo = VaultManager.configRepo ?: return
        modelRepository = ModelRepository(modelRepo = mRepo, configRepo = cRepo)
    }

    fun ensureVaultInitialized() {
        if (!VaultManager.isReady.value && ::appContext.isInitialized) {
            val ok = VaultManager.initPlaintext(appContext)
            if (!ok) Log.e(TAG, "VaultManager re-init failed")
        }
        if (modelRepository == null && VaultManager.isReady.value) {
            initModelRepository()
        }
    }

    fun shutdown() {
        appScope.cancel()
        VaultManager.close()
    }

    /**
     * Close the Room database for backup/restore operations.
     */
    fun closeDatabase() {
        AppDatabase.closeDatabase()
    }

    /**
     * Re-initialize the entire container after a restore operation.
     */
    fun reinitialize(context: Context) {
        val ctx = context.applicationContext
        appContext = ctx
        database = AppDatabase.getDatabase(ctx)

        VaultManager.close()
        val ok = VaultManager.initPlaintext(ctx)
        if (!ok) {
            Log.e(TAG, "VaultManager reinit failed")
        } else {
            initModelRepository()
        }

    }

    // Room database — for RAG (FTS4) and migration only
    fun getDatabase(): AppDatabase = database

    fun getModelRepository(): ModelRepository {
        if (modelRepository == null) ensureVaultInitialized()
        return modelRepository ?: error("VaultManager not initialized — cannot access ModelRepository")
    }

    fun getPersonaRepo(): UmsPersonaRepository {
        if (VaultManager.personaRepo == null) ensureVaultInitialized()
        return VaultManager.personaRepo ?: error("VaultManager not initialized — cannot access PersonaRepository")
    }

    fun getMemoryRepo(): UmsMemoryRepository {
        if (VaultManager.memoryRepo == null) ensureVaultInitialized()
        return VaultManager.memoryRepo ?: error("VaultManager not initialized — cannot access MemoryRepository")
    }

}