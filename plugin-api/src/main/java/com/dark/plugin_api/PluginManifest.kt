package com.dark.plugin_api

import kotlinx.serialization.Serializable

@Serializable
data class PluginManifest(
    val id: String,
    val version: String,
    val apiVersion: Int,
    val name: String,
    val description: String,
    val author: String,
    val entryClass: String,
    val initial: String,
    val capabilities: List<PluginCapability> = emptyList(),
    val hasNativeCode: Boolean = false,
)
