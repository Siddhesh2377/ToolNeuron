package com.dark.tool_neuron.repo

import android.content.Context
import com.dark.hxs.HexStorage
import com.dark.hxs.HxsRecord
import com.dark.tool_neuron.plugin.PluginPrefs
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PluginPrefsRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val storage = HexStorage()
    private val _all = MutableStateFlow<Map<String, PluginPrefs>>(emptyMap())
    val all: StateFlow<Map<String, PluginPrefs>> = _all.asStateFlow()

    init {
        val dir = File(context.filesDir, "plugin_store")
        dir.mkdirs()
        val path = dir.absolutePath
        if (storage.exists(path)) storage.openPlaintext(path)
        else storage.createPlaintext(path)
        storage.ensureCollection(COL)
        storage.addIndex(COL, TAG_PLUGIN_ID, HexStorage.WIRE_BYTES)
        refresh()
    }

    private fun refresh() {
        _all.value = storage.getAll(COL)
            .map { it.toPrefs() }
            .associateBy { it.pluginId }
    }

    fun isEnabled(pluginId: String, default: Boolean = false): Boolean =
        _all.value[pluginId]?.enabled ?: default

    fun enabledIds(): Set<String> =
        _all.value.values.filter { it.enabled }.map { it.pluginId }.toSet()

    fun getConfig(pluginId: String): String =
        _all.value[pluginId]?.configJson ?: "{}"

    fun setEnabled(pluginId: String, enabled: Boolean) {
        val current = _all.value[pluginId]
            ?: PluginPrefs(pluginId = pluginId, enabled = enabled)
        upsert(current.copy(enabled = enabled))
    }

    fun setConfig(pluginId: String, json: String) {
        val current = _all.value[pluginId]
            ?: PluginPrefs(pluginId = pluginId, enabled = false)
        upsert(current.copy(configJson = json))
    }

    private fun upsert(prefs: PluginPrefs) {
        val existing = storage.queryString(COL, TAG_PLUGIN_ID, prefs.pluginId)
        existing.forEach { storage.delete(COL, it.id) }
        storage.put(COL, prefs.toRecord())
        storage.flush(COL)
        refresh()
    }

    private companion object {
        const val COL = "plugin_prefs"
        const val TAG_PLUGIN_ID = 1
        const val TAG_ENABLED = 2
        const val TAG_CONFIG_JSON = 3
    }

    private fun PluginPrefs.toRecord(): HxsRecord = HxsRecord.build {
        putString(TAG_PLUGIN_ID, pluginId)
        putBool(TAG_ENABLED, enabled)
        putString(TAG_CONFIG_JSON, configJson)
    }

    private fun HxsRecord.toPrefs(): PluginPrefs = PluginPrefs(
        pluginId = getString(TAG_PLUGIN_ID),
        enabled = getBool(TAG_ENABLED),
        configJson = getString(TAG_CONFIG_JSON).ifEmpty { "{}" },
    )
}
