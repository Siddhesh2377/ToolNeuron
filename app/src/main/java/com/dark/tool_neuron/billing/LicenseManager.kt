package com.dark.tool_neuron.billing

import android.content.Context
import android.net.Uri
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
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
data class LicenseInfo(
    val userId: String,
    val product: String,
    val issuedAt: Long,
    val signature: String
)

@Singleton
class LicenseManager @Inject constructor(
    @ApplicationContext private val context: Context
) {

    companion object {
        private const val TAG = "LicenseManager"
        private const val LICENSE_FILE_NAME = "toolneuron_pro.tnlicense"

        // Placeholder Ed25519 public key (Base64-encoded).
        // Replace with the real public key once the signing keypair is generated.
        private const val ED25519_PUBLIC_KEY_B64 =
            "MCowBQYDK2VwAyEAPlaceholderKeyReplaceWithRealEd25519PublicKey00="

        // HMAC-SHA256 fallback secret for platforms where EdDSA is unavailable.
        // This is a temporary measure; upgrade to Ed25519 via JNI later.
        private val HMAC_SECRET = byteArrayOf(
            0x54, 0x4E, 0x50, 0x52, 0x4F, 0x2D, 0x48, 0x4D,
            0x41, 0x43, 0x2D, 0x53, 0x45, 0x43, 0x52, 0x45,
            0x54, 0x2D, 0x4B, 0x45, 0x59, 0x2D, 0x56, 0x31,
            0x2D, 0x50, 0x4C, 0x41, 0x43, 0x45, 0x48, 0x4F
        )

        private val json = Json { ignoreUnknownKeys = true }
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val _isLicenseValid = MutableStateFlow(false)
    val isLicenseValid: StateFlow<Boolean> = _isLicenseValid.asStateFlow()

    private var cachedLicenseInfo: LicenseInfo? = null

    /**
     * Import a .tnlicense file from the given URI.
     * Copies it to internal storage and validates.
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

                // Write encrypted copy to internal storage
                val licenseFile = getLicenseFile()
                withContext(Dispatchers.IO) {
                    licenseFile.writeBytes(obfuscateBytes(bytes))
                }

                Log.d(TAG, "License file imported successfully")
                validateLicense()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to import license", e)
                _isLicenseValid.value = false
            }
        }
    }

    /**
     * Validate the stored license file.
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

                val raw = withContext(Dispatchers.IO) {
                    deobfuscateBytes(licenseFile.readBytes())
                }

                val licenseJson = raw.decodeToString()
                val info = json.decodeFromString<LicenseInfo>(licenseJson)

                // Verify product field
                if (info.product != "pro") {
                    Log.w(TAG, "Invalid product in license: ${info.product}")
                    _isLicenseValid.value = false
                    cachedLicenseInfo = null
                    return@launch
                }

                // Build the message that was signed: "userId|product|issuedAt"
                val message = "${info.userId}|${info.product}|${info.issuedAt}"
                val signatureBytes = Base64.decode(info.signature, Base64.NO_WRAP)

                val valid = verifySignature(message.toByteArray(), signatureBytes)

                _isLicenseValid.value = valid
                cachedLicenseInfo = if (valid) info else null

                Log.d(TAG, "License validation result: $valid")
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

    /**
     * Try EdDSA (Ed25519) first; fall back to HMAC-SHA256 if the provider is
     * not available on this device.
     */
    private fun verifySignature(message: ByteArray, signatureBytes: ByteArray): Boolean {
        return try {
            verifyEd25519(message, signatureBytes)
        } catch (e: Exception) {
            Log.d(TAG, "EdDSA not available, falling back to HMAC-SHA256: ${e.message}")
            verifyHmacSha256(message, signatureBytes)
        }
    }

    private fun verifyEd25519(message: ByteArray, signatureBytes: ByteArray): Boolean {
        val keyBytes = Base64.decode(ED25519_PUBLIC_KEY_B64, Base64.NO_WRAP)
        val keySpec = X509EncodedKeySpec(keyBytes)
        val keyFactory = KeyFactory.getInstance("EdDSA")
        val publicKey = keyFactory.generatePublic(keySpec)

        val signature = Signature.getInstance("EdDSA")
        signature.initVerify(publicKey)
        signature.update(message)
        return signature.verify(signatureBytes)
    }

    private fun verifyHmacSha256(message: ByteArray, signatureBytes: ByteArray): Boolean {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(HMAC_SECRET, "HmacSHA256"))
        val expected = mac.doFinal(message)
        return expected.contentEquals(signatureBytes)
    }

    private fun getLicenseFile(): File {
        return File(context.filesDir, LICENSE_FILE_NAME)
    }

    /**
     * Simple XOR obfuscation for at-rest storage. Not cryptographically secure
     * but raises the bar slightly above plaintext for casual inspection.
     */
    private fun obfuscateBytes(data: ByteArray): ByteArray {
        val key = byteArrayOf(0x4E, 0x45, 0x55, 0x52, 0x4F, 0x4E)
        return ByteArray(data.size) { i -> (data[i].toInt() xor key[i % key.size].toInt()).toByte() }
    }

    private fun deobfuscateBytes(data: ByteArray): ByteArray = obfuscateBytes(data)
}
