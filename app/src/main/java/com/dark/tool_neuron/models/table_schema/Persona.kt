package com.dark.tool_neuron.models.table_schema

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "personas")
data class Persona(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),

    @ColumnInfo(name = "name")
    val name: String,

    @ColumnInfo(name = "avatar")
    val avatar: String = "",

    @ColumnInfo(name = "system_prompt")
    val systemPrompt: String = "",

    @ColumnInfo(name = "greeting")
    val greeting: String = "",

    @ColumnInfo(name = "is_default")
    val isDefault: Boolean = false,

    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis(),

    // Character card fields
    @ColumnInfo(name = "description")
    val description: String = "",

    @ColumnInfo(name = "personality")
    val personality: String = "",

    @ColumnInfo(name = "scenario")
    val scenario: String = "",

    @ColumnInfo(name = "example_messages")
    val exampleMessages: String = "",

    @ColumnInfo(name = "alternate_greetings")
    val alternateGreetings: List<String> = emptyList(),

    @ColumnInfo(name = "tags")
    val tags: List<String> = emptyList(),

    @ColumnInfo(name = "avatar_uri")
    val avatarUri: String? = null,

    @ColumnInfo(name = "creator_notes")
    val creatorNotes: String = "",

    // Persona Engine: per-character sampling profile (JSON)
    @ColumnInfo(name = "sampling_profile")
    val samplingProfile: String = "",

    // Persona Engine: control vectors (JSON array of {path, strength})
    @ColumnInfo(name = "control_vectors")
    val controlVectors: String = ""
)
