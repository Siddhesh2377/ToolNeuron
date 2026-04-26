package com.dark.native_server

abstract class InferenceBridge {

    abstract fun startGeneration(genId: Long, messagesJson: String, paramsJson: String): Boolean

    abstract fun cancelGeneration(genId: Long)

    open fun onRequestEvent(eventJson: String) {}
}
