package com.dark.tool_neuron.viewmodel

import android.app.Application
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.dark.plugin_exc.InstalledPlugin
import com.dark.plugin_exc.PluginExecutor
import com.dark.plugin_exc.catalog.CatalogEntry
import com.dark.plugin_exc.catalog.PluginCatalogClient
import com.dark.plugin_exc.ui.PluginContainerActivity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

@HiltViewModel
class PluginInstallViewModel @Inject constructor(
    application: Application,
    private val executor: PluginExecutor,
    private val catalogClient: PluginCatalogClient,
) : AndroidViewModel(application) {

    val installed: StateFlow<List<InstalledPlugin>> =
        executor.registry.installed.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = executor.registry.installed.value,
        )

    val activePlugin: StateFlow<String?> = executor.activePlugin
    val openPlugins: StateFlow<List<String>> = executor.openPlugins

    private val _catalog = MutableStateFlow<CatalogState>(CatalogState.Loading)
    val catalog: StateFlow<CatalogState> = _catalog

    private val _itemStates = MutableStateFlow<Map<String, ItemAction>>(emptyMap())

    val storeRows: StateFlow<List<StoreRow>> = combine(
        _catalog,
        installed,
        _itemStates,
    ) { cat, inst, actions ->
        if (cat !is CatalogState.Ready) return@combine emptyList()
        val installedById = inst.associateBy { it.manifest.id }
        cat.catalog.plugins.map { entry ->
            val install = installedById[entry.id]
            val phase = when {
                actions[entry.id] is ItemAction.Downloading -> Phase.Downloading(
                    (actions[entry.id] as ItemAction.Downloading).bytes,
                    (actions[entry.id] as ItemAction.Downloading).total,
                )
                actions[entry.id] is ItemAction.Installing -> Phase.Installing
                actions[entry.id] is ItemAction.Failed -> Phase.Failed(
                    (actions[entry.id] as ItemAction.Failed).reason
                )
                install == null -> Phase.NotInstalled
                install.manifest.version != entry.version -> Phase.UpdateAvailable(install.manifest.version)
                else -> Phase.Installed
            }
            StoreRow(entry = entry, phase = phase)
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList(),
    )

    fun refreshCatalog() {
        viewModelScope.launch {
            _catalog.value = CatalogState.Loading
            val result = runCatching {
                withContext(Dispatchers.IO) { catalogClient.fetchCatalog() }
            }
            _catalog.value = result.fold(
                onSuccess = { CatalogState.Ready(it) },
                onFailure = { CatalogState.Failed(it.message ?: it::class.java.simpleName) },
            )
        }
    }

    fun installFromCatalog(entry: CatalogEntry) {
        viewModelScope.launch {
            _itemStates.update { it + (entry.id to ItemAction.Downloading(0, entry.size)) }
            val app = getApplication<Application>()
            val tmp = File(app.cacheDir, "plugin_download_${entry.id}_${System.nanoTime()}.zip")
            val outcome = runCatching {
                withContext(Dispatchers.IO) {
                    catalogClient.downloadAndVerify(entry, tmp) { bytes, total ->
                        _itemStates.update {
                            it + (entry.id to ItemAction.Downloading(bytes, total))
                        }
                    }
                    _itemStates.update { it + (entry.id to ItemAction.Installing) }
                    tmp.inputStream().use { executor.install(it) }
                }
            }
            tmp.delete()
            outcome.fold(
                onSuccess = {
                    _itemStates.update { it - entry.id }
                },
                onFailure = { err ->
                    _itemStates.update {
                        it + (entry.id to ItemAction.Failed(err.message ?: "install failed"))
                    }
                },
            )
        }
    }

    fun dismissError(pluginId: String) {
        _itemStates.update { it - pluginId }
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

    sealed interface CatalogState {
        data object Loading : CatalogState
        data class Ready(val catalog: com.dark.plugin_exc.catalog.PluginCatalog) : CatalogState
        data class Failed(val reason: String) : CatalogState
    }

    sealed interface ItemAction {
        data class Downloading(val bytes: Long, val total: Long) : ItemAction
        data object Installing : ItemAction
        data class Failed(val reason: String) : ItemAction
    }

    sealed interface Phase {
        data object NotInstalled : Phase
        data object Installed : Phase
        data class UpdateAvailable(val fromVersion: String) : Phase
        data class Downloading(val bytes: Long, val total: Long) : Phase
        data object Installing : Phase
        data class Failed(val reason: String) : Phase
    }

    data class StoreRow(
        val entry: CatalogEntry,
        val phase: Phase,
    )
}
