package com.dark.neuroverse.services.accessibility

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.annotation.SuppressLint
import android.util.Log
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

@SuppressLint("AccessibilityPolicy")
class NeuroVAccessibilityService : AccessibilityService() {

    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType == AccessibilityEvent.TYPE_VIEW_CLICKED) {
            Log.d("MyService", "View clicked")
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        this.serviceInfo = this.serviceInfo.apply {
            flags = flags or AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS
        }

    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        Log.d("MyService", "Key event received: ${event.scanCode}, keyCode=${event.keyCode}")
        if (event.scanCode == 250 && event.action == KeyEvent.ACTION_DOWN) {
            Log.d("MyService", "🔥 Essential Button Pressed! Assistant Launched")
            return true
        }
        return super.onKeyEvent(event)
    }

    override fun onInterrupt() {

    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }
}