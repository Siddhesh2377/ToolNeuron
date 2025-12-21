package com.mp.ai_engine.databases.gguf

import android.content.Context
import androidx.room.Room

object GGUFDataBaseProvider {

    @Volatile
    private var ggufInstance: GGUFDatabase? = null

    fun getDatabase(context: Context): GGUFDatabase {
        return ggufInstance ?: synchronized(this) {
            Room.databaseBuilder(
                context.applicationContext, GGUFDatabase::class.java, "gguf_db"
            ).fallbackToDestructiveMigration(false)
                .build().also { ggufInstance = it }
        }
    }
}