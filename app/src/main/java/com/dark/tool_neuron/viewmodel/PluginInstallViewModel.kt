package com.dark.tool_neuron.viewmodel

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.dark.plugin_exc.InstalledPlugin
import com.dark.plugin_exc.PluginExecutor
import com.dark.plugin_exc.ui.PluginContainerActivity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class PluginInstallViewModel @Inject constructor(
    application: Application,
    private val executor: PluginExecutor,
) : AndroidViewModel(application) {

    val installed: StateFlow<List<InstalledPlugin>> =
        executor.registry.installed.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = executor.registry.installed.value,
        )

    val activePlugin: StateFlow<String?> = executor.activePlugin
    val openPlugins: StateFlow<List<String>> = executor.openPlugins

    private val _installState = MutableStateFlow<InstallState>(InstallState.Idle)
    val installState: StateFlow<InstallState> = _installState

    fun installFromUri(uri: Uri) {
        viewModelScope.launch {
            _installState.value = InstallState.Working
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    val app = getApplication<Application>()
                    val stream = app.contentResolver.openInputStream(uri)
                        ?: throw IllegalStateException("could not open ${uri}")
                    stream.use { executor.install(it) }
                }
            }
            _installState.value = result.fold(
                onSuccess = { InstallState.Success(it.manifest.id, it.manifest.name) },
                onFailure = { InstallState.Failed(it.message ?: it::class.java.simpleName) },
            )
        }
    }

    fun openPlugin(pluginId: String) {
        val app = getApplication<Application>()
        executor.open(pluginId)
        app.startActivity(
            PluginContainerActivity.launchIntent(app, pluginId)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    fun uninstall(pluginId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            executor.uninstall(pluginId)
        }
    }

    fun stopPlugin(pluginId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            executor.close(pluginId)
        }
    }

    fun dismissInstallState() {
        _installState.value = InstallState.Idle
    }

    sealed interface InstallState {
        data object Idle : InstallState
        data object Working : InstallState
        data class Success(val pluginId: String, val name: String) : InstallState
        data class Failed(val reason: String) : InstallState
    }
}
