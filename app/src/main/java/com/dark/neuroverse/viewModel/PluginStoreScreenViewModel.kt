package com.dark.neuroverse.viewModel

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewModelScope
import com.dark.plugins.manager.PluginManager
import com.dark.plugins.model.InstalledPlugin
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import java.io.File

class PluginStoreScreenViewModel : ViewModel() {

    val installedPlugins: StateFlow<List<InstalledPlugin>> =
        PluginManager.installedPlugins.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            emptyList()
        )

    fun installFromUri(context: Context, uri: Uri) {
        val fileName = uri.getDisplayName(context)
            ?: "plugin-${System.currentTimeMillis()}.zip"
        val cacheFile = File(context.cacheDir, fileName)

        context.contentResolver.openInputStream(uri)?.use { input ->
            cacheFile.outputStream().use { output ->
                input.copyTo(output)
            }
        }

        PluginManager.installFromPath(context, cacheFile.absolutePath)
    }

    fun uninstallPlugin(pluginName: String) {
        PluginManager.uninstall(pluginName)
    }

    // Helper Extensions
    private fun Uri.getDisplayName(context: Context): String? {
        return context.contentResolver.query(
            this,
            arrayOf(OpenableColumns.DISPLAY_NAME),
            null, null, null
        )?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (nameIndex >= 0 && cursor.moveToFirst()) {
                cursor.getString(nameIndex)
            } else null
        }
    }
}