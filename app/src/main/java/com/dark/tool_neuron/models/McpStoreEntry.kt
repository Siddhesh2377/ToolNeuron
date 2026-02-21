package com.dark.tool_neuron.models

import kotlinx.serialization.Serializable

/**
 * Represents an MCP server entry in the remote registry (MCP Store).
 * Users browse these entries and install them as local McpServer configurations.
 */
@Serializable
data class McpStoreEntry(
    val id: String,
    val name: String,
    val description: String,
    val url: String,
    val transportType: String = "SSE",
    val category: String = "general",
    val requiresApiKey: Boolean = false,
    val requiresTermux: Boolean = false,
    val pipPackage: String? = null,
    val setupCommand: String? = null,
    val defaultPort: Int? = null,
    val author: String = "",
    val tags: List<String> = emptyList(),
    val iconName: String? = null,
    val setupInstructions: String? = null
)

/**
 * Categories for MCP Store entries
 */
object McpStoreCategories {
    const val ALL = "All"
    const val SEARCH = "Search"
    const val CODE = "Code"
    const val DATA = "Data"
    const val FILES = "Files"
    const val AI = "AI"
    const val UTILITIES = "Utilities"
    const val LOCAL = "Local (Termux)"

    val all = listOf(ALL, SEARCH, CODE, DATA, FILES, AI, UTILITIES, LOCAL)
}
