package com.dark.plugins.model

import androidx.room.Entity
import androidx.room.PrimaryKey


/**
 * Local DB row describing an installed plugin.
 */
@Entity(tableName = "plugins")
data class InstalledPlugin(
    @PrimaryKey
    val pluginName: String,           // unique, used as primary key
    val pluginDescription: String = "",
    val manifestCode: String = "",    // raw Manifest.json
    val pluginPath: String = "",      // absolute file path to the zip
    val mainClass: String = "",
    val pluginVersion: String = "",    // optional, if present in manifest (not enforced)
    val tools: List<Tools> = emptyList(),
    val shaCode: String,
)