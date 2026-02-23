package com.dark.tool_neuron.models.table_schema

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

enum class EntityType {
    PERSON,
    PLACE,
    TOPIC,
    EVENT,
    PREFERENCE,
    THING
}

@Entity(
    tableName = "knowledge_entities",
    indices = [
        Index(value = ["name"]),
        Index(value = ["type"])
    ]
)
data class KnowledgeEntity(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),

    @ColumnInfo(name = "name")
    val name: String,

    @ColumnInfo(name = "type")
    val type: EntityType,

    @ColumnInfo(name = "embedding", typeAffinity = ColumnInfo.BLOB)
    val embedding: ByteArray? = null,

    @ColumnInfo(name = "first_seen")
    val firstSeen: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "last_seen")
    val lastSeen: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "mention_count")
    val mentionCount: Int = 1
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is KnowledgeEntity) return false
        return id == other.id
    }

    override fun hashCode(): Int = id.hashCode()
}
