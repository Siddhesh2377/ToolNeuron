package com.dark.userdata.ntds

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

fun encrypt(data: ByteArray, key: SecretKey): ByteArray {
    val cipher = Cipher.getInstance("AES/GCM/NoPadding")
    cipher.init(Cipher.ENCRYPT_MODE, key)

    val iv = cipher.iv               // Let Android generate IV
    val encrypted = cipher.doFinal(data)

    return iv + encrypted            // Still prepend IV for storage
}

fun decrypt(encrypted: ByteArray, key: SecretKey): ByteArray {
    val iv = encrypted.copyOfRange(0, 12)
    val cipher = Cipher.getInstance("AES/GCM/NoPadding")
    cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, iv))
    return cipher.doFinal(encrypted.copyOfRange(12, encrypted.size))
}

/**
 * Creates or retrieves an AES-256 GCM key stored securely in AndroidKeyStore.
 * Key is non-exportable, ideal for encrypting .brain files.
 */
fun getOrCreateHardwareBackedAesKey(
    alias: String = "brain_key",
    requireStrongBox: Boolean = false
): SecretKey {
    val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
    ks.getKey(alias, null)?.let { return it as SecretKey }

    val spec = KeyGenParameterSpec.Builder(
        alias,
        KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
    )
        .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
        .setIsStrongBoxBacked(requireStrongBox)
        .build()

    val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
    generator.init(spec)
    return generator.generateKey()
}