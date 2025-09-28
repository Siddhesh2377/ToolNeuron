package com.mp.data_hub_lib.model

import kotlinx.serialization.Serializable

@Serializable
data class BrainRoot(val docs: List<BrainDoc>)

@Serializable
data class BrainDoc(
    val id: String,
    val text: String,
    val embedding: List<Float>
)

@Serializable
data class Doc(
    val text: String,
    val similarity: Double
)