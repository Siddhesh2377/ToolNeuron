package com.dark.tool_neuron.service.server

import com.dark.native_server.BindMode

data class ServerInfo(
    val host: String,
    val displayHost: String,
    val lanHost: String?,
    val port: Int,
    val bindMode: BindMode,
    val wifiActive: Boolean,
)

sealed interface ServerState {
    data object Stopped : ServerState
    data class LoadingModel(val modelId: String, val modelName: String) : ServerState
    data object Starting : ServerState
    data class Running(val info: ServerInfo, val modelId: String, val modelName: String) : ServerState
    data class Failed(val reason: String) : ServerState
}

data class ServerRequestEvent(
    val timestampMs: Long,
    val method: String,
    val path: String,
    val status: Int,
    val durationMs: Long,
    val client: String,
)
