package com.dark.tool_neuron.worker

import android.content.Context
import android.net.Uri
import android.util.Log
import com.dark.tool_neuron.di.AppContainer
import com.dark.tool_neuron.models.vault.ChatExport
import com.dark.tool_neuron.vault.VaultHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.security.SecureRandom
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

class SystemBackupManager(private val context: Context) {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    companion object {
        private const val TAG = "SystemBackupManager"
        private const val MAGIC = 0x544E424B // "TNBK"
        private const val BACKUP_VERSION = 1
        private const val PBKDF2_ITERATIONS = 100_000
        private const val KEY_LENGTH = 256
        private const val SALT_LENGTH = 16
        private const val IV_LENGTH = 12
        private const val GCM_TAG_LENGTH = 128
        private const val DB_NAME = "llm_models_database"
    }

    sealed class BackupProgress {
        data object Starting : BackupProgress()
        data class Collecting(val step: String) : BackupProgress()
        data class Processing(val progress: Float) : BackupProgress()
        data object Complete : BackupProgress()
        data class Error(val message: String) : BackupProgress()
    }

    enum class EntryType {
        DB_FILE,
        VAULT_FILE,       // Legacy — raw vault files (device-bound encryption, not portable)
        DATASTORE_FILE,
        RAG_FILE,
        AVATAR_FILE,
        CHAT_EXPORT       // Portable — decrypted chat JSON, re-encrypted on restore
    }

    // ======================== BACKUP ========================

    suspend fun createBackup(
        outputUri: Uri,
        password: String,
        onProgress: (BackupProgress) -> Unit = {}
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            onProgress(BackupProgress.Starting)

            // 1. Collect all data entries
            val entries = mutableListOf<Triple<EntryType, String, ByteArray>>()

            // Room database
            onProgress(BackupProgress.Collecting("Database"))
            collectDatabaseFile()?.let { entries.add(Triple(EntryType.DB_FILE, DB_NAME, it)) }

            // Chat vault — export as decrypted JSON (portable across devices)
            onProgress(BackupProgress.Collecting("Chat vault"))
            try {
                if (!VaultHelper.isInitialized()) VaultHelper.initialize(context)
                val allChats = VaultHelper.getAllChats()
                for (chat in allChats) {
                    val export = VaultHelper.exportChat(chat.chatId)
                    val exportJson = json.encodeToString(ChatExport.serializer(), export)
                    entries.add(Triple(EntryType.CHAT_EXPORT, chat.chatId, exportJson.toByteArray(Charsets.UTF_8)))
                }
                Log.i(TAG, "Exported ${allChats.size} chats as portable JSON")
            } catch (e: Exception) {
                Log.w(TAG, "Failed to export chats: ${e.message}")
            }

            // DataStore preferences
            onProgress(BackupProgress.Collecting("Settings"))
            collectDataStoreFiles().forEach { (path, data) ->
                entries.add(Triple(EntryType.DATASTORE_FILE, path, data))
            }

            // RAG files
            onProgress(BackupProgress.Collecting("RAG data"))
            collectDirectoryFiles(File(context.filesDir, "rags")).forEach { (path, data) ->
                entries.add(Triple(EntryType.RAG_FILE, path, data))
            }

            // Persona avatars
            onProgress(BackupProgress.Collecting("Avatars"))
            collectDirectoryFiles(File(context.filesDir, "persona_avatars")).forEach { (path, data) ->
                entries.add(Triple(EntryType.AVATAR_FILE, path, data))
            }

            // 2. Build payload (serialized entries)
            onProgress(BackupProgress.Processing(0.3f))
            val payload = buildPayload(entries)

            // 3. Compress
            onProgress(BackupProgress.Processing(0.5f))
            val compressed = gzipCompress(payload)

            // 4. Encrypt
            onProgress(BackupProgress.Processing(0.7f))
            val salt = ByteArray(SALT_LENGTH).also { SecureRandom().nextBytes(it) }
            val iv = ByteArray(IV_LENGTH).also { SecureRandom().nextBytes(it) }
            val key = deriveKey(password, salt)
            val encrypted = encrypt(compressed, key, iv)

            // 5. Build metadata
            val metadata = JSONObject().apply {
                put("appVersion", getAppVersion())
                put("dbVersion", 6)
                put("backupVersion", BACKUP_VERSION)
                put("createdAt", System.currentTimeMillis())
                put("entryCount", entries.size)
            }
            val metadataBytes = metadata.toString().toByteArray(Charsets.UTF_8)

            // 6. Write archive to output URI
            onProgress(BackupProgress.Processing(0.9f))
            context.contentResolver.openOutputStream(outputUri)?.use { output ->
                DataOutputStream(output).apply {
                    writeInt(MAGIC)
                    writeInt(BACKUP_VERSION)
                    writeLong(System.currentTimeMillis())
                    writeInt(metadataBytes.size)
                    write(metadataBytes)
                    write(salt)
                    write(iv)
                    write(encrypted)
                    flush()
                }
            } ?: throw Exception("Failed to open output stream")

            onProgress(BackupProgress.Complete)
            Log.i(TAG, "Backup created: ${entries.size} entries")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Backup failed", e)
            onProgress(BackupProgress.Error(e.message ?: "Backup failed"))
            false
        }
    }

    // ======================== RESTORE ========================

    suspend fun restoreBackup(
        inputUri: Uri,
        password: String,
        onProgress: (BackupProgress) -> Unit = {}
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            onProgress(BackupProgress.Starting)

            // 1. Read archive
            onProgress(BackupProgress.Collecting("Reading backup"))
            val archiveData = context.contentResolver.openInputStream(inputUri)?.use { it.readBytes() }
                ?: throw Exception("Failed to read backup file")

            val input = DataInputStream(ByteArrayInputStream(archiveData))

            // Verify magic
            val magic = input.readInt()
            if (magic != MAGIC) throw Exception("Invalid backup file")

            val version = input.readInt()
            if (version > BACKUP_VERSION) throw Exception("Backup version $version not supported")

            val timestamp = input.readLong()

            // Read metadata
            val metadataLen = input.readInt()
            val metadataBytes = ByteArray(metadataLen)
            input.readFully(metadataBytes)

            // Read crypto params
            val salt = ByteArray(SALT_LENGTH)
            input.readFully(salt)
            val iv = ByteArray(IV_LENGTH)
            input.readFully(iv)

            // Read encrypted payload
            val headerSize = 4 + 4 + 8 + 4 + metadataLen + SALT_LENGTH + IV_LENGTH
            val encrypted = archiveData.copyOfRange(headerSize, archiveData.size)

            // 2. Decrypt
            onProgress(BackupProgress.Processing(0.3f))
            val key = deriveKey(password, salt)
            val compressed = try {
                decrypt(encrypted, key, iv)
            } catch (e: Exception) {
                throw Exception("Wrong password or corrupted backup")
            }

            // 3. Decompress
            onProgress(BackupProgress.Processing(0.5f))
            val payload = gzipDecompress(compressed)

            // 4. Parse entries
            val entries = parsePayload(payload)

            // 5. Separate chat exports from file entries
            val chatExports = entries.filter { it.first == EntryType.CHAT_EXPORT }
            val fileEntries = entries.filter { it.first != EntryType.CHAT_EXPORT && it.first != EntryType.VAULT_FILE }
            // VAULT_FILE entries are skipped — they use device-bound Keystore encryption

            // 6. Close everything before restoring files
            onProgress(BackupProgress.Collecting("Preparing restore"))
            closeVaultForBackup()
            AppContainer.closeDatabase()

            // 7. Clear existing vault (will be rebuilt from chat exports)
            val vaultDir = File(context.filesDir, "memory_vault")
            vaultDir.deleteRecursively()

            // 8. Restore file entries (DB, DataStore, RAG, Avatars)
            onProgress(BackupProgress.Processing(0.6f))
            for (entry in fileEntries) {
                restoreEntry(entry.first, entry.second, entry.third)
            }

            // 9. Re-initialize (vault starts fresh with new Keystore key)
            onProgress(BackupProgress.Processing(0.7f))
            AppContainer.reinitialize(context)

            // 10. Re-import chats into vault (re-encrypts with new device key)
            if (chatExports.isNotEmpty()) {
                onProgress(BackupProgress.Collecting("Restoring chats"))
                if (!VaultHelper.isInitialized()) VaultHelper.initialize(context)
                for ((index, entry) in chatExports.withIndex()) {
                    try {
                        val exportJson = String(entry.third, Charsets.UTF_8)
                        val chatExport = json.decodeFromString(ChatExport.serializer(), exportJson)
                        VaultHelper.importChat(chatExport)
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to import chat ${entry.second}: ${e.message}")
                    }
                    val chatProgress = 0.7f + (0.2f * (index + 1) / chatExports.size)
                    onProgress(BackupProgress.Processing(chatProgress))
                }
                Log.i(TAG, "Imported ${chatExports.size} chats")
            }

            onProgress(BackupProgress.Complete)
            Log.i(TAG, "Restore complete: ${entries.size} entries")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Restore failed", e)
            // Try to reinitialize even on failure
            try { AppContainer.reinitialize(context) } catch (_: Exception) {}
            onProgress(BackupProgress.Error(e.message ?: "Restore failed"))
            false
        }
    }

    // ======================== DELETE ALL ========================

    suspend fun deleteAllData(onProgress: (BackupProgress) -> Unit = {}): Boolean = withContext(Dispatchers.IO) {
        try {
            onProgress(BackupProgress.Starting)

            // Clear vault
            onProgress(BackupProgress.Collecting("Clearing chats"))
            VaultHelper.clearVault(context)

            // Clear database
            onProgress(BackupProgress.Collecting("Clearing database"))
            AppContainer.getDatabase().clearAllTables()

            // Clear RAG files
            onProgress(BackupProgress.Collecting("Clearing RAG data"))
            File(context.filesDir, "rags").deleteRecursively()

            // Clear avatar files
            onProgress(BackupProgress.Collecting("Clearing avatars"))
            File(context.filesDir, "persona_avatars").deleteRecursively()

            // Clear DataStore preferences
            onProgress(BackupProgress.Collecting("Clearing settings"))
            clearDataStoreFiles()

            onProgress(BackupProgress.Complete)
            Log.i(TAG, "All data deleted")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Delete all failed", e)
            onProgress(BackupProgress.Error(e.message ?: "Delete failed"))
            false
        }
    }

    // ======================== CRYPTO ========================

    private fun deriveKey(password: String, salt: ByteArray): SecretKey {
        val spec = PBEKeySpec(password.toCharArray(), salt, PBKDF2_ITERATIONS, KEY_LENGTH)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val keyBytes = factory.generateSecret(spec).encoded
        return SecretKeySpec(keyBytes, "AES")
    }

    private fun encrypt(data: ByteArray, key: SecretKey, iv: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(GCM_TAG_LENGTH, iv))
        return cipher.doFinal(data)
    }

    private fun decrypt(data: ByteArray, key: SecretKey, iv: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_LENGTH, iv))
        return cipher.doFinal(data)
    }

    // ======================== COMPRESSION ========================

    private fun gzipCompress(data: ByteArray): ByteArray {
        val bos = ByteArrayOutputStream()
        GZIPOutputStream(bos).use { it.write(data) }
        return bos.toByteArray()
    }

    private fun gzipDecompress(data: ByteArray): ByteArray {
        return GZIPInputStream(ByteArrayInputStream(data)).use { it.readBytes() }
    }

    // ======================== PAYLOAD SERIALIZATION ========================

    private fun buildPayload(entries: List<Triple<EntryType, String, ByteArray>>): ByteArray {
        val bos = ByteArrayOutputStream()
        val dos = DataOutputStream(bos)

        dos.writeInt(entries.size)
        for ((type, path, data) in entries) {
            dos.writeInt(type.ordinal)
            val pathBytes = path.toByteArray(Charsets.UTF_8)
            dos.writeInt(pathBytes.size)
            dos.write(pathBytes)
            dos.writeLong(data.size.toLong())
            dos.write(data)
        }
        dos.flush()
        return bos.toByteArray()
    }

    private fun parsePayload(payload: ByteArray): List<Triple<EntryType, String, ByteArray>> {
        val dis = DataInputStream(ByteArrayInputStream(payload))
        val count = dis.readInt()
        val entries = mutableListOf<Triple<EntryType, String, ByteArray>>()
        val types = EntryType.entries

        repeat(count) {
            val typeOrdinal = dis.readInt()
            val type = types[typeOrdinal]
            val pathLen = dis.readInt()
            val pathBytes = ByteArray(pathLen)
            dis.readFully(pathBytes)
            val path = String(pathBytes, Charsets.UTF_8)
            val dataLen = dis.readLong().toInt()
            val data = ByteArray(dataLen)
            dis.readFully(data)
            entries.add(Triple(type, path, data))
        }
        return entries
    }

    // ======================== DATA COLLECTION ========================

    private fun collectDatabaseFile(): ByteArray? {
        val db = AppContainer.getDatabase()
        // Checkpoint WAL to flush pending writes to main db file
        try {
            db.openHelper.writableDatabase.execSQL("PRAGMA wal_checkpoint(TRUNCATE)")
        } catch (e: Exception) {
            Log.w(TAG, "WAL checkpoint failed: ${e.message}")
        }

        val dbFile = context.getDatabasePath(DB_NAME)
        return if (dbFile.exists()) dbFile.readBytes() else null
    }

    private fun collectDirectoryFiles(dir: File): List<Pair<String, ByteArray>> {
        if (!dir.exists() || !dir.isDirectory) return emptyList()
        return dir.walkTopDown()
            .filter { it.isFile }
            .map { file ->
                val relativePath = file.relativeTo(dir).path
                relativePath to file.readBytes()
            }
            .toList()
    }

    private fun collectDataStoreFiles(): List<Pair<String, ByteArray>> {
        val dataStoreDir = File(context.filesDir.parentFile, "datastore")
        if (!dataStoreDir.exists()) return emptyList()
        return dataStoreDir.listFiles()
            ?.filter { it.isFile && it.name.endsWith(".preferences_pb") }
            ?.map { it.name to it.readBytes() }
            ?: emptyList()
    }

    // ======================== RESTORE HELPERS ========================

    private fun restoreEntry(type: EntryType, path: String, data: ByteArray) {
        when (type) {
            EntryType.DB_FILE -> {
                val dbFile = context.getDatabasePath(DB_NAME)
                // Delete WAL and SHM files
                File(dbFile.path + "-wal").delete()
                File(dbFile.path + "-shm").delete()
                dbFile.parentFile?.mkdirs()
                dbFile.writeBytes(data)
            }
            EntryType.VAULT_FILE -> {
                val vaultDir = File(context.filesDir, "memory_vault")
                vaultDir.mkdirs()
                File(vaultDir, path).apply {
                    parentFile?.mkdirs()
                    writeBytes(data)
                }
            }
            EntryType.DATASTORE_FILE -> {
                val dataStoreDir = File(context.filesDir.parentFile, "datastore")
                dataStoreDir.mkdirs()
                File(dataStoreDir, path).writeBytes(data)
            }
            EntryType.RAG_FILE -> {
                val ragDir = File(context.filesDir, "rags")
                ragDir.mkdirs()
                File(ragDir, path).apply {
                    parentFile?.mkdirs()
                    writeBytes(data)
                }
            }
            EntryType.AVATAR_FILE -> {
                val avatarDir = File(context.filesDir, "persona_avatars")
                avatarDir.mkdirs()
                File(avatarDir, path).apply {
                    parentFile?.mkdirs()
                    writeBytes(data)
                }
            }
            EntryType.CHAT_EXPORT -> { /* Handled separately after vault init */ }
        }
    }

    private fun clearDataStoreFiles() {
        val dataStoreDir = File(context.filesDir.parentFile, "datastore")
        if (dataStoreDir.exists()) {
            dataStoreDir.listFiles()?.filter { it.name.endsWith(".preferences_pb") }?.forEach {
                it.delete()
            }
        }
    }

    // ======================== VAULT LIFECYCLE ========================

    private suspend fun closeVaultForBackup(): Boolean {
        return try {
            if (VaultHelper.isInitialized()) {
                VaultHelper.close()
                true
            } else false
        } catch (e: Exception) {
            Log.w(TAG, "Failed to close vault: ${e.message}")
            false
        }
    }

    private suspend fun reopenVault() {
        try {
            VaultHelper.initialize(context)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to reopen vault: ${e.message}")
        }
    }

    // ======================== UTILS ========================

    private fun getAppVersion(): String {
        return try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "1.0"
        } catch (_: Exception) { "1.0" }
    }
}
