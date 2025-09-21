package com.mp.data_hub_lib

import android.content.Context
import com.mp.data_hub_lib.model.DataSetManifest
import kotlinx.serialization.json.Json
import net.lingala.zip4j.ZipFile
import org.json.JSONObject
import java.io.File

class DataNativeLib {
    // Native methods
    external fun loadVecx(path: String, key: String): Boolean
    external fun updateEntity(entity: String, key: String, value: String): Boolean
    external fun getEntity(entity: String): String
    external fun saveVecx(path: String): Boolean

    companion object {
        init {
            try {
                System.loadLibrary("data_hub_lib")
                println("Native library loaded successfully.")
            } catch (e: Exception) {
                println("Failed to load native library: ${e.message}")
            }
        }
    }

    fun loadManifest(): DataSetManifest? {
        val manifestJson = getEntity("m")
        val data = JSONObject(manifestJson)
        return Json.decodeFromString(DataSetManifest.serializer(), data.toString())
    }

}
