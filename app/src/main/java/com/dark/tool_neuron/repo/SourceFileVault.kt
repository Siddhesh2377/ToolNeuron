package com.dark.tool_neuron.repo

import android.content.Context
import com.dark.hxs_encryptor.HxsEncryptor
import com.dark.tool_neuron.data.AppKeyStore
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SourceFileVault @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val keyStore: AppKeyStore,
    private val encryptor: HxsEncryptor,
) {

    private val baseDir: File by lazy {
        File(context.filesDir, SECURE_DIR).apply { mkdirs() }
    }

    init {
        runCatching { File(context.filesDir, LEGACY_DIR).deleteRecursively() }
    }

    fun exists(sourceId: String): Boolean = file(sourceId).exists()

    fun read(sourceId: String): ByteArray? {
        val f = file(sourceId)
        if (!f.exists()) return null
        val sealed = runCatching { f.readBytes() }.getOrNull() ?: return null
        return runCatching {
            encryptor.decrypt(sealed, keyFor(sourceId), aadFor(sourceId))
        }.getOrNull()
    }

    fun write(sourceId: String, bytes: ByteArray): Boolean {
        val target = file(sourceId)
        if (target.exists()) return true
        val sealed = runCatching {
            encryptor.encrypt(bytes, keyFor(sourceId), aadFor(sourceId))
        }.getOrNull() ?: return false
        val tmp = File(baseDir, "$sourceId.bin.tmp")
        return try {
            tmp.outputStream().use { it.write(sealed) }
            if (!tmp.renameTo(target)) {
                tmp.copyTo(target, overwrite = true)
                tmp.delete()
            }
            true
        } catch (_: Throwable) {
            tmp.delete()
            false
        }
    }

    fun delete(sourceId: String) {
        file(sourceId).delete()
    }

    fun clearAll() {
        baseDir.deleteRecursively()
        baseDir.mkdirs()
    }

    fun directory(): File = baseDir

    private fun file(sourceId: String): File = File(baseDir, "$sourceId.bin")

    private fun keyFor(sourceId: String): ByteArray {
        val dek = keyStore.unwrapOrCreateDek()
        val signerHash = keyStore.installSignerHash()
        return encryptor.deriveKey(
            ikm = dek,
            salt = signerHash,
            info = "$INFO_PREFIX@$sourceId",
        )
    }

    private fun aadFor(sourceId: String): ByteArray =
        sourceId.toByteArray(Charsets.UTF_8)

    companion object {
        private const val SECURE_DIR = "chat_documents/sources_v2"
        private const val LEGACY_DIR = "chat_documents/sources"
        private const val INFO_PREFIX = "tn.chat_doc_source.user_key.v2"
    }
}
