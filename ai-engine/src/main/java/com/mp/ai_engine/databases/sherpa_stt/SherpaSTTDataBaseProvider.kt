package com.mp.ai_engine.databases.sherpa_stt

import android.content.Context
import androidx.room.Room

object SherpaSTTDataBaseProvider {

    @Volatile
    private var opInstance: SherpaSTTDatabase? = null

    fun getDatabase(context: Context): SherpaSTTDatabase {
        return opInstance ?: synchronized(this) {
            Room.databaseBuilder(
                context.applicationContext, SherpaSTTDatabase::class.java, "sherpa_stt_models"
            ).fallbackToDestructiveMigration(false)
                .build().also { opInstance = it }
        }
    }
}