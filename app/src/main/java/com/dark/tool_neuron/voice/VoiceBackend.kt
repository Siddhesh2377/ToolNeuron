package com.dark.tool_neuron.voice

enum class VoiceBackend(val key: String, val label: String) {
    AUTO("auto", "Auto"),
    OFFLINE("offline", "Offline model"),
    ANDROID_SYSTEM("android_system", "Android system"),
    ;

    companion object {
        fun fromKey(key: String): VoiceBackend =
            entries.firstOrNull { it.key == key } ?: AUTO
    }
}
