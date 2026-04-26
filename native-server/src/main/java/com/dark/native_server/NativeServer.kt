package com.dark.native_server

enum class BindMode {
    LOOPBACK_ONLY,
    WIFI_ONLY,
    ALL_INTERFACES,
}

object NativeServer {

    init {
        System.loadLibrary("native_server")
    }

    external fun nativeStart(host: String, port: Int): Boolean
    external fun nativeStop()
    external fun nativeIsRunning(): Boolean
    external fun nativeBoundPort(): Int

    external fun nativeGenerateToken(): String
    external fun nativeSetToken(token: String)
    external fun nativeClearToken()
    external fun nativeHasToken(): Boolean

    external fun nativeSetModelsCatalog(dataArrayJson: String)
    external fun nativeClearModelsCatalog()

    external fun nativeAttachBridge(bridge: InferenceBridge)
    external fun nativeDetachBridge()

    external fun nativeFeedToken(genId: Long, token: String)
    external fun nativeFeedDone(genId: Long, finishReason: String)
    external fun nativeFeedError(genId: Long, message: String)

    external fun nativeRecentRequestsJson(max: Int): String
    external fun nativeClearAuditLog()

    external fun nativeConfigureRateLimit(
        capacity: Double,
        refillRps: Double,
        authFailThreshold: Int,
        banDurationMs: Long,
    )
    external fun nativeResetRateLimit()

    external fun nativeSetWebUiHtml(html: String)
    external fun nativeClearWebUi()

    external fun nativeSetDocsHtml(html: String)
    external fun nativeClearDocs()
}
