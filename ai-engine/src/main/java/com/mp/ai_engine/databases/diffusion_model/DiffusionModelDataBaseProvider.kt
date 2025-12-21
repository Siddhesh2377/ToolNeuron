package com.mp.ai_engine.databases.diffusion_model

import android.content.Context
import androidx.room.Room

object DiffusionModelDataBaseProvider {

    @Volatile
    private var diffusionModelDatabaseInstance: DiffusionModelDatabase? = null

    fun getDatabase(context: Context): DiffusionModelDatabase {
        return diffusionModelDatabaseInstance ?: synchronized(this) {
            Room.databaseBuilder(
                context.applicationContext, DiffusionModelDatabase::class.java, "diffusion_db"
            ).fallbackToDestructiveMigration(false)
                .build().also { diffusionModelDatabaseInstance = it }
        }
    }
}