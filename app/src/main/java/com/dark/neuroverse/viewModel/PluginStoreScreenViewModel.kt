package com.dark.neuroverse.viewModel

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import android.widget.Toast
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dark.ai_module.workers.downloadFile
import com.dark.neuroverse.model.OnlinePlugin
import com.dark.plugins.manager.PluginManager
import com.dark.plugins.model.InstalledPlugin
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import java.security.MessageDigest

data class OnlinePluginUiState(
    val onlinePlugin: OnlinePlugin,
    val progress: Float = 0f,
    val isDownloading: Boolean = false,
    val isInstalled: Boolean = false
)

class PluginStoreScreenViewModel : ViewModel() {

    val installedPlugins: StateFlow<List<InstalledPlugin>> =
        PluginManager.installedPlugins.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            emptyList()
        )

    // Online plugin store state
    private val _onlinePlugins = MutableStateFlow<List<OnlinePluginUiState>>(emptyList())
    val onlinePlugins = _onlinePlugins.asStateFlow()

    private val db = FirebaseFirestore.getInstance()

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

        // Update online plugin state if it's from the store
        _onlinePlugins.value = _onlinePlugins.value.map { state ->
            if (state.onlinePlugin.name == pluginName) {
                state.copy(isInstalled = false, progress = 0f, isDownloading = false)
            } else state
        }
    }

    // ========== Online Plugin Store Methods ==========

    fun loadOnlinePlugins() {
        viewModelScope.launch {
            db.collection("plugin-packs")
                .get()
                .addOnSuccessListener { result ->
                    val plugins = result.documents.mapNotNull { doc ->
                        doc.toObject(OnlinePlugin::class.java)
                    }.map { plugin ->
                        OnlinePluginUiState(
                            onlinePlugin = plugin,
                            isInstalled = isPluginInstalled(plugin.sha)
                        )
                    }
                    _onlinePlugins.value = plugins
                }
                .addOnFailureListener { e ->
                    Log.w("FirestoreDB", "Error fetching online plugins", e)
                }
        }
    }

    private fun isPluginInstalled(shaCode: String): Boolean {
        return installedPlugins.value.any { it.shaCode == shaCode }
    }

    fun getFileSha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(8192)
            var bytesRead: Int
            while (input.read(buffer).also { bytesRead = it } != -1) {
                digest.update(buffer, 0, bytesRead)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    fun installOnlinePlugin(plugin: OnlinePlugin, context: Context) {
        updateOnlinePluginState(plugin) { it.copy(isDownloading = true, progress = 0f) }

        val pluginsDir = File(context.filesDir, "plugins")
        pluginsDir.mkdirs()

        val fileName = "${plugin.name.replace(" ", "-")}.${plugin.fileExtension ?: "zip"}"
        val file = File(pluginsDir, fileName)

        viewModelScope.launch {
            downloadFile(
                fileUrl = plugin.downloadLink,
                outputFile = file,
                onProgress = { progress ->
                    updateOnlinePluginState(plugin) { it.copy(progress = progress) }
                },
                onComplete = {
                    installDownloadedPlugin(context, file, plugin)
                },
                onError = { e ->
                    Log.e("PluginDL", "Download failed", e)
                    updateOnlinePluginState(plugin) { it.copy(isDownloading = false, progress = 0f) }

                    // Clean up failed download
                    if (file.exists()) file.delete()
                }
            )
        }
    }

    private fun installDownloadedPlugin(context: Context, file: File, plugin: OnlinePlugin) {
        try {
            // Use existing installation logic
            PluginManager.installFromPath(context, file.absolutePath)

            updateOnlinePluginState(plugin) {
                it.copy(
                    isDownloading = false,
                    isInstalled = true,
                    progress = 1f
                )
            }

            // Optionally clean up the downloaded file after installation
            // file.delete()
        } catch (e: Exception) {
            Log.e("PluginInstall", "Installation failed", e)
            updateOnlinePluginState(plugin) {
                it.copy(
                    isDownloading = false,
                    isInstalled = false,
                    progress = 0f
                )
            }

            // Clean up failed installation
            if (file.exists()) file.delete()
        }
    }

    fun deleteOnlinePlugin(plugin: OnlinePlugin, context: Context) {
        // Uninstall using existing method
        uninstallPlugin(plugin.name)

        // Clean up downloaded file
        val pluginsDir = File(context.filesDir, "plugins")
        val fileName = "${plugin.name.replace(" ", "-")}.${plugin.fileExtension ?: "zip"}"
        val file = File(pluginsDir, fileName)

        if (file.exists()) {
            file.delete()
        }

        Toast.makeText(context, "Plugin '${plugin.name}' uninstalled", Toast.LENGTH_SHORT).show()
    }

    private fun updateOnlinePluginState(
        plugin: OnlinePlugin,
        update: (OnlinePluginUiState) -> OnlinePluginUiState
    ) {
        _onlinePlugins.value = _onlinePlugins.value.map {
            if (it.onlinePlugin.name == plugin.name) update(it) else it
        }
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

    override fun onCleared() {
        super.onCleared()
        viewModelScope.cancel()
    }
}