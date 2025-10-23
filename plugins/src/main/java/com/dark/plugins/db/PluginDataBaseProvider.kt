package com.dark.plugins.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.dark.plugins.model.InstalledPlugin
import com.dark.plugins.model.PluginTypeConverters


@Database(entities = [InstalledPlugin::class], version = 1, exportSchema = true)
@TypeConverters(PluginTypeConverters::class)
abstract class PluginDataBaseProvider : RoomDatabase() {
    abstract fun getInstalledPluginDao(): PluginLocalDBDao

    companion object {
        @Volatile
        private var INSTANCE: PluginDataBaseProvider? = null

        fun getDatabase(context: Context): PluginDataBaseProvider {
            return INSTANCE ?: synchronized(this) {
                Room.databaseBuilder(
                    context.applicationContext, PluginDataBaseProvider::class.java, "local_plugin_db"
                ).build()
            }
        }
    }
}