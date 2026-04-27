package com.dark.tool_neuron.data

import android.content.Context
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyInfo
import android.security.keystore.KeyProperties
import android.security.keystore.StrongBoxUnavailableException
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppKeyStore @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {

    private val bootstrapDir: File
    private val bootstrapFile: File
    @Volatile private var cachedDek: ByteArray? = null

    enum class Backing { STRONGBOX, TEE, SOFTWARE_FALLBACK, UNKNOWN }

    init {
        bootstrapDir = context.filesDir.resolve(BOOTSTRAP_DIR)
        bootstrapDir.mkdirs()
        bootstrapFile = bootstrapDir.resolve(BOOTSTRAP_FILE)
        migrateLegacyIfNeeded()
    }

    @Synchronized
    fun unwrapOrCreateDek(): ByteArray {
        cachedDek?.let { return it }
        val fromDisk = bootstrapFile.exists()
        val dek = if (fromDisk) {
            val blob = readBlob() ?: throw SecurityException("corrupt bootstrap blob")
            unwrap(blob.iv, blob.ct)
        } else {
            val fresh = ByteArray(DEK_LEN)
            SecureRandom().nextBytes(fresh)
            val (iv, ct) = wrap(fresh)
            writeBlob(Blob(iv, ct))
            fresh
        }
        cachedDek = dek
        return dek
    }

    fun wipe() {
        cachedDek = null
        context.filesDir.listFiles()?.forEach { it.deleteRecursively() }
        context.cacheDir.listFiles()?.forEach { it.deleteRecursively() }
        try {
            val ks = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
            if (ks.containsAlias(KEY_ALIAS)) ks.deleteEntry(KEY_ALIAS)
        } catch (_: Throwable) {
        }
    }

    fun backing(): Backing {
        return try {
            val ks = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
            val key = ks.getKey(KEY_ALIAS, null) as? SecretKey ?: return Backing.UNKNOWN
            val factory = SecretKeyFactory.getInstance(key.algorithm, ANDROID_KEYSTORE)
            val info = factory.getKeySpec(key, KeyInfo::class.java) as KeyInfo
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                when (info.securityLevel) {
                    KeyProperties.SECURITY_LEVEL_STRONGBOX -> Backing.STRONGBOX
                    KeyProperties.SECURITY_LEVEL_TRUSTED_ENVIRONMENT -> Backing.TEE
                    KeyProperties.SECURITY_LEVEL_SOFTWARE -> Backing.SOFTWARE_FALLBACK
                    else -> Backing.UNKNOWN
                }
            } else {
                @Suppress("DEPRECATION")
                if (info.isInsideSecureHardware) Backing.TEE else Backing.SOFTWARE_FALLBACK
            }
        } catch (_: Throwable) {
            Backing.UNKNOWN
        }
    }

    private data class Blob(val iv: ByteArray, val ct: ByteArray)

    private fun readBlob(): Blob? = try {
        val raw = bootstrapFile.readBytes()
        if (raw.size < HEADER_LEN) null
        else {
            val plain = xorMask(raw)
            val buf = ByteBuffer.wrap(plain).order(ByteOrder.LITTLE_ENDIAN)
            val magic = ByteArray(MAGIC.size).also { buf.get(it) }
            if (!magic.contentEquals(MAGIC)) null
            else {
                val version = buf.get()
                if (version != BLOB_VERSION) null
                else {
                    val ivLen = buf.getShort().toInt() and 0xFFFF
                    if (ivLen !in 1..64 || buf.remaining() < ivLen + 2) null
                    else {
                        val iv = ByteArray(ivLen).also { buf.get(it) }
                        val ctLen = buf.getShort().toInt() and 0xFFFF
                        if (ctLen < 1 || ctLen > 4096 || buf.remaining() < ctLen) null
                        else {
                            val ct = ByteArray(ctLen).also { buf.get(it) }
                            Blob(iv, ct)
                        }
                    }
                }
            }
        }
    } catch (_: Throwable) {
        null
    }

    private fun writeBlob(b: Blob) {
        val len = MAGIC.size + 1 + 2 + b.iv.size + 2 + b.ct.size
        val buf = ByteBuffer.allocate(len).order(ByteOrder.LITTLE_ENDIAN)
        buf.put(MAGIC)
        buf.put(BLOB_VERSION)
        buf.putShort(b.iv.size.toShort())
        buf.put(b.iv)
        buf.putShort(b.ct.size.toShort())
        buf.put(b.ct)
        val masked = xorMask(buf.array())
        val tmp = File(bootstrapDir, "$BOOTSTRAP_FILE.tmp")
        tmp.writeBytes(masked)
        if (!tmp.renameTo(bootstrapFile)) {
            bootstrapFile.writeBytes(masked)
            tmp.delete()
        }
    }

    private fun xorMask(data: ByteArray): ByteArray {
        val out = ByteArray(data.size)
        for (i in data.indices) {
            out[i] = (data[i].toInt() xor XOR_KEY[i % XOR_KEY.size].toInt()).toByte()
        }
        return out
    }

    private fun wrap(plaintext: ByteArray): Pair<ByteArray, ByteArray> {
        val key = getOrCreateKeystoreKey()
        val cipher = Cipher.getInstance(GCM_TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key)
        val iv = cipher.iv
        val ct = cipher.doFinal(plaintext)
        return iv to ct
    }

    private fun unwrap(iv: ByteArray, ct: ByteArray): ByteArray {
        val key = getOrCreateKeystoreKey()
        val cipher = Cipher.getInstance(GCM_TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
        return cipher.doFinal(ct)
    }

    private fun getOrCreateKeystoreKey(): SecretKey {
        val ks = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (ks.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }

        val kg = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        return try {
            kg.init(buildKeySpec(preferStrongBox = true))
            kg.generateKey()
        } catch (_: StrongBoxUnavailableException) {
            kg.init(buildKeySpec(preferStrongBox = false))
            kg.generateKey()
        }
    }

    private fun buildKeySpec(preferStrongBox: Boolean): KeyGenParameterSpec {
        val builder = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setRandomizedEncryptionRequired(true)
        if (preferStrongBox && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            builder.setIsStrongBoxBacked(true)
        }
        return builder.build()
    }

    private fun migrateLegacyIfNeeded() {
        if (bootstrapFile.exists()) return
        val bootstrapStale = bootstrapDir.listFiles()?.any { it.name != BOOTSTRAP_FILE } == true
        val prefsDir = context.filesDir.resolve("app_prefs")
        val prefsStale = prefsDir.exists() && (prefsDir.listFiles()?.isNotEmpty() == true)
        if (!bootstrapStale && !prefsStale) return
        bootstrapDir.listFiles()?.forEach { if (it.name != BOOTSTRAP_FILE) it.delete() }
        if (prefsDir.exists()) prefsDir.listFiles()?.forEach { it.delete() }
        try {
            val ks = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
            if (ks.containsAlias(KEY_ALIAS)) ks.deleteEntry(KEY_ALIAS)
        } catch (_: Throwable) {
        }
        Log.i(TAG, "migrated: wiped stale bootstrap + prefs, keystore alias reset")
    }

    companion object {
        private const val TAG = "AppKeyStore"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val KEY_ALIAS = "toolneuron_vault_dek_v1"
        private const val GCM_TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_TAG_BITS = 128
        private const val BOOTSTRAP_DIR = "app_bootstrap"
        private const val BOOTSTRAP_FILE = "k.bin"
        private const val DEK_LEN = 32
        private val MAGIC = byteArrayOf(0x54, 0x4E, 0x44, 0x4B)
        private const val BLOB_VERSION: Byte = 1
        private const val HEADER_LEN = 4 + 1 + 2 + 12 + 2 + 16
        private val XOR_KEY = byteArrayOf(
            0x7A.toByte(), 0xC3.toByte(), 0x19.toByte(), 0xE5.toByte(),
            0x4B.toByte(), 0xF1.toByte(), 0x8A.toByte(), 0x2D.toByte(),
            0x91.toByte(), 0x56.toByte(), 0xD7.toByte(), 0x0E.toByte(),
            0x3F.toByte(), 0xBC.toByte(), 0x62.toByte(), 0x0A.toByte(),
            0xE8.toByte(), 0x47.toByte(), 0xB1.toByte(), 0x6F.toByte(),
            0x38.toByte(), 0xD2.toByte(), 0x05.toByte(), 0x9B.toByte(),
            0xC4.toByte(), 0x7A.toByte(), 0x12.toByte(), 0xFE.toByte(),
            0x5D.toByte(), 0xA0.toByte(), 0x83.toByte(), 0x26.toByte(),
        )
    }
}
