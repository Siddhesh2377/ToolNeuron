package com.dark.hxs_encryptor

class HxsEncryptor {


    fun encrypt(plaintext: ByteArray, key: ByteArray, aad: ByteArray? = null): ByteArray =
        nativeEncrypt(plaintext, key, aad)
            ?: throw SecurityException("Encryption failed")

    fun decrypt(sealedData: ByteArray, key: ByteArray, aad: ByteArray? = null): ByteArray =
        nativeDecrypt(sealedData, key, aad)
            ?: throw SecurityException("Decryption failed: invalid key or tampered data")


    fun deriveKey(ikm: ByteArray, salt: ByteArray? = null, info: String): ByteArray =
        nativeDeriveKey(ikm, salt, info)
            ?: throw SecurityException("HKDF key derivation failed")

    fun argon2id(
        password: ByteArray,
        salt: ByteArray,
        tCost: Int = 3,
        mCost: Int = 65536,
        parallelism: Int = 4,
        hashLen: Int = 32,
    ): ByteArray =
        nativeArgon2id(password, salt, tCost, mCost, parallelism, hashLen)
            ?: throw SecurityException("Argon2id derivation failed")

    fun pbkdf2(
        password: ByteArray,
        salt: ByteArray,
        iterations: Int = 600_000,
        keyLength: Int = 32,
    ): ByteArray =
        nativePbkdf2(password, salt, iterations, keyLength)
            ?: throw SecurityException("PBKDF2 derivation failed")


    fun randomBytes(size: Int): ByteArray =
        nativeRandomBytes(size)
            ?: throw SecurityException("Random bytes generation failed")

    fun sha256(data: ByteArray): ByteArray =
        nativeSha256(data)
            ?: throw SecurityException("SHA-256 failed")

    fun secureWipe(data: ByteArray) = nativeSecureWipe(data)

    fun detectOptimalCipher(): Int = nativeDetectCipher()


    fun isDebuggerAttached(): Boolean = nativeIsDebuggerAttached()
    fun blockDebugger(): Boolean = nativeBlockDebugger()
    fun isFridaPresent(): Boolean = nativeIsFridaPresent()
    fun isXposedPresent(): Boolean = nativeIsXposedPresent()

    fun verifyApkSignature(expectedHash: ByteArray, actualHash: ByteArray): Boolean =
        nativeVerifyApkSignature(expectedHash, actualHash)

    fun hashFile(path: String): ByteArray? = nativeHashFile(path)


    data class KeyPair(val publicKey: ByteArray, val secretKey: ByteArray)
    data class EncapsResult(val ciphertext: ByteArray, val sharedSecret: ByteArray)

    fun hybridKemKeygen(): KeyPair {
        val result = nativeHybridKemKeygen()
            ?: throw SecurityException("Hybrid KEM keygen failed")
        return KeyPair(result[0], result[1])
    }

    fun hybridKemEncaps(publicKey: ByteArray): EncapsResult {
        val result = nativeHybridKemEncaps(publicKey)
            ?: throw SecurityException("Hybrid KEM encapsulation failed")
        return EncapsResult(result[0], result[1])
    }

    fun hybridKemDecaps(ciphertext: ByteArray, secretKey: ByteArray): ByteArray =
        nativeHybridKemDecaps(ciphertext, secretKey)
            ?: throw SecurityException("Hybrid KEM decapsulation failed")


    fun hybridSignKeygen(): KeyPair {
        val result = nativeHybridSignKeygen()
            ?: throw SecurityException("Hybrid signature keygen failed")
        return KeyPair(result[0], result[1])
    }

    fun hybridSign(message: ByteArray, secretKey: ByteArray): ByteArray =
        nativeHybridSign(message, secretKey)
            ?: throw SecurityException("Hybrid signing failed")

    fun hybridVerify(message: ByteArray, signature: ByteArray, publicKey: ByteArray): Boolean =
        nativeHybridVerify(message, signature, publicKey)


    private external fun nativeEncrypt(plaintext: ByteArray, key: ByteArray, aad: ByteArray?): ByteArray?
    private external fun nativeDecrypt(sealedData: ByteArray, key: ByteArray, aad: ByteArray?): ByteArray?
    private external fun nativeDeriveKey(ikm: ByteArray, salt: ByteArray?, info: String): ByteArray?
    private external fun nativeArgon2id(password: ByteArray, salt: ByteArray, tCost: Int, mCost: Int, parallelism: Int, hashLen: Int): ByteArray?
    private external fun nativePbkdf2(password: ByteArray, salt: ByteArray, iterations: Int, keyLength: Int): ByteArray?
    private external fun nativeRandomBytes(size: Int): ByteArray?
    private external fun nativeSha256(data: ByteArray): ByteArray?
    private external fun nativeSecureWipe(data: ByteArray)
    private external fun nativeDetectCipher(): Int

    private external fun nativeIsDebuggerAttached(): Boolean
    private external fun nativeBlockDebugger(): Boolean
    private external fun nativeIsFridaPresent(): Boolean
    private external fun nativeIsXposedPresent(): Boolean
    private external fun nativeVerifyApkSignature(expected: ByteArray, actual: ByteArray): Boolean
    private external fun nativeHashFile(path: String): ByteArray?

    private external fun nativeHybridKemKeygen(): Array<ByteArray>?
    private external fun nativeHybridKemEncaps(publicKey: ByteArray): Array<ByteArray>?
    private external fun nativeHybridKemDecaps(ciphertext: ByteArray, secretKey: ByteArray): ByteArray?

    private external fun nativeHybridSignKeygen(): Array<ByteArray>?
    private external fun nativeHybridSign(message: ByteArray, secretKey: ByteArray): ByteArray?
    private external fun nativeHybridVerify(message: ByteArray, signature: ByteArray, publicKey: ByteArray): Boolean

    companion object {
        init {
            System.loadLibrary("hxs_encryptor")
        }
    }
}
