package com.dark.plugins.sys.uiAction

import android.app.Service
import android.content.Intent
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Messenger
import android.util.Log
import org.json.JSONObject

class CommandBridgeService : Service() {

    private val handler = Handler(Looper.getMainLooper()) { msg ->
        val jsonStr = msg.data.getString("command") ?: return@Handler false
        Log.d("CommandBridgeService", "Received command: $jsonStr")
        val json = JSONObject(jsonStr)
        UiCommandQueue.enqueue(json)
        true
    }

    private val messenger = Messenger(handler)

    override fun onBind(intent: Intent?): IBinder {
        return messenger.binder
    }
}