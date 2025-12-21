package com.dark.tool_neuron.model

import com.mp.ai_engine.models.llm_models.ModelType

data class LocalModel(
    val modelId: String,
    val modelName: String,
    val modelType: ModelType
)