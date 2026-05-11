package com.dark.plugin_exc.catalog

import com.dark.plugin_api.PluginCapability
import kotlinx.serialization.Serializable

@Serializable
data class PluginCatalog(
    val schemaVersion: Int,
    val updatedAt: String,
    val plugins: List<CatalogEntry>,
)

@Serializable
data class CatalogEntry(
    val id: String,
    val version: String,
    val apiVersion: Int,
    val name: String,
    val description: String,
    val author: String,
    val initial: String,
    val capabilities: List<PluginCapability> = emptyList(),
    val hasNativeCode: Boolean = false,
    val download: String,
    val size: Long,
    val sha256: String,
)
