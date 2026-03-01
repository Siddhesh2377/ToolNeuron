package com.dark.tool_neuron.billing

import android.content.Context
import android.net.Uri
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.security.KeyFactory
import java.security.KeyStore
import java.security.MessageDigest
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.Mac
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
data class LicenseInfo(
    val userId: String,
    val product: String,
    val issuedAt: Long,
    val signature: String,
    val signatureType: String = "ed25519" // "ed25519" or "hmac-sha256"
)

@Singleton
class LicenseManager @Inject constructor(
    @ApplicationContext private val context: Context
) {

    companion object {
        private const val TAG = "LicenseManager"
        private const val LICENSE_FILE_NAME = "toolneuron_pro.tnlicense"

        // Real Ed25519 public key (Base64-encoded X.509/SPKI DER).
        // The private key is NOT in this app — it lives on the signing server only.
        private const val ED25519_PUBLIC_KEY_B64 =
            "MCowBQYDK2VwAyEAzpgsf753Tf+rzHlDrvPLnhXLSEnrgGhgaqBFAKAP84s="

        // Android Keystore alias for AES-GCM encryption of license at rest
        private const val KEYSTORE_ALIAS = "toolneuron_license_key"
        private const val KEYSTORE_PROVIDER = "AndroidKeyStore"
        private const val GCM_TAG_LENGTH = 128 // bits
        private const val GCM_IV_LENGTH = 12 // bytes

        // Magic header to distinguish AES-GCM encrypted files from legacy XOR files
        private val AES_GCM_MAGIC = byteArrayOf(0x54, 0x4E, 0x41, 0x45) // "TNAE"

        private val json = Json { ignoreUnknownKeys = true }
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val _isLicenseValid = MutableStateFlow(false)
    val isLicenseValid: StateFlow<Boolean> = _isLicenseValid.asStateFlow()

    private var cachedLicenseInfo: LicenseInfo? = null

    /**
     * Import a .tnlicense file from the given URI.
     * Encrypts with AES-GCM via Android Keystore and validates.
     */
    fun importLicense(uri: Uri) {
        scope.launch {
            try {
                val bytes = withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                }

                if (bytes == null) {
                    Log.w(TAG, "Failed to read license file from URI")
                    return@launch
                }

                // Encrypt and write to internal storage
                val licenseFile = getLicenseFile()
                val encrypted = encryptForStorage(bytes)
                withContext(Dispatchers.IO) {
                    licenseFile.writeBytes(encrypted)
                }

                validateLicense()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to import license", e)
                _isLicenseValid.value = false
            }
        }
    }

    /**
     * Validate the stored license file.
     * Handles both legacy XOR-encrypted and new AES-GCM-encrypted files.
     */
    fun validateLicense() {
        scope.launch {
            try {
                val licenseFile = getLicenseFile()
                if (!licenseFile.exists()) {
                    _isLicenseValid.value = false
                    cachedLicenseInfo = null
                    return@launch
                }

                val fileBytes = withContext(Dispatchers.IO) { licenseFile.readBytes() }
                val raw = decryptFromStorage(fileBytes)
                if (raw == null) {
                    Log.w(TAG, "Failed to decrypt license file")
                    _isLicenseValid.value = false
                    cachedLicenseInfo = null
                    return@launch
                }

                val licenseJson = raw.decodeToString()
                val info = json.decodeFromString<LicenseInfo>(licenseJson)

                // Verify product field
                if (info.product != "pro") {
                    _isLicenseValid.value = false
                    cachedLicenseInfo = null
                    return@launch
                }

                // Build the message that was signed: "userId|product|issuedAt"
                val message = "${info.userId}|${info.product}|${info.issuedAt}"
                val signatureBytes = Base64.decode(info.signature, Base64.NO_WRAP)

                val valid = verifySignature(message.toByteArray(), signatureBytes, info.signatureType)

                _isLicenseValid.value = valid
                cachedLicenseInfo = if (valid) info else null

                // If valid and stored with legacy XOR, re-encrypt with AES-GCM
                if (valid && !isAesGcmEncrypted(fileBytes)) {
                    migrateToAesGcm(raw, licenseFile)
                }
            } catch (e: Exception) {
                Log.e(TAG, "License validation failed", e)
                _isLicenseValid.value = false
                cachedLicenseInfo = null
            }
        }
    }

    /**
     * Get the current license info, or null if no valid license.
     */
    fun getLicenseInfo(): LicenseInfo? = cachedLicenseInfo

    // ==================== Signature Verification ====================

    /**
     * Verify signature based on type. Ed25519 preferred, HMAC-SHA256 for backward compat.
     */
    private fun verifySignature(message: ByteArray, signatureBytes: ByteArray, type: String): Boolean {
        return when (type) {
            "hmac-sha256" -> verifyHmacSha256(message, signatureBytes)
            else -> verifyEd25519(message, signatureBytes)
        }
    }

    /**
     * Verify Ed25519 signature. Available on Android 13+ (API 33).
     * Returns false on older devices — they should use hmac-sha256 signed licenses.
     */
    private fun verifyEd25519(message: ByteArray, signatureBytes: ByteArray): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            Log.w(TAG, "Ed25519 requires API 33+, device is API ${Build.VERSION.SDK_INT}")
            return false
        }
        return try {
            val keyBytes = Base64.decode(ED25519_PUBLIC_KEY_B64, Base64.NO_WRAP)
            val keySpec = X509EncodedKeySpec(keyBytes)
            val keyFactory = KeyFactory.getInstance("EdDSA")
            val publicKey = keyFactory.generatePublic(keySpec)

            val signature = Signature.getInstance("EdDSA")
            signature.initVerify(publicKey)
            signature.update(message)
            signature.verify(signatureBytes)
        } catch (e: Exception) {
            Log.e(TAG, "Ed25519 verification failed: ${e.message}")
            false
        }
    }

    /**
     * HMAC-SHA256 verification for backward compatibility with pre-API 33 devices.
     * The secret is derived from the app's signing certificate, NOT hardcoded.
     */
    private fun verifyHmacSha256(message: ByteArray, signatureBytes: ByteArray): Boolean {
        return try {
            val secret = deriveHmacSecret()
            val mac = Mac.getInstance("HmacSHA256")
            mac.init(SecretKeySpec(secret, "HmacSHA256"))
            val expected = mac.doFinal(message)
            MessageDigest.isEqual(expected, signatureBytes) // Constant-time comparison
        } catch (e: Exception) {
            Log.e(TAG, "HMAC-SHA256 verification failed: ${e.message}")
            false
        }
    }

    /**
     * Derive HMAC secret from the app's signing certificate SHA-256 fingerprint.
     * This ties the secret to the specific signed build — if someone re-signs the APK,
     * the secret changes and existing HMAC licenses become invalid.
     */
    private fun deriveHmacSecret(): ByteArray {
        val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            context.packageManager.getPackageInfo(
                context.packageName,
                android.content.pm.PackageManager.GET_SIGNING_CERTIFICATES
            )
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(
                context.packageName,
                android.content.pm.PackageManager.GET_SIGNATURES
            )
        }

        val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            packageInfo.signingInfo?.apkContentsSigners
        } else {
            @Suppress("DEPRECATION")
            packageInfo.signatures
        }

        val certBytes = signatures?.firstOrNull()?.toByteArray()
            ?: throw IllegalStateException("No signing certificate found")

        // SHA-256 of the signing cert = 32 bytes, perfect for HMAC key
        return MessageDigest.getInstance("SHA-256").digest(certBytes)
    }

    // ==================== At-Rest Encryption ====================

    /**
     * Check if data starts with AES-GCM magic header
     */
    private fun isAesGcmEncrypted(data: ByteArray): Boolean {
        if (data.size < AES_GCM_MAGIC.size) return false
        return data.copyOfRange(0, AES_GCM_MAGIC.size).contentEquals(AES_GCM_MAGIC)
    }

    /**
     * Encrypt for storage. Uses AES-GCM with magic header prefix.
     */
    private fun encryptForStorage(plaintext: ByteArray): ByteArray {
        val key = getOrCreateAesKey()
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key)

        val iv = cipher.iv
        val ciphertext = cipher.doFinal(plaintext)

        // Format: [MAGIC(4)][IV(12)][ciphertext+tag]
        return AES_GCM_MAGIC + iv + ciphertext
    }

    /**
     * Decrypt from storage. Tries AES-GCM first, falls back to legacy XOR for migration.
     */
    private fun decryptFromStorage(data: ByteArray): ByteArray? {
        if (isAesGcmEncrypted(data)) {
            return decryptAesGcm(data)
        }
        // Legacy XOR format — try to decrypt for migration
        return try {
            legacyXorDecrypt(data)
        } catch (e: Exception) {
            Log.e(TAG, "Legacy decryption failed: ${e.message}")
            null
        }
    }

    /**
     * Decrypt AES-GCM data. Format: [MAGIC(4)][IV(12)][ciphertext+tag]
     */
    private fun decryptAesGcm(data: ByteArray): ByteArray? {
        val headerSize = AES_GCM_MAGIC.size + GCM_IV_LENGTH
        if (data.size < headerSize + 16) return null // Too short

        return try {
            val key = getOrCreateAesKey()
            val iv = data.copyOfRange(AES_GCM_MAGIC.size, AES_GCM_MAGIC.size + GCM_IV_LENGTH)
            val ciphertext = data.copyOfRange(headerSize, data.size)

            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_LENGTH, iv))
            cipher.doFinal(ciphertext)
        } catch (e: Exception) {
            Log.e(TAG, "AES-GCM decryption failed: ${e.message}")
            null
        }
    }

    /**
     * Legacy XOR decryption for migrating old license files.
     * Will be removed in a future version.
     */
    private fun legacyXorDecrypt(data: ByteArray): ByteArray {
        val key = byteArrayOf(0x4E, 0x45, 0x55, 0x52, 0x4F, 0x4E) // "NEURON"
        return ByteArray(data.size) { i -> (data[i].toInt() xor key[i % key.size].toInt()).toByte() }
    }

    /**
     * Re-encrypt a legacy XOR file with AES-GCM
     */
    private suspend fun migrateToAesGcm(plaintext: ByteArray, file: File) {
        try {
            val encrypted = encryptForStorage(plaintext)
            withContext(Dispatchers.IO) {
                file.writeBytes(encrypted)
            }
            Log.i(TAG, "Migrated license from legacy encryption to AES-GCM")
        } catch (e: Exception) {
            Log.w(TAG, "Migration to AES-GCM failed, will retry next validation: ${e.message}")
        }
    }

    /**
     * Get or create AES-256-GCM key in Android Keystore.
     * The key is hardware-backed on supported devices and never leaves the TEE.
     */
    private fun getOrCreateAesKey(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER)
        keyStore.load(null)

        val existing = keyStore.getEntry(KEYSTORE_ALIAS, null)
        if (existing is KeyStore.SecretKeyEntry) {
            return existing.secretKey
        }

        val keyGen = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES, KEYSTORE_PROVIDER
        )
        keyGen.init(
            KeyGenParameterSpec.Builder(
                KEYSTORE_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return keyGen.generateKey()
    }

    private fun getLicenseFile(): File {
        return File(context.filesDir, LICENSE_FILE_NAME)
    }
}
