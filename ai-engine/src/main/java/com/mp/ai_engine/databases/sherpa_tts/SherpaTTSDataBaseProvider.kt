package com.mp.ai_engine.databases.sherpa_tts

import android.content.Context
import androidx.room.Room

object SherpaTTSDataBaseProvider {

    @Volatile
    private var opInstance: SherpaTTSDatabase? = null

    fun getDatabase(context: Context): SherpaTTSDatabase {
        return opInstance ?: synchronized(this) {
            Room.databaseBuilder(
                context.applicationContext, SherpaTTSDatabase::class.java, "sherpa_tts_models"
            ).fallbackToDestructiveMigration(false)
                .build().also { opInstance = it }
        }
    }
}