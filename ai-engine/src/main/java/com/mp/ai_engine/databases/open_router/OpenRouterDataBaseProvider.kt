package com.mp.ai_engine.databases.open_router

import android.content.Context
import androidx.room.Room

object OpenRouterDataBaseProvider {

    @Volatile
    private var opInstance: OpenRouterDatabase? = null

    fun getDatabase(context: Context): OpenRouterDatabase {
        return opInstance ?: synchronized(this) {
            Room.databaseBuilder(
                context.applicationContext, OpenRouterDatabase::class.java, "open_router_db"
            ).fallbackToDestructiveMigration(false)
                .build().also { opInstance = it }
        }
    }
}