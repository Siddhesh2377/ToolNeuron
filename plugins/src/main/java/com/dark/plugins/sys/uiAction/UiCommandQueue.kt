package com.dark.plugins.sys.uiAction

import android.util.Log
import org.json.JSONObject

object UiCommandQueue {
    private val _queue = mutableListOf<JSONObject>()

    @Synchronized
    fun enqueue(command: JSONObject) {
        _queue.add(command)
        Log.d("UiCommandQueue", "Enqueued: $command")
    }

    @Synchronized
    fun poll(): JSONObject? = if (_queue.isNotEmpty()) _queue.removeAt(0) else null

    @Synchronized
    fun peek(): JSONObject? = _queue.firstOrNull()

    @Synchronized
    fun hasCommand(): Boolean = _queue.isNotEmpty()
}
