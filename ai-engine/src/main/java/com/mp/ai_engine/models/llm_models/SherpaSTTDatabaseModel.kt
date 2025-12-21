package com.mp.ai_engine.models.llm_models

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID


@Entity(tableName = "sherpa_stt_models")
data class SherpaSTTDatabaseModel(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),

    val modelName: String,
    val modelDescription: String = "",
    val modelFileSize: String = "",
    val modelDir: String,

    val encoder: String = "",
    val decoder: String = "",
    val tokens: String = "",

    val createdAt: Long = System.currentTimeMillis(),
    val lastUsedAt: Long = System.currentTimeMillis()
)