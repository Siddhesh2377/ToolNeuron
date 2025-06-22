package com.dark.neuroverse.services.accessibility

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.annotation.SuppressLint
import android.util.Log
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import com.dark.plugin_api.info.services.types.ScreenReading
import com.dark.plugin_runtime.engine.PluginManager
import com.dark.plugin_runtime.model.ScreenReadingServicePlugins
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

@SuppressLint("AccessibilityPolicy")
class NeuroVAccessibilityService : AccessibilityService() {

    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var currentServiceList: List<ScreenReadingServicePlugins> = emptyList()

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType == AccessibilityEvent.TYPE_VIEW_CLICKED) {
            Log.d("MyService", "View clicked")
        }

        serviceScope.launch {
            PluginManager.serviceBasedPluginsScreenReading.collect { plugins ->
                Log.d("MyService", "Loaded services: $plugins")
                currentServiceList = plugins
            }
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.d("MyService", "Accessibility service connected")
        this.serviceInfo = this.serviceInfo.apply {
            flags = flags or AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS
        }

        PluginManager.init(applicationContext)

        PluginManager.loadPluginScreenReadingServices()
        // Collect services:
        serviceScope.launch {
            PluginManager.serviceBasedPluginsScreenReading.collect { plugins ->
                Log.d("MyService", "Loaded services: $plugins")
                currentServiceList = plugins
            }
        }
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        Log.d("MyService", "Key event received: ${event.scanCode}, keyCode=${event.keyCode}")

        if (event.scanCode == 250 && event.action == KeyEvent.ACTION_DOWN) {
            Log.d("MyService", "🔥 Essential Button Pressed! Assistant Launched")
            return true
        }

        currentServiceList.forEach { service ->
            if (service.service is ScreenReading) (service.service as ScreenReading).onKeyEvent(event)
            Log.d("MyService", "Key event sent to service: ${service.service.getServiceType()}")
        }

        return super.onKeyEvent(event)
    }

    override fun onInterrupt() {

    }

    override fun onDestroy() {
        super.onDestroy()
        currentServiceList.forEach {
            it.service.onDestroy()
        }
        serviceScope.cancel()
    }
}