package com.dark.ai_manager.ai.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "local_models")
data class ModelsData(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val modeName: String,
    val modelDescription: String,
    val modelLink: String,
    val modelPageLink: String,
    val modelPath: String
)