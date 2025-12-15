package com.mp.ai_engine.databases.sherpa_tts

import androidx.room.TypeConverter
import com.mp.ai_engine.models.llm_models.Voices
import kotlinx.serialization.json.Json

class TTSTypeConverter {

    private val json = Json { ignoreUnknownKeys = true }

    @TypeConverter
    fun fromVoicesList(value: List<Voices>): String {
        return json.encodeToString(value)
    }

    @TypeConverter
    fun toVoicesList(value: String): List<Voices> {
        return json.decodeFromString(value)
    }

}