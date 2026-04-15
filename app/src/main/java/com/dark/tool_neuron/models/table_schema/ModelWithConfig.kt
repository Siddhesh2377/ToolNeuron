package com.dark.tool_neuron.models.table_schema

import com.dark.tool_neuron.models.engine_schema.GgufEngineSchema

fun GgufEngineSchema.toModelConfig(modelId: String): ModelConfig {
    return ModelConfig(
        modelId = modelId,
        modelLoadingParams = toLoadingJson(),
        modelInferenceParams = toInferenceJson()
    )
}