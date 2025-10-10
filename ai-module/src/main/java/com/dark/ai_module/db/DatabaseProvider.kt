package com.dark.ai_module.db

import android.content.Context
import androidx.room.Room

object DatabaseProvider {
    @Volatile
    private var INSTANCE: AppDatabase? = null

    fun getDatabase(context: Context): AppDatabase {
        return INSTANCE ?: synchronized(this) {
            Room.databaseBuilder(
                context.applicationContext, AppDatabase::class.java, "mode_db"
            ).fallbackToDestructiveMigration(false)
                .build().also { INSTANCE = it }
        }
    }
}