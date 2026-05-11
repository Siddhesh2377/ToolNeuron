package com.dark.hxs_encryptor

object PolicyEngine {

    enum class Feature(val id: Int) {
        APP_LAUNCH(0),
        OPEN_VAULT(1),
        AUTH_SETUP(2),
        AUTH_VERIFY(3),
        AUTH_DISABLE(4),
        READ_MODEL(5),
        WRITE_MODEL(6),
        READ_CHAT(7),
        WRITE_CHAT(8),
        READ_RAG(9),
        WRITE_RAG(10),
        INFERENCE(11),
        UI_SETTINGS(12),
        UI_PASSWORD_SCREEN(13),
        UI_SETUP_SCREEN(14),
        UI_INTRO(15),
        UI_HOME(16),
        UI_MODEL_STORE(17),
        UI_MODEL_MANAGER(18),
        UI_DEV_NOTES(19),
        UI_GUIDE(20),
        PRO_UNLIMITED_CONTEXT(1000),
        PRO_ADVANCED_RAG(1001),
        PRO_EXPORT_CHATS(1002),
    }

    fun isAllowed(feature: Feature, sessionToken: ByteArray? = null): Boolean =
        nativeIsAllowed(feature.id, sessionToken)

    fun requireAllowed(feature: Feature, sessionToken: ByteArray? = null) {
        if (!nativeIsAllowed(feature.id, sessionToken)) {
            throw SecurityException("Feature $feature denied by policy")
        }
    }

    fun setPassthrough(passthrough: Boolean) = nativeSetPassthrough(passthrough)

    fun markTampered() = nativeMarkTampered()

    fun isTampered(): Boolean = nativeIsTampered()

    fun hasSession(): Boolean = nativeHasSession()

    fun invalidateSession() = nativeInvalidateSession()

    fun resetForTesting() = nativeResetForTesting()

    @JvmStatic private external fun nativeIsAllowed(featureId: Int, token: ByteArray?): Boolean
    @JvmStatic private external fun nativeSetPassthrough(passthrough: Boolean)
    @JvmStatic private external fun nativeMarkTampered()
    @JvmStatic private external fun nativeIsTampered(): Boolean
    @JvmStatic private external fun nativeHasSession(): Boolean
    @JvmStatic private external fun nativeInvalidateSession()
    @JvmStatic private external fun nativeResetForTesting()

    init {
        System.loadLibrary("hxs_encryptor")
    }
}
