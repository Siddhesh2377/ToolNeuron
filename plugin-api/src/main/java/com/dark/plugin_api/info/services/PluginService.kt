package com.dark.plugin_api.info.services

import android.content.Context
import com.dark.plugin_api.info.services.types.ServiceType

open class PluginService(protected val context: Context) {

    open fun getServiceType() : ServiceType {
        return ServiceType.NONE
    }

    open fun onConnected(){

    }

    open fun execute() {

    }

    open fun onDestroy(){

    }
}