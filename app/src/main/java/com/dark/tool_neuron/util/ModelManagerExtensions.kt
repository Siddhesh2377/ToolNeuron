package com.dark.tool_neuron.util

import android.content.Context
import com.dark.ai_module.workers.ModelManager
import com.dark.tool_neuron.data.UserPrefs
import kotlinx.coroutines.flow.first

/**
 * Extension functions to simplify OpenRouter configuration
 * Call these from your Application class or ViewModel
 */

/**
 * Initialize ModelManager with OpenRouter credentials from SharedPreferences
 *
 * Usage in Application.onCreate():
 * ```
 * ModelManager.init(this)
 * lifecycleScope.launch {
 *     ModelManager.initOpenRouterFromPrefs(this@Application)
 * }
 * ```
 */
suspend fun initOpenRouterFromPrefs(context: Context) {
    val apiKey = UserPrefs.getOpenRouterApiKey(context).first()
    val baseUrl = UserPrefs.getOpenRouterBaseUrl(context).first()

    if (apiKey.isNotBlank()) {
        ModelManager.configureOpenRouter(apiKey, baseUrl)
    }
}