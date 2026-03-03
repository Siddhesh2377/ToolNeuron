package com.dark.system_encryptor

/**
 * Native encryption engine for protecting user data.
 * Uses BoringSSL: AES-256-GCM for encryption, HKDF-SHA256 for key derivation.
 */
class SystemEncryptor {

    external fun nativeEncrypt(plaintext: ByteArray, key: ByteArray): ByteArray?
    external fun nativeDecrypt(sealedData: ByteArray, key: ByteArray): ByteArray?
    external fun nativeDeriveKey(masterKey: ByteArray, context: String): ByteArray?
    external fun nativeRandomBytes(size: Int): ByteArray?
    external fun nativeSecureWipe(data: ByteArray)
    private external fun nativePbkdf2(
        password: ByteArray, salt: ByteArray, iterations: Int, keyLength: Int
    ): ByteArray?

    fun encryptData(plaintext: ByteArray, key: ByteArray): ByteArray {
        return nativeEncrypt(plaintext, key)
            ?: throw SecurityException("Encryption failed")
    }

    fun decryptData(sealedData: ByteArray, key: ByteArray): ByteArray {
        return nativeDecrypt(sealedData, key)
            ?: throw SecurityException("Decryption failed: invalid key or tampered data")
    }

    fun deriveKey(masterKey: ByteArray, context: String): ByteArray {
        return nativeDeriveKey(masterKey, context)
            ?: throw SecurityException("Key derivation failed")
    }

    fun randomBytes(size: Int): ByteArray {
        return nativeRandomBytes(size)
            ?: throw SecurityException("Random bytes generation failed")
    }

    fun pbkdf2(password: ByteArray, salt: ByteArray, iterations: Int = 600_000, keyLength: Int = 32): ByteArray =
        nativePbkdf2(password, salt, iterations, keyLength)
            ?: throw SecurityException("PBKDF2 derivation failed")

    fun secureWipe(data: ByteArray) {
        nativeSecureWipe(data)
    }

    companion object {
        init {
            System.loadLibrary("system_encryptor")
        }
    }
}
