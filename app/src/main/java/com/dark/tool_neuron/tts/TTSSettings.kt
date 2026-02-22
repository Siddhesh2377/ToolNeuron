package com.dark.tool_neuron.tts

import kotlinx.serialization.Serializable

@Serializable
data class TTSSettings(
    val voice: String = "F1",
    val speed: Float = 1.05f,
    val steps: Int = 2,
    val language: String = "en",
    val autoSpeak: Boolean = false,
    val useNNAPI: Boolean = false
)
