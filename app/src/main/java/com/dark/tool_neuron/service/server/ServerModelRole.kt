package com.dark.tool_neuron.service.server

enum class ServerModelRole(val token: String) {
    AUTO("auto"),
    DISABLED("disabled"),
    CHAT("gguf"),
    VLM("vlm"),
    EMBEDDING("embedding"),
    TTS("tts"),
    STT("stt"),
    IMAGE_GEN("image_gen"),
    IMAGE_UPSCALER("image_upscaler"),
    ;

    companion object {
        fun fromToken(token: String?): ServerModelRole =
            entries.firstOrNull { it.token == token } ?: AUTO
    }
}
