package com.dark.ai_module.model

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.ForeignKey.Companion.CASCADE
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation


fun getDefaultModelData() = ModelsData(
    0, "", "", 0, "", "", "", "", "", 0
)

@Entity(
    tableName = "model_props",
    foreignKeys = [ForeignKey(
        entity = ModelsData::class,
        parentColumns = ["id"],
        childColumns = ["modelId"],
        onDelete = CASCADE
    )],
    indices = [Index("modelId")]
)
data class ModelProps(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val modelId: Int,
    val chatTemplate: String,
    val systemPrompt: String,
    val temperature: Float = 1.0f,
    val topK: Int = 40,
    val topP: Float = 0.9f,
    val minP: Int = 2048
)

data class ModelWithProps(
    @Embedded val model: ModelsData,
    @Relation(
        parentColumn = "id",
        entityColumn = "modelId"
    )
    val props: ModelProps?
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

data class ManagerDefaults(
    val systemPrompt: String = "You are a helpful assistant.",
    val contextLength: Int = 8_024,
)

data class ParamsBundle(
    val professional: ModelParams.Professional = ModelParams.Professional(),
    val emotional: ModelParams.Emotional = ModelParams.Emotional(),
)

object ModelParams {
    data class Professional(val value: Float = 3.5f)
    data class Emotional(val value: Float = 7.6f)
}

sealed class LoadState {
    object Idle : LoadState()
    data class Loading(val progress: Float) : LoadState()
    data class OnLoaded(val model: ModelsData) : LoadState()
    data class Error(val message: String) : LoadState()
}

data class ModelInitParams(
    val threads: Int = (Runtime.getRuntime().availableProcessors().coerceAtLeast(2)) / 2,
    val gpuLayers: Int = 0,
    val useMMAP: Boolean = true,
    val useMLOCK: Boolean = false,
    val ctxSize: Int = 4_048,
    val temp: Float = 0.7f,
    val topK: Int = 20,
    val topP: Float = 0.5f,
    val minP: Float = 0.0f,
    val systemPrompt: String = "You are a helpful assistant.",
    val chatTemplate: String? = null,
)

data class GenerationParams(val maxTokens: Int = 2048)