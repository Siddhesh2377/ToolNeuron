package com.mp.n_apps.data

import kotlinx.serialization.Serializable

@Serializable
data class AgentConfig(
    val id: String,
    val name: String,
    val providerUrl: String,
    val modelName: String,
    val apiKey: String,
    val isActive: Boolean = false
)

@Serializable
data class AgentConfigList(
    val agents: List<AgentConfig> = emptyList()
)
