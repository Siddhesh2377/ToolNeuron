package com.dark.neuroverse.viewModel

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.ViewModelStoreOwner
import com.dark.plugins.manager.PluginManager
import com.dark.plugins.model.PluginLocalDB
import com.dark.plugins.model.LoadedPlugin
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.io.File

class PluginStoreScreenViewModel : ViewModel() {

    // Installed plugins -> map to your UI model
    val pluginsList: StateFlow<List<PluginLocalDB>> =
        PluginManager.installedPlugins
            .map { rows -> rows.map(::mapToPluginModel) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // Also expose raw DB rows if you prefer them directly in UI
    val installedPlugins: StateFlow<List<PluginLocalDB>> =
        PluginManager.installedPlugins
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // Running & current plugin mirrors of PluginManager
    val runningPlugins: StateFlow<List<LoadedPlugin>> =
        PluginManager.runningPlugins
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val currentPlugin: StateFlow<LoadedPlugin?> =
        PluginManager.currentPlugin
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    // ---- Commands that delegate to PluginManager ----

    fun runPlugin(context: Context, name: String, data: Any) {
        PluginManager.runPlugin(context, name, data)
    }

    fun stopPlugin(name: String) {
        PluginManager.stopPlugin(name)
    }

    fun setCurrentPluginByName(name: String) {
        PluginManager.setCurrentPluginByName(name)
    }

    fun cancelAllPlugins() {
        PluginManager.cancelAll()
    }

    fun getPluginViewModelStoreOwner(pluginName: String): ViewModelStoreOwner {
        return PluginManager.getViewModelStoreOwner(pluginName)
    }

    fun addPluginFromUri(context: Context, uri: Uri) {
        // Copy picked file to cache and ask PluginManager to install it
        val name = queryDisplayName(context, uri) ?: "plugin-${System.currentTimeMillis()}.zip"
        val out = File(context.cacheDir, name)
        context.contentResolver.openInputStream(uri).use { input ->
            out.outputStream().use { output ->
                input?.copyTo(output)
            }
        }
        // install (will place it into filesDir/plugins/<mainClass>/...)
        PluginManager.registerPlugin(out.absolutePath, context = context)
    }

    fun deletePlugin(pluginName: String): Boolean {
        return PluginManager.uninstallPlugin(pluginName)
    }

    private fun queryDisplayName(context: Context, uri: Uri): String? {
        val cursor = context.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)
        cursor?.use {
            val idx = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (idx >= 0 && it.moveToFirst()) return it.getString(idx)
        }
        return null
    }

    // ---- Helpers ----

    private fun mapToPluginModel(row: PluginLocalDB): PluginLocalDB {
        return row
    }
}
