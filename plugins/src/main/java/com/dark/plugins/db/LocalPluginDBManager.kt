package com.dark.plugins.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.dark.plugins.model.PluginLocalDB
import com.dark.plugins.model.PluginTypeConverters


@Database(entities = [PluginLocalDB::class], version = 1, exportSchema = true)
@TypeConverters(PluginTypeConverters::class)
abstract class LocalPluginDBManager : RoomDatabase() {
    abstract fun getPluginLocalDBDao(): PluginLocalDBDao

    companion object {
        @Volatile
        private var INSTANCE: LocalPluginDBManager? = null

        fun getInstance(context: Context): LocalPluginDBManager {
            return INSTANCE ?: synchronized(this) {
                Room.databaseBuilder(
                    context.applicationContext, LocalPluginDBManager::class.java, "local_plugin_db"
                ).build()
            }
        }
    }
}