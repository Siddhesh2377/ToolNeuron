package com.dark.tool_neuron.models.table_schema

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

enum class RagSourceType {
    TEXT,
    CHAT,
    FILE,
    MEDICAL_TEXT,
    NEURON_PACKET,
    MEMORY_VAULT
}

enum class RagStatus {
    INSTALLED,
    LOADED,
    LOADING,
    ERROR
}

@Entity(tableName = "installed_rags")
data class InstalledRag(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),

    @ColumnInfo(name = "name")
    val name: String,

    @ColumnInfo(name = "description")
    val description: String = "",

    @ColumnInfo(name = "source_type")
    val sourceType: RagSourceType,

    @ColumnInfo(name = "file_path")
    val filePath: String? = null,

    @ColumnInfo(name = "node_count")
    val nodeCount: Int = 0,

    @ColumnInfo(name = "embedding_dimension")
    val embeddingDimension: Int = 0,

    @ColumnInfo(name = "embedding_model")
    val embeddingModel: String = "",

    @ColumnInfo(name = "domain")
    val domain: String = "general",

    @ColumnInfo(name = "language")
    val language: String = "en",

    @ColumnInfo(name = "version")
    val version: String = "1.0",

    @ColumnInfo(name = "tags")
    val tags: String = "", // Comma-separated tags

    @ColumnInfo(name = "status")
    val status: RagStatus = RagStatus.INSTALLED,

    @ColumnInfo(name = "is_enabled")
    val isEnabled: Boolean = true,

    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "updated_at")
    val updatedAt: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "last_loaded_at")
    val lastLoadedAt: Long? = null,

    @ColumnInfo(name = "size_bytes")
    val sizeBytes: Long = 0,

    @ColumnInfo(name = "metadata_json")
    val metadataJson: String? = null,

    @ColumnInfo(name = "is_encrypted")
    val isEncrypted: Boolean = false,

    @ColumnInfo(name = "loading_mode")
    val loadingMode: Int = 1,

    @ColumnInfo(name = "has_admin_access")
    val hasAdminAccess: Boolean = false
) {
    fun getTagsList(): List<String> = tags.split(",").filter { it.isNotBlank() }.map { it.trim() }

    fun getFormattedSize(): String {
        return when {
            sizeBytes < 1024 -> "$sizeBytes B"
            sizeBytes < 1024 * 1024 -> "${sizeBytes / 1024} KB"
            else -> "${sizeBytes / (1024 * 1024)} MB"
        }
    }

}