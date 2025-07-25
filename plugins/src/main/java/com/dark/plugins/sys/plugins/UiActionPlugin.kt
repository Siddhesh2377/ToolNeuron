package com.dark.plugins.sys.plugins

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import android.os.Message
import android.os.Messenger
import android.util.Log
import com.dark.plugins.engine.PluginApi
import com.dark.plugins.engine.PluginInfo
import com.dark.plugins.sys.uiAction.CommandBridgeService
import org.json.JSONObject

class UiActionPlugin(private val context: Context) : PluginApi(context) {

    private var messenger: Messenger? = null
    private var pendingJson: String? = null


    private val conn = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            messenger = Messenger(binder)
            Log.d("UiActionPlugin", "Bound to CommandBridgeService")

            pendingJson?.let { jsonString ->
                val msg = Message.obtain()
                val bundle = Bundle()
                bundle.putString("command", jsonString)
                msg.data = bundle
                messenger?.send(msg)
                Log.d("UiActionPlugin", "Sent command: $jsonString")
                pendingJson = null
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            messenger = null
        }
    }

    override fun onCreate(data: Any) {
        super.onCreate(data)
        Log.d("UiActionPlugin", "onCreate")

        val intent = Intent(context, CommandBridgeService::class.java)
        context.bindService(intent, conn, Context.BIND_AUTO_CREATE)

        // Send JSON once service is connected
        if (data is JSONObject) {
            pendingJson = data.toString()
        }
    }


    override fun onDestroy() {
        context.unbindService(conn)
        super.onDestroy()
    }

    override fun getPluginInfo(): PluginInfo {
        return PluginInfo("UiActionPlugin", "Handles UI tool calls via accessibility bridge")
    }
}