package com.dark.tool_neuron.model

import com.dark.tool_neuron.model.enums.PathType
import com.dark.tool_neuron.model.enums.ProviderType

data class ModelInfo(
    val id: String,
    val name: String,
    val path: String,
    val pathType: PathType,
    val providerType: ProviderType = ProviderType.GGUF,
    val fileSize: Long = 0,
    val isActive: Boolean = false,
)

data class ModelConfig(
    val id: String,
    val modelId: String,
    val loadingParamsJson: String = "{}",
    val inferenceParamsJson: String = "{}",
)
