package com.dark.plugin_api.info.services.types

import android.content.Context
import android.view.KeyEvent
import com.dark.plugin_api.info.services.PluginService

open class ScreenReading(context: Context) : PluginService(context) {
    override fun getServiceType(): ServiceType {
        return ServiceType.SCREEN_READING
    }


    open fun onKeyEvent(event: KeyEvent) {

    }

    override fun onDestroy() {

    }
}