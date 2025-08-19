package com.dark.ai_module.model

import androidx.room.Entity
import androidx.room.PrimaryKey


fun getDefaultModelData() = ModelsData(
    0, "", "", 0, "", "", "", "", "", 0
)


@Entity(tableName = "local_models")
data class ModelsData(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val modeName: String = "",
    val modelDescription: String = "",
    val modelCtxSize: Int = 0,
    val toolUse: String = "",
    val modelLink: String = "",
    val modelPageLink: String = "",
    val modelPath: String = "",
    val chatTemplate: String = "",
    val modelSize: Int = 0
)