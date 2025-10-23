package com.mp.data_hub_lib.model

import kotlinx.serialization.Serializable

@Serializable
data class RagResult(
    val docs: List<Doc>,
    val stats: GenerationStats
)