package com.mp.ai_engine.databases.diffusion_model

import androidx.room.Database
import androidx.room.RoomDatabase
import com.mp.ai_engine.models.image_models.DiffusionDatabaseModel
import com.mp.ai_engine.models.llm_models.GGUFDatabaseModel

@Database(
    entities = [DiffusionDatabaseModel::class], version = 1, exportSchema = false
)
abstract class DiffusionModelDatabase : RoomDatabase() {
    abstract fun DiffusionModelDatabaseAccessObject(): DiffusionModelDatabaseAccessObject
}