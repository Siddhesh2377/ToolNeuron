package com.mp.updatemanager

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.ContextCompat

class UpdateActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Log.d("UpdateActionReceiver", "Broadcast received!")

        if (intent.action == "com.mp.ACTION_DOWNLOAD_UPDATE") {
            val version = intent.getStringExtra("version")
            val serviceIntent = Intent(context, ApkDownloadService::class.java).apply {
                putExtra("version", version)
            }
            ContextCompat.startForegroundService(context, serviceIntent)
        }
    }

}

