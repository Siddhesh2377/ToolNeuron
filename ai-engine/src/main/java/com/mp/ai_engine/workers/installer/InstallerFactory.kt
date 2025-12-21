package com.mp.ai_engine.workers.installer

import com.mp.ai_engine.models.llm_models.CloudModel
import com.mp.ai_engine.workers.installer.internal_workers.DiffusionModelInstaller
import com.mp.ai_engine.workers.installer.internal_workers.GGUFModelInstaller
import com.mp.ai_engine.workers.installer.internal_workers.OpenRouterModelInstaller
import com.mp.ai_engine.workers.installer.internal_workers.SherpaSTTModelInstaller
import com.mp.ai_engine.workers.installer.internal_workers.SherpaTTSModelInstaller

/**
 * Factory to create appropriate installer based on CloudModel
 */
object InstallerFactory {

    private val installers: List<SuperInstaller> by lazy {
        listOf(
            GGUFModelInstaller(),
            SherpaTTSModelInstaller(),
            SherpaSTTModelInstaller(),
            OpenRouterModelInstaller(),
            DiffusionModelInstaller()
        )
    }

    /**
     * Returns the appropriate installer for the given CloudModel
     */
    fun getInstaller(cloudModel: CloudModel): SuperInstaller? {
        return installers.firstOrNull { it.canHandle(cloudModel) }
    }

    /**
     * Checks if an installer exists for the given model type
     */
    fun hasInstaller(cloudModel: CloudModel): Boolean {
        return installers.any { it.canHandle(cloudModel) }
    }
}