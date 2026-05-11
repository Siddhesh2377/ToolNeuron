package com.dark.tool_neuron.data

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import com.dark.hxs_encryptor.BootIntegrity
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NativeIntegrity @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val prefs: AppPreferences,
) {

    data class VerifyOutcome(
        val ok: Boolean,
        val reasons: Int,
        val firstInstall: Boolean,
    )

    private data class AppFingerprint(
        val signerHash: ByteArray,
        val versionCode: Long,
        val lastUpdateTime: Long,
    ) {
        fun matches(other: AppFingerprint): Boolean =
            signerHash.contentEquals(other.signerHash) &&
                versionCode == other.versionCode &&
                lastUpdateTime == other.lastUpdateTime
    }

    private data class Manifest(
        val fingerprint: AppFingerprint,
        val entries: LinkedHashMap<String, ByteArray>,
    )

    fun bootVerify(): VerifyOutcome {
        val libsOnDisk = discoverNativeLibraries()
        if (libsOnDisk.isEmpty()) {
            return VerifyOutcome(ok = false, reasons = BootIntegrity.FAIL_BAD_INPUT, firstInstall = false)
        }

        val current = computeAppFingerprint()
            ?: return VerifyOutcome(ok = false, reasons = BootIntegrity.FAIL_BAD_INPUT, firstInstall = false)

        val stored = prefs.getBytes(KEY_LIB_MANIFEST)?.let(::decodeManifest)
        val headerChanged = stored == null || !stored.fingerprint.matches(current)

        if (headerChanged) {
            val fresh = LinkedHashMap<String, ByteArray>()
            for (path in libsOnDisk) {
                fresh[File(path).name] = hashFile(path)
            }
            prefs.putBytes(KEY_LIB_MANIFEST, encodeManifest(Manifest(current, fresh)))
            prefs.putBytes(KEY_APK_SIGNER_HASH, current.signerHash)
            return VerifyOutcome(ok = true, reasons = BootIntegrity.FAIL_NONE, firstInstall = stored == null)
        }

        val currentByName = libsOnDisk.associateBy { File(it).name }
        if (currentByName.keys != stored.entries.keys) {
            return VerifyOutcome(ok = false, reasons = BootIntegrity.FAIL_LIB_HASH, firstInstall = false)
        }

        val paths = stored.entries.keys.map { currentByName.getValue(it) }.toTypedArray()
        val hashes = stored.entries.keys.map { stored.entries.getValue(it) }.toTypedArray()
        val reasons = BootIntegrity.verify(paths, hashes)
        return VerifyOutcome(ok = reasons == BootIntegrity.FAIL_NONE, reasons = reasons, firstInstall = false)
    }

    fun scanProcessEnvironment(): Int = BootIntegrity.scanEnvironment()

    fun storedApkSignerHash(): ByteArray? = prefs.getBytes(KEY_APK_SIGNER_HASH)

    private fun discoverNativeLibraries(): List<String> {
        val dir = File(context.applicationInfo.nativeLibraryDir)
        if (!dir.isDirectory) return emptyList()
        return dir.listFiles { f -> f.isFile && f.name.endsWith(".so") }
            ?.map { it.absolutePath }
            ?.sorted()
            ?: emptyList()
    }

    private fun hashFile(path: String): ByteArray =
        BootIntegrity.hashFile(path)
            ?: throw SecurityException("Failed to hash $path during boot-integrity")

    private fun computeAppFingerprint(): AppFingerprint? {
        val pm = context.packageManager
        val pkg = context.packageName
        val info = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getPackageInfo(pkg, PackageManager.PackageInfoFlags.of(PackageManager.GET_SIGNING_CERTIFICATES.toLong()))
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageInfo(pkg, PackageManager.GET_SIGNING_CERTIFICATES)
            }
        } catch (_: Throwable) {
            return null
        }
        val signer = computeSignerHash(info) ?: return null
        val versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) info.longVersionCode else {
            @Suppress("DEPRECATION") info.versionCode.toLong()
        }
        return AppFingerprint(signer, versionCode, info.lastUpdateTime)
    }

    private fun computeSignerHash(info: android.content.pm.PackageInfo): ByteArray? {
        val signatures: Array<android.content.pm.Signature>? = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val sc = info.signingInfo ?: return null
                if (sc.hasMultipleSigners()) sc.apkContentsSigners else sc.signingCertificateHistory
            } else {
                @Suppress("DEPRECATION")
                info.signatures
            }
        } catch (_: Throwable) {
            null
        }
        val first = signatures?.firstOrNull() ?: return null
        val md = MessageDigest.getInstance("SHA-256")
        return md.digest(first.toByteArray())
    }

    private fun encodeManifest(m: Manifest): ByteArray {
        val signerLen = m.fingerprint.signerHash.size
        var total = 1 + 2 + signerLen + 8 + 8 + 4
        for ((name, hash) in m.entries) {
            total += 2 + name.toByteArray(Charsets.UTF_8).size + 2 + hash.size
        }
        val buf = ByteBuffer.allocate(total).order(ByteOrder.LITTLE_ENDIAN)
        buf.put(MANIFEST_VERSION)
        buf.putShort(signerLen.toShort())
        buf.put(m.fingerprint.signerHash)
        buf.putLong(m.fingerprint.versionCode)
        buf.putLong(m.fingerprint.lastUpdateTime)
        buf.putInt(m.entries.size)
        for ((name, hash) in m.entries) {
            val nameBytes = name.toByteArray(Charsets.UTF_8)
            buf.putShort(nameBytes.size.toShort())
            buf.put(nameBytes)
            buf.putShort(hash.size.toShort())
            buf.put(hash)
        }
        return buf.array()
    }

    private fun decodeManifest(data: ByteArray): Manifest? {
        if (data.size < 1 + 2 + 8 + 8 + 4) return null
        val buf = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)
        val version = buf.get()
        if (version != MANIFEST_VERSION) return null
        val signerLen = buf.getShort().toInt() and 0xFFFF
        if (signerLen !in 1..64 || buf.remaining() < signerLen + 20) return null
        val signer = ByteArray(signerLen).also { buf.get(it) }
        val versionCode = buf.getLong()
        val lastUpdateTime = buf.getLong()
        val count = buf.getInt()
        if (count < 0 || count > 64) return null
        val entries = LinkedHashMap<String, ByteArray>(count)
        repeat(count) {
            if (buf.remaining() < 2) return null
            val nameLen = buf.getShort().toInt() and 0xFFFF
            if (buf.remaining() < nameLen + 2) return null
            val nameBytes = ByteArray(nameLen).also { buf.get(it) }
            val hashLen = buf.getShort().toInt() and 0xFFFF
            if (hashLen != 32 || buf.remaining() < hashLen) return null
            val hash = ByteArray(hashLen).also { buf.get(it) }
            entries[String(nameBytes, Charsets.UTF_8)] = hash
        }
        return Manifest(AppFingerprint(signer, versionCode, lastUpdateTime), entries)
    }

    companion object {
        private const val MANIFEST_VERSION: Byte = 2
        private const val KEY_LIB_MANIFEST = "native_lib_manifest_v2"
        private const val KEY_APK_SIGNER_HASH = "apk_signer_hash_v1"
    }
}
