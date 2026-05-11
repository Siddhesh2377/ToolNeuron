package com.dark.plugin_api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class PluginCapability {
    @SerialName("hxs.read")         HXS_READ,
    @SerialName("hxs.write")        HXS_WRITE,
    @SerialName("internet")         INTERNET,
    @SerialName("ai.onnx")          AI_ONNX,
    @SerialName("camera")           CAMERA,
    @SerialName("mic")              MIC,
    @SerialName("filesystem.read")  FILESYSTEM_READ,
    @SerialName("filesystem.write") FILESYSTEM_WRITE,
    @SerialName("notifications")    NOTIFICATIONS,
    @SerialName("clipboard")        CLIPBOARD,
}
