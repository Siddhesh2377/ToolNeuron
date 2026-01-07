package com.memoryvault

import org.json.JSONObject

sealed class VaultItem {
    abstract val id: String
    abstract val timestamp: Long
    abstract val category: String?
    abstract val tags: Set<String>
}

data class MessageItem(
    override val id: String,
    override val timestamp: Long,
    override val category: String?,
    override val tags: Set<String>,
    val content: String
) : VaultItem()

data class FileItem(
    override val id: String,
    override val timestamp: Long,
    override val category: String?,
    override val tags: Set<String>,
    val fileName: String,
    val mimeType: String,
    val size: Long,
    val data: ByteArray
) : VaultItem() {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as FileItem
        if (id != other.id) return false
        if (timestamp != other.timestamp) return false
        if (category != other.category) return false
        if (tags != other.tags) return false
        if (fileName != other.fileName) return false
        if (mimeType != other.mimeType) return false
        if (size != other.size) return false
        if (!data.contentEquals(other.data)) return false
        return true
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + timestamp.hashCode()
        result = 31 * result + (category?.hashCode() ?: 0)
        result = 31 * result + tags.hashCode()
        result = 31 * result + fileName.hashCode()
        result = 31 * result + mimeType.hashCode()
        result = 31 * result + size.hashCode()
        result = 31 * result + data.contentHashCode()
        return result
    }
}

data class CustomDataItem(
    override val id: String,
    override val timestamp: Long,
    override val category: String?,
    override val tags: Set<String>,
    val dataType: String,
    val data: JSONObject
) : VaultItem()

data class EmbeddingItem(
    override val id: String,
    override val timestamp: Long,
    override val category: String?,
    override val tags: Set<String>,
    val vector: FloatArray,
    val linkedContentId: String,
    val modelName: String
) : VaultItem() {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as EmbeddingItem
        if (id != other.id) return false
        if (timestamp != other.timestamp) return false
        if (category != other.category) return false
        if (tags != other.tags) return false
        if (!vector.contentEquals(other.vector)) return false
        if (linkedContentId != other.linkedContentId) return false
        if (modelName != other.modelName) return false
        return true
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + timestamp.hashCode()
        result = 31 * result + (category?.hashCode() ?: 0)
        result = 31 * result + tags.hashCode()
        result = 31 * result + vector.contentHashCode()
        result = 31 * result + linkedContentId.hashCode()
        result = 31 * result + modelName.hashCode()
        return result
    }
}

data class ScoredVaultItem(
    val item: VaultItem,
    val score: Float
)