package com.dark.hxs_encryptor

object BootIntegrity {

    const val FAIL_NONE: Int          = 0
    const val FAIL_DEBUGGER: Int      = 1 shl 0
    const val FAIL_FRIDA: Int         = 1 shl 1
    const val FAIL_XPOSED: Int        = 1 shl 2
    const val FAIL_PTRACE_DENIED: Int = 1 shl 3
    const val FAIL_LIB_HASH: Int      = 1 shl 4
    const val FAIL_BAD_INPUT: Int     = 1 shl 5
    const val FAIL_INLINE_HOOK: Int   = 1 shl 6

    fun installPtraceSelfTrace() = nativeInstallPtrace()

    fun scanEnvironment(): Int = nativeScanEnvironment()

    fun verify(libPaths: Array<String>, libHashes: Array<ByteArray>): Int =
        nativeBootVerify(libPaths, libHashes)

    fun hashFile(path: String): ByteArray? = nativeHashFile(path)

    fun verifyHookBaselines(): Boolean = nativeVerifyHookBaselines()

    fun hardFail(reason: Int) = nativeHardFail(reason)

    fun setRelaxedForTesting(relaxed: Boolean): Boolean = nativeSetRelaxedForTesting(relaxed)

    @JvmStatic private external fun nativeInstallPtrace()
    @JvmStatic private external fun nativeScanEnvironment(): Int
    @JvmStatic private external fun nativeBootVerify(libPaths: Array<String>, libHashes: Array<ByteArray>): Int
    @JvmStatic private external fun nativeHashFile(path: String): ByteArray?
    @JvmStatic private external fun nativeHardFail(reason: Int)
    @JvmStatic private external fun nativeSetRelaxedForTesting(relaxed: Boolean): Boolean
    @JvmStatic private external fun nativeVerifyHookBaselines(): Boolean

    init {
        System.loadLibrary("hxs_encryptor")
    }
}
