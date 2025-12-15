package com.mp.ai_engine.models.llm_models

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

import kotlinx.serialization.Serializable

@Serializable
data class Voices(
    val id: Int = 0,
    val name: String = "",
    val gender: String = "",
    val tone: String = ""
)

@Entity(tableName = "sherpa_tts_models")
data class SherpaTTSDatabaseModel(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),

    val modelName: String,
    val modelDescription: String = "",
    val modelFileSize: String = "",
    val modelDir: String,

    val modelFileName: String = "",
    val voicesFileName: String = "",
    val dataDirName: String = "",

    val voices: List<Voices> = listOf(),

    val sampleRate: Int = 22050,
    val speedControl: Float = 1.0f,

    val createdAt: Long = System.currentTimeMillis(),
    val lastUsedAt: Long = System.currentTimeMillis()
)