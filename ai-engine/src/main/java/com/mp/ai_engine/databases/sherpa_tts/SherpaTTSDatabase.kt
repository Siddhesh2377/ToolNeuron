package com.mp.ai_engine.databases.sherpa_tts

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.mp.ai_engine.models.llm_models.SherpaTTSDatabaseModel

@Database(
    entities = [SherpaTTSDatabaseModel::class], version = 1, exportSchema = false
)
@TypeConverters(TTSTypeConverter::class)
abstract class SherpaTTSDatabase : RoomDatabase() {
    abstract fun SherpaTTSDatabaseAccessObject(): SherpaTTSDatabaseAccessObject
}