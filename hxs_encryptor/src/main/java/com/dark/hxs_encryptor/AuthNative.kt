package com.dark.hxs_encryptor

object AuthNative {

    data class SetupResult(val salt: ByteArray, val hash: ByteArray) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is SetupResult) return false
            return salt.contentEquals(other.salt) && hash.contentEquals(other.hash)
        }
        override fun hashCode(): Int = salt.contentHashCode() * 31 + hash.contentHashCode()
    }

    fun setup(pin: ByteArray): SetupResult {
        val r = nativeSetup(pin)
            ?: throw SecurityException("Auth setup failed")
        return SetupResult(salt = r[0], hash = r[1])
    }

    fun verify(pin: ByteArray, salt: ByteArray, storedHash: ByteArray): ByteArray? =
        nativeVerify(pin, salt, storedHash)

    fun invalidate() = nativeInvalidate()

    @JvmStatic private external fun nativeSetup(pin: ByteArray): Array<ByteArray>?
    @JvmStatic private external fun nativeVerify(pin: ByteArray, salt: ByteArray, storedHash: ByteArray): ByteArray?
    @JvmStatic private external fun nativeInvalidate()

    init {
        System.loadLibrary("hxs_encryptor")
    }
}
