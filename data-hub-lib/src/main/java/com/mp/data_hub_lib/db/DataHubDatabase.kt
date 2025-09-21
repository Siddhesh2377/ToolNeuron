package com.mp.data_hub_lib.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.mp.data_hub_lib.model.DataSetModel

@Database(entities = [DataSetModel::class], version = 1)
abstract class DataHubDatabase : RoomDatabase() {
    abstract fun dataHubDAO(): DataHubDAO
}
