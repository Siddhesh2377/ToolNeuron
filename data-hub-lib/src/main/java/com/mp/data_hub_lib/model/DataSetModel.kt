package com.mp.data_hub_lib.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "data_hub_models")
data class DataSetModel(
    @PrimaryKey
    val modelName: String,
    val modelDescription: String,
    val modelPath: String,
    val modelAuthor: String,
    val modelCreated: String,
    val documentCount: Int = 0  // Track number of documents
)
