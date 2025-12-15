package com.mp.ai_engine.databases.sherpa_stt

import androidx.room.Database
import androidx.room.RoomDatabase
import com.mp.ai_engine.models.llm_models.SherpaSTTDatabaseModel

@Database(
    entities = [SherpaSTTDatabaseModel::class], version = 1, exportSchema = false
)
abstract class SherpaSTTDatabase : RoomDatabase() {
    abstract fun SherpaSTTDatabaseAccessObject(): SherpaSTTDatabaseAccessObject
}