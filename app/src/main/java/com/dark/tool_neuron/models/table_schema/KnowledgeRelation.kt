package com.dark.tool_neuron.models.table_schema

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(
    tableName = "knowledge_relations",
    foreignKeys = [
        ForeignKey(
            entity = KnowledgeEntity::class,
            parentColumns = ["id"],
            childColumns = ["subject_id"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = KnowledgeEntity::class,
            parentColumns = ["id"],
            childColumns = ["object_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["subject_id"]),
        Index(value = ["object_id"]),
        Index(value = ["predicate"]),
        Index(value = ["persona_id"])
    ]
)
data class KnowledgeRelation(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),

    @ColumnInfo(name = "subject_id")
    val subjectId: String,

    @ColumnInfo(name = "predicate")
    val predicate: String,

    @ColumnInfo(name = "object_id")
    val objectId: String,

    @ColumnInfo(name = "confidence")
    val confidence: Float = 1.0f,

    @ColumnInfo(name = "source_fact_id")
    val sourceFactId: String? = null,

    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "persona_id")
    val personaId: String? = null
)
