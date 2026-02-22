package com.memoryvault.core

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Log
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class EncryptionManager(private val keyAlias: String) {
    private val keyStore: KeyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }

    companion object{
        private const val TAG = "EncryptionManager"
        private const val GCM_TAG_LENGTH = 128
    }

    init {
        ensureMasterKey()
    }

    private fun ensureMasterKey() {
        if (!keyStore.containsAlias(keyAlias)) {
            val keyGenerator = KeyGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_AES,
                "AndroidKeyStore"
            )

            val spec = KeyGenParameterSpec.Builder(
                keyAlias,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setRandomizedEncryptionRequired(true)
                .build()

            keyGenerator.init(spec)
            keyGenerator.generateKey()
        }
    }

    private fun getMasterKey(): SecretKey {
        return keyStore.getKey(keyAlias, null) as SecretKey
    }

    fun encrypt(data: ByteArray): EncryptedData {
        val startTime = System.currentTimeMillis()
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, getMasterKey())

        val iv = cipher.iv
        val encrypted = cipher.doFinal(data)

        val duration = System.currentTimeMillis() - startTime
        //Log.d(TAG, "Encrypted ${data.size} bytes → ${encrypted.size} bytes (${duration}ms)")

        return EncryptedData(iv, encrypted)
    }

    fun decrypt(encryptedData: EncryptedData): ByteArray {
        val startTime = System.currentTimeMillis()
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val spec = GCMParameterSpec(GCM_TAG_LENGTH, encryptedData.iv)
        cipher.init(Cipher.DECRYPT_MODE, getMasterKey(), spec)

        val decrypted = cipher.doFinal(encryptedData.data)

        val duration = System.currentTimeMillis() - startTime
        Log.d(TAG, "Decrypted ${encryptedData.data.size} bytes → ${decrypted.size} bytes (${duration}ms)")

        return decrypted
    }
}

data class EncryptedData(
    val iv: ByteArray,
    val data: ByteArray
) {
    fun toBytes(): ByteArray {
        return iv + data
    }

    companion object {
        fun fromBytes(bytes: ByteArray, ivLength: Int = 12): EncryptedData {
            require(bytes.size > ivLength) { "Invalid encrypted data size" }
            val iv = bytes.copyOf(ivLength)
            val data = bytes.copyOfRange(ivLength, bytes.size)
            return EncryptedData(iv, data)
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as EncryptedData
        if (!iv.contentEquals(other.iv)) return false
        if (!data.contentEquals(other.data)) return false
        return true
    }

    override fun hashCode(): Int {
        var result = iv.contentHashCode()
        result = 31 * result + data.contentHashCode()
        return result
    }
}