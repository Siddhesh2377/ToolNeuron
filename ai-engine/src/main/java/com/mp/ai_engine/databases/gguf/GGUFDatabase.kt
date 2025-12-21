package com.mp.ai_engine.databases.gguf

import androidx.room.Database
import androidx.room.RoomDatabase
import com.mp.ai_engine.models.llm_models.GGUFDatabaseModel

@Database(
    entities = [GGUFDatabaseModel::class], version = 1, exportSchema = false
)
abstract class GGUFDatabase : RoomDatabase() {
    abstract fun GGUFDatabaseAccessObject(): GGUFDatabaseAccessObject
}