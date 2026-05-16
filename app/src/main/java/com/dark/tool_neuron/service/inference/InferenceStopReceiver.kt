package com.dark.tool_neuron.service.inference

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class InferenceStopReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != ACTION_STOP) return
        InferenceClient.requestUserStop(context)
    }

    companion object {
        const val ACTION_STOP = "com.dark.tool_neuron.inference.NOTIFICATION_STOP"
    }
}
