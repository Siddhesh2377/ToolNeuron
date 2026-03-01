package com.dark.tool_neuron.billing

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import java.security.MessageDigest

/**
 * Verifies the app hasn't been re-signed (tampered APK).
 *
 * If someone decompiles, patches the premium check, and re-signs with their own key,
 * this guard detects it and invalidates all premium features.
 *
 * The expected fingerprint is set at build time from the real signing certificate.
 */
internal object IntegrityGuard {

    private const val TAG = "IG"

    // SHA-256 fingerprint of the official release signing certificate.
    // Set this after your first signed release build.
    // To get it: keytool -list -v -keystore your.keystore | grep SHA256
    // Or: gradlew signingReport
    // Format: lowercase hex, no colons
    @Volatile
    private var expectedFingerprint: String? = null

    // Cache the result — checking once per app launch is sufficient
    @Volatile
    private var cachedResult: Boolean? = null

    /**
     * Set the expected signing certificate fingerprint.
     * Called once during app initialization with BuildConfig value.
     */
    fun init(fingerprint: String) {
        expectedFingerprint = fingerprint.lowercase().replace(":", "")
        cachedResult = null // Reset cache on re-init
    }

    /**
     * Returns true if the app's signing certificate matches the expected one.
     * Returns true if no fingerprint is configured (development builds).
     */
    fun isIntact(context: Context): Boolean {
        cachedResult?.let { return it }

        val expected = expectedFingerprint
        if (expected.isNullOrEmpty()) {
            // No fingerprint configured — allow (dev builds)
            return true
        }

        val result = try {
            val actual = getCurrentFingerprint(context)
            actual != null && actual == expected
        } catch (e: Exception) {
            Log.e(TAG, "Check failed", e)
            false
        }

        cachedResult = result
        return result
    }

    /**
     * Get SHA-256 fingerprint of the current APK's signing certificate.
     */
    private fun getCurrentFingerprint(context: Context): String? {
        val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            context.packageManager.getPackageInfo(
                context.packageName,
                PackageManager.GET_SIGNING_CERTIFICATES
            ).signingInfo?.apkContentsSigners
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(
                context.packageName,
                PackageManager.GET_SIGNATURES
            ).signatures
        }

        val certBytes = signatures?.firstOrNull()?.toByteArray() ?: return null
        val digest = MessageDigest.getInstance("SHA-256").digest(certBytes)
        return digest.joinToString("") { "%02x".format(it) }
    }
}
