package com.dark.neuroverse.data

//import android.util.Log
//import com.chaquo.python.Python
//import com.dark.neuroverse.model.DOC
//import java.io.File
//
//object DocReader {
//    fun read(path: File): DOC {
//        val py = Python.getInstance()
//        val reader = py.getModule("universal_reader")
//        return try {
//            val result = reader.callAttr("read_file", path.absolutePath.trim()).toString()
//            Log.d("DocReader", result)
//            Log.d("DocReader", result.length.toString())
//            DOC(path.absolutePath, path.name, result.trim(), path.extension)
//        } catch (e: Exception) {
//            Log.e("DocReader", "Failed to read file: ${e.message}", e)
//
//            DOC(path.absolutePath, path.name, "Failed to read file: ${e.message}",path.extension)
//        }
//    }
//}
