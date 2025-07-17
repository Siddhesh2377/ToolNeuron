package com.dark.neuroverse.data

import android.util.Log
import com.chaquo.python.Python
import java.io.File

object DocReader {
    fun read(path: File): String {
        val py = Python.getInstance()
        val reader = py.getModule("universal_reader")
        return try {
            val result = reader.callAttr("read_file", path.absolutePath.trim()).toString()
            Log.d("DocReader", result)
            result
        } catch (e: Exception) {
            Log.e("DocReader", "Failed to read file: ${e.message}", e)
            "Failed to read file: ${e.message}"
        }
    }
}
