package com.dark.neuroverse.services.assistant

import android.service.voice.VoiceInteractionService
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

class NeuroVService : VoiceInteractionService(),
    CoroutineScope by CoroutineScope(SupervisorJob() + Dispatchers.Default) {

    private val serviceName = "NeuroVService"

    override fun onReady() {
        super.onReady()
        Log.d(serviceName, "Service is ready")
    }

    override fun onDestroy() {
        cancel()
        super.onDestroy()
    }

}