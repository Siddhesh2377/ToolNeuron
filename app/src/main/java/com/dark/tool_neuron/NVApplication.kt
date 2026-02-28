package com.dark.tool_neuron

import android.app.Application
import android.util.Log
import com.dark.tool_neuron.data.AppSettingsDataStore
import com.dark.tool_neuron.di.AppContainer
import com.dark.tool_neuron.plugins.DeviceInfoPlugin
import com.dark.tool_neuron.plugins.FileManagerPlugin
import com.dark.tool_neuron.plugins.PluginManager
import com.dark.tool_neuron.plugins.WebSearchPlugin
import com.dark.tool_neuron.repo.RagRepository
import com.dark.tool_neuron.tts.TTSManager
import com.dark.tool_neuron.ui.theme.ThemeEngine
import com.dark.tool_neuron.vault.VaultHelper
import com.dark.tool_neuron.worker.DataIntegrityManager
import com.dark.tool_neuron.worker.KnowledgeGraphBuilder
import com.dark.tool_neuron.worker.LlmModelWorker
import com.dark.tool_neuron.worker.MemorySummaryWorker
import dagger.hilt.android.HiltAndroidApp
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

@HiltAndroidApp
class NVApplication : Application() {

    companion object {
        private const val TAG = "NVApplication"
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Application onCreate")

        // Initialize app container first
        AppContainer.init(applicationContext, this)

        // Register plugins
        PluginManager.registerPlugin(WebSearchPlugin())
        PluginManager.registerPlugin(DeviceInfoPlugin(applicationContext))
        PluginManager.registerPlugin(FileManagerPlugin(applicationContext))
        Log.d(TAG, "Plugins registered: ${PluginManager.registeredPlugins.value.size} plugins")

        // Initialize ThemeEngine
        ThemeEngine.init(applicationContext)
        Log.d(TAG, "ThemeEngine initialized")

        // Initialize billing
        val billingManager = AppContainer.getBillingManager()
        val licenseManager = AppContainer.getLicenseManager()
        billingManager.initialize()
        licenseManager.validateLicense()
        Log.d(TAG, "Billing initialized")

        // Wire feature gate into PluginManager
        val featureGateManager = AppContainer.getFeatureGateManager()
        PluginManager.featureGateProvider = { featureGateManager.isPro.value }

        // Initialize TTS Manager without auto-loading (loading controlled by settings)
        TTSManager.init(applicationContext, autoLoad = false)
        Log.d(TAG, "TTSManager initialized")

        val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        // Run data integrity check after vault is ready
        appScope.launch {
            try {
                // Wait for vault to be ready (15s timeout)
                val vaultReady = VaultHelper.awaitReady(15_000)
                if (!vaultReady) {
                    Log.w(TAG, "Vault not ready after 15s, running integrity check without chat validation")
                }

                val db = AppContainer.getDatabase()
                val ragRepository = RagRepository(
                    ragDao = db.ragDao(),
                    context = applicationContext
                )
                val manager = DataIntegrityManager(
                    context = applicationContext,
                    modelDao = db.modelDao(),
                    personaDao = db.personaDao(),
                    ragDao = db.ragDao(),
                    aiMemoryDao = db.aiMemoryDao(),
                    ragRepository = ragRepository,
                    appSettings = AppSettingsDataStore(applicationContext)
                )
                val report = manager.runFullCheck()
                Log.i(TAG, "Integrity check: ${report.totalFixes} fixes applied")
            } catch (e: Exception) {
                Log.e(TAG, "Data integrity check failed", e)
            }
        }

        // Conditionally load TTS model based on user setting
        appScope.launch {
            try {
                val settings = AppSettingsDataStore(applicationContext)
                val loadOnStart = settings.loadTTSOnStart.first()
                if (loadOnStart) {
                    val modelDir = TTSManager.getModelDirectory()
                    if (modelDir != null) {
                        val success = TTSManager.loadModel(modelDir)
                        Log.d(TAG, "TTS model auto-loaded on start: $success")
                    }
                } else {
                    Log.d(TAG, "TTS auto-load disabled by user setting")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error checking TTS auto-load setting", e)
            }
        }

        // Schedule background memory workers
        scheduleMemoryWorkers()

        // Note: Service binding moved to MainActivity to comply with Android 14+ foreground service restrictions
    }

    private fun scheduleMemoryWorkers() {
        val idleChargingConstraints = Constraints.Builder()
            .setRequiresCharging(true)
            .setRequiresDeviceIdle(true)
            .build()

        val workManager = WorkManager.getInstance(applicationContext)

        // L2: Memory summary consolidation — every 6 hours when idle + charging
        workManager.enqueueUniquePeriodicWork(
            MemorySummaryWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<MemorySummaryWorker>(6, TimeUnit.HOURS)
                .setConstraints(idleChargingConstraints)
                .build()
        )

        // L3: Knowledge graph builder — every 12 hours when idle + charging
        workManager.enqueueUniquePeriodicWork(
            KnowledgeGraphBuilder.WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<KnowledgeGraphBuilder>(12, TimeUnit.HOURS)
                .setConstraints(idleChargingConstraints)
                .build()
        )

        Log.d(TAG, "Memory workers scheduled (summary: 6h, knowledge graph: 12h)")
    }

    override fun onTerminate() {
        super.onTerminate()
        Log.d(TAG, "Application onTerminate")
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        // Don't unbind on configuration changes
        if (level == TRIM_MEMORY_UI_HIDDEN) {
            Log.d(TAG, "UI hidden, keeping service bound")
        }
    }
}