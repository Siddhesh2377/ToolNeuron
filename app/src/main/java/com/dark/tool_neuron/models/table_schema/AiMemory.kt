package com.dark.tool_neuron.models.table_schema

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

enum class MemoryCategory {
    PERSONAL,
    PREFERENCE,
    WORK,
    INTEREST,
    GENERAL
}

@Entity(
    tableName = "ai_memories",
    indices = [
        Index(value = ["category"]),
        Index(value = ["persona_id"])
    ]
)
data class AiMemory(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),

    @ColumnInfo(name = "fact")
    val fact: String,

    @ColumnInfo(name = "category")
    val category: MemoryCategory = MemoryCategory.GENERAL,

    @ColumnInfo(name = "source_chat_id")
    val sourceChatId: String? = null,

    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "updated_at")
    val updatedAt: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "last_accessed_at")
    val lastAccessedAt: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "access_count")
    val accessCount: Int = 0,

    @ColumnInfo(name = "embedding", typeAffinity = ColumnInfo.BLOB)
    val embedding: ByteArray? = null,

    @ColumnInfo(name = "is_summarized")
    val isSummarized: Boolean = false,

    @ColumnInfo(name = "summary_group_id")
    val summaryGroupId: String? = null,

    @ColumnInfo(name = "persona_id")
    val personaId: String? = null
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is AiMemory) return false
        return id == other.id
    }

    override fun hashCode(): Int = id.hashCode()
}
