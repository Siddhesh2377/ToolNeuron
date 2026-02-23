package com.dark.tool_neuron.worker

import android.content.Context
import android.net.Uri
import android.util.Log
import com.dark.tool_neuron.di.AppContainer
import com.dark.tool_neuron.models.enums.PathType
import com.dark.tool_neuron.models.enums.ProviderType
import com.dark.tool_neuron.models.table_schema.AiMemory
import com.dark.tool_neuron.models.table_schema.KnowledgeRelation
import com.dark.tool_neuron.models.table_schema.MemoryCategory
import com.dark.tool_neuron.models.vault.ChatExport
import com.dark.tool_neuron.vault.VaultHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.security.MessageDigest
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
        private const val BACKUP_VERSION = 2
        private const val PBKDF2_ITERATIONS = 100_000
        private const val KEY_LENGTH = 256
        private const val SALT_LENGTH = 16
        private const val IV_LENGTH = 12
        private const val GCM_TAG_LENGTH = 128
        private const val DB_NAME = "llm_models_database"
        private const val MAX_MODEL_FILE_SIZE = 2L * 1024 * 1024 * 1024 // 2GB
    }

    sealed class BackupProgress {
        data object Starting : BackupProgress()
        data class Collecting(val component: String, val componentIndex: Int = 0, val componentCount: Int = 1) : BackupProgress()
        data class Processing(val progress: Float, val stage: String = "") : BackupProgress()
        data object Complete : BackupProgress()
        data class Error(val message: String) : BackupProgress()
    }

    // Ordinals: 0=DB_FILE, 1=VAULT_FILE, 2=DATASTORE_FILE, 3=RAG_FILE, 4=AVATAR_FILE, 5=CHAT_EXPORT, 6=MODEL_FILE, 7=MANIFEST, 8=MEMORY_EXPORT
    // v1 backups only used ordinals 0-5. Appending 6-8 maintains backward compatibility.
    enum class EntryType {
        DB_FILE,
        VAULT_FILE,       // Legacy — raw vault files (device-bound encryption, not portable)
        DATASTORE_FILE,
        RAG_FILE,
        AVATAR_FILE,
        CHAT_EXPORT,      // Portable — decrypted chat JSON, re-encrypted on restore
        MODEL_FILE,       // NEW v2 — model GGUF/SD/TTS files (relative path under models/)
        MANIFEST,         // NEW v2 — SHA-256 checksums for validation
        MEMORY_EXPORT     // NEW v3 — per-persona memory + KG triples as JSON
    }

    data class BackupOptions(
        val includeRagFiles: Boolean = true,
        val includeModelFiles: Boolean = false,
        val modelIdsToInclude: Set<String> = emptySet()
    )

    data class BackupSizeEstimate(
        val databaseSize: Long = 0,
        val chatVaultSize: Long = 0,
        val dataStoreSize: Long = 0,
        val ragFilesSize: Long = 0,
        val avatarFilesSize: Long = 0,
        val modelFilesSize: Long = 0,
        val modelBreakdown: List<ModelSizeInfo> = emptyList()
    ) {
        val totalSize: Long
            get() = databaseSize + chatVaultSize + dataStoreSize + ragFilesSize + avatarFilesSize + modelFilesSize
    }

    data class ModelSizeInfo(
        val modelId: String,
        val modelName: String,
        val providerType: ProviderType,
        val sizeBytes: Long,
        val canBackup: Boolean, // false if content:// URI or > 2GB
        val reason: String = ""
    )

    // ======================== MEMORY EXPORT MODELS ========================

    @Serializable
    data class PersonaMemoryExport(
        val personaId: String?,
        val personaName: String?,
        val memories: List<MemoryFactExport>,
        val kgTriples: List<KgTripleExport>,
        val exportedAt: Long
    )

    @Serializable
    data class MemoryFactExport(
        val id: String,
        val fact: String,
        val category: String,
        val sourceChatId: String? = null,
        val createdAt: Long,
        val updatedAt: Long,
        val lastAccessedAt: Long,
        val accessCount: Int,
        val isSummarized: Boolean = false,
        val summaryGroupId: String? = null,
        val personaId: String? = null
    )

    @Serializable
    data class KgTripleExport(
        val id: String,
        val subjectId: String,
        val predicate: String,
        val objectId: String,
        val confidence: Float,
        val sourceFactId: String? = null,
        val createdAt: Long,
        val personaId: String? = null
    )

    // ======================== SIZE ESTIMATION ========================

    suspend fun estimateBackupSize(options: BackupOptions = BackupOptions()): BackupSizeEstimate = withContext(Dispatchers.IO) {
        val dbFile = context.getDatabasePath(DB_NAME)
        val databaseSize = if (dbFile.exists()) dbFile.length() else 0L

        val vaultDir = File(context.filesDir, "memory_vault")
        val chatVaultSize = if (vaultDir.exists()) dirSize(vaultDir) else 0L

        val dataStoreDir = File(context.filesDir.parentFile, "datastore")
        val dataStoreSize = if (dataStoreDir.exists()) {
            dataStoreDir.listFiles()
                ?.filter { it.isFile && it.name.endsWith(".preferences_pb") }
                ?.sumOf { it.length() } ?: 0L
        } else 0L

        val ragDir = File(context.filesDir, "rags")
        val ragFilesSize = if (options.includeRagFiles && ragDir.exists()) dirSize(ragDir) else 0L

        val avatarDir = File(context.filesDir, "persona_avatars")
        val avatarFilesSize = if (avatarDir.exists()) dirSize(avatarDir) else 0L

        var modelFilesSize = 0L
        val modelBreakdown = mutableListOf<ModelSizeInfo>()

        if (options.includeModelFiles) {
            val db = AppContainer.getDatabase()
            val models = db.modelDao().getAllOnce()
            val modelsDir = File(context.filesDir, "models")

            for (model in models) {
                if (options.modelIdsToInclude.isNotEmpty() && model.id !in options.modelIdsToInclude) continue

                val canBackup: Boolean
                val reason: String
                val sizeBytes: Long

                if (model.pathType == PathType.CONTENT_URI) {
                    canBackup = false
                    reason = "External file (content:// URI)"
                    sizeBytes = model.fileSize ?: 0L
                } else {
                    val modelFile = File(model.modelPath)
                    if (!modelFile.exists()) {
                        canBackup = false
                        reason = "File not found"
                        sizeBytes = 0L
                    } else if (!modelFile.absolutePath.startsWith(modelsDir.absolutePath)) {
                        canBackup = false
                        reason = "Outside app models directory"
                        sizeBytes = if (modelFile.isDirectory) dirSize(modelFile) else modelFile.length()
                    } else {
                        sizeBytes = if (modelFile.isDirectory) dirSize(modelFile) else modelFile.length()
                        if (sizeBytes > MAX_MODEL_FILE_SIZE) {
                            canBackup = false
                            reason = "File too large (>${MAX_MODEL_FILE_SIZE / (1024 * 1024 * 1024)}GB)"
                        } else {
                            canBackup = true
                            reason = ""
                        }
                    }
                }

                modelBreakdown.add(
                    ModelSizeInfo(
                        modelId = model.id,
                        modelName = model.modelName,
                        providerType = model.providerType,
                        sizeBytes = sizeBytes,
                        canBackup = canBackup,
                        reason = reason
                    )
                )

                if (canBackup) {
                    modelFilesSize += sizeBytes
                }
            }
        }

        BackupSizeEstimate(
            databaseSize = databaseSize,
            chatVaultSize = chatVaultSize,
            dataStoreSize = dataStoreSize,
            ragFilesSize = ragFilesSize,
            avatarFilesSize = avatarFilesSize,
            modelFilesSize = modelFilesSize,
            modelBreakdown = modelBreakdown
        )
    }

    // ======================== BACKUP ========================

    suspend fun createBackup(
        outputUri: Uri,
        password: String,
        options: BackupOptions = BackupOptions(),
        onProgress: (BackupProgress) -> Unit = {}
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            onProgress(BackupProgress.Starting)

            val componentCount = 6 + (if (options.includeModelFiles) 1 else 0)
            var componentIndex = 0

            // 1. Collect all data entries
            val entries = mutableListOf<Triple<EntryType, String, ByteArray>>()
            val checksums = mutableMapOf<String, String>() // path -> sha256

            // Room database
            onProgress(BackupProgress.Collecting("Database", ++componentIndex, componentCount))
            collectDatabaseFile()?.let {
                entries.add(Triple(EntryType.DB_FILE, DB_NAME, it))
                checksums[DB_NAME] = sha256(it)
            }

            // Chat vault — export as decrypted JSON (portable across devices)
            onProgress(BackupProgress.Collecting("Chat vault", ++componentIndex, componentCount))
            try {
                if (!VaultHelper.isInitialized()) VaultHelper.initialize(context)
                val allChats = VaultHelper.getAllChats()
                for (chat in allChats) {
                    val export = VaultHelper.exportChat(chat.chatId)
                    val exportJson = json.encodeToString(ChatExport.serializer(), export)
                    val data = exportJson.toByteArray(Charsets.UTF_8)
                    entries.add(Triple(EntryType.CHAT_EXPORT, chat.chatId, data))
                    checksums["chat:${chat.chatId}"] = sha256(data)
                }
                Log.i(TAG, "Exported ${allChats.size} chats as portable JSON")
            } catch (e: Exception) {
                Log.w(TAG, "Failed to export chats: ${e.message}")
            }

            // DataStore preferences
            onProgress(BackupProgress.Collecting("Settings", ++componentIndex, componentCount))
            collectDataStoreFiles().forEach { (path, data) ->
                entries.add(Triple(EntryType.DATASTORE_FILE, path, data))
                checksums["ds:$path"] = sha256(data)
            }

            // RAG files
            if (options.includeRagFiles) {
                onProgress(BackupProgress.Collecting("RAG data", ++componentIndex, componentCount))
                collectDirectoryFiles(File(context.filesDir, "rags")).forEach { (path, data) ->
                    entries.add(Triple(EntryType.RAG_FILE, path, data))
                    checksums["rag:$path"] = sha256(data)
                }
            } else {
                componentIndex++
            }

            // Persona avatars
            onProgress(BackupProgress.Collecting("Avatars", ++componentIndex, componentCount))
            collectDirectoryFiles(File(context.filesDir, "persona_avatars")).forEach { (path, data) ->
                entries.add(Triple(EntryType.AVATAR_FILE, path, data))
                checksums["avatar:$path"] = sha256(data)
            }

            // Per-persona memory + KG export
            try {
                onProgress(BackupProgress.Collecting("Memories", ++componentIndex, componentCount))
                collectMemoryExports().forEach { (personaKey, data) ->
                    entries.add(Triple(EntryType.MEMORY_EXPORT, personaKey, data))
                    checksums["memory:$personaKey"] = sha256(data)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Memory export failed: ${e.message}")
            }

            // Model files (v2)
            if (options.includeModelFiles) {
                onProgress(BackupProgress.Collecting("AI Models", ++componentIndex, componentCount))
                collectModelFiles(options).forEach { (path, data) ->
                    entries.add(Triple(EntryType.MODEL_FILE, path, data))
                    checksums["model:$path"] = sha256(data)
                }
            }

            // Add manifest entry (v2)
            val manifestJson = JSONObject()
            for ((key, hash) in checksums) {
                manifestJson.put(key, hash)
            }
            entries.add(Triple(EntryType.MANIFEST, "manifest.json", manifestJson.toString().toByteArray(Charsets.UTF_8)))

            // 2. Build payload (serialized entries)
            onProgress(BackupProgress.Processing(0.3f, "Serializing"))
            val payload = buildPayload(entries)

            // 3. Compress
            onProgress(BackupProgress.Processing(0.5f, "Compressing"))
            val compressed = gzipCompress(payload)

            // 4. Encrypt
            onProgress(BackupProgress.Processing(0.7f, "Encrypting"))
            val salt = ByteArray(SALT_LENGTH).also { SecureRandom().nextBytes(it) }
            val iv = ByteArray(IV_LENGTH).also { SecureRandom().nextBytes(it) }
            val key = deriveKey(password, salt)
            val encrypted = encrypt(compressed, key, iv)

            // 5. Determine effective backup version
            // Write v1 header if no v2-only features are used (backward compatible with older app versions)
            val hasModelEntries = entries.any { it.first == EntryType.MODEL_FILE }
            val effectiveVersion = if (hasModelEntries) 2 else 1

            // 5. Build metadata
            val metadata = JSONObject().apply {
                put("appVersion", getAppVersion())
                put("dbVersion", 7)
                put("backupVersion", effectiveVersion)
                put("createdAt", System.currentTimeMillis())
                put("entryCount", entries.size)
                put("includesModels", options.includeModelFiles)
                put("includesRags", options.includeRagFiles)
            }
            val metadataBytes = metadata.toString().toByteArray(Charsets.UTF_8)

            // 6. Write archive to output URI
            onProgress(BackupProgress.Processing(0.9f, "Writing"))
            context.contentResolver.openOutputStream(outputUri)?.use { output ->
                DataOutputStream(output).apply {
                    writeInt(MAGIC)
                    writeInt(effectiveVersion)
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
            Log.i(TAG, "Backup v$effectiveVersion created: ${entries.size} entries (models=${options.includeModelFiles}, modelEntries=$hasModelEntries)")
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

            // ========== PHASE 1: VALIDATE (non-destructive) ==========
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

            // Decrypt (validates password)
            onProgress(BackupProgress.Processing(0.1f, "Validating password"))
            val key = deriveKey(password, salt)
            val compressed = try {
                decrypt(encrypted, key, iv)
            } catch (e: Exception) {
                throw Exception("Wrong password or corrupted backup")
            }

            // Decompress
            onProgress(BackupProgress.Processing(0.2f, "Decompressing"))
            val payload = gzipDecompress(compressed)

            // Parse entries
            val entries = parsePayload(payload)

            // Validate manifest checksums (v2 only)
            if (version >= 2) {
                onProgress(BackupProgress.Processing(0.25f, "Validating checksums"))
                val manifestEntry = entries.find { it.first == EntryType.MANIFEST }
                if (manifestEntry != null) {
                    validateManifest(manifestEntry.third, entries)
                }
            }

            // Separate entries by type
            val chatExports = entries.filter { it.first == EntryType.CHAT_EXPORT }
            val modelEntries = entries.filter { it.first == EntryType.MODEL_FILE }
            val memoryExports = entries.filter { it.first == EntryType.MEMORY_EXPORT }
            val fileEntries = entries.filter {
                it.first != EntryType.CHAT_EXPORT &&
                it.first != EntryType.VAULT_FILE &&
                it.first != EntryType.MODEL_FILE &&
                it.first != EntryType.MANIFEST &&
                it.first != EntryType.MEMORY_EXPORT
            }
            // VAULT_FILE entries are skipped — they use device-bound Keystore encryption

            // ========== PHASE 2: SAFETY SNAPSHOT ==========
            onProgress(BackupProgress.Processing(0.3f, "Creating safety snapshot"))
            val safetyDir = createSafetySnapshot()

            try {
                // ========== PHASE 3: RESTORE (destructive) ==========
                onProgress(BackupProgress.Collecting("Preparing restore"))
                closeVaultForBackup()
                AppContainer.closeDatabase()

                // Clear existing vault (will be rebuilt from chat exports)
                val vaultDir = File(context.filesDir, "memory_vault")
                vaultDir.deleteRecursively()

                // Restore file entries (DB, DataStore, RAG, Avatars)
                onProgress(BackupProgress.Processing(0.4f, "Restoring files"))
                for (entry in fileEntries) {
                    restoreEntry(entry.first, entry.second, entry.third)
                }

                // Restore model files (v2)
                if (modelEntries.isNotEmpty()) {
                    onProgress(BackupProgress.Processing(0.5f, "Restoring models"))
                    for (entry in modelEntries) {
                        restoreModelEntry(entry.second, entry.third)
                    }
                    Log.i(TAG, "Restored ${modelEntries.size} model file entries")
                }

                // Re-initialize (vault starts fresh with new Keystore key)
                onProgress(BackupProgress.Processing(0.6f, "Reinitializing"))
                AppContainer.reinitialize(context)

                // Re-import chats into vault (re-encrypts with new device key)
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
                        onProgress(BackupProgress.Processing(chatProgress, "Restoring chats"))
                    }
                    Log.i(TAG, "Imported ${chatExports.size} chats")
                }

                // Restore per-persona memory exports (supplement DB restore)
                if (memoryExports.isNotEmpty()) {
                    onProgress(BackupProgress.Processing(0.92f, "Restoring memories"))
                    restoreMemoryExports(memoryExports)
                }

                // Success — delete safety snapshot
                safetyDir?.deleteRecursively()

                onProgress(BackupProgress.Complete)
                Log.i(TAG, "Restore complete: ${entries.size} entries (v$version)")
                true
            } catch (e: Exception) {
                // ========== PHASE 4: ROLLBACK ==========
                Log.e(TAG, "Restore failed, attempting rollback", e)
                try {
                    restoreFromSafetySnapshot(safetyDir)
                    AppContainer.reinitialize(context)
                    Log.i(TAG, "Rollback successful")
                } catch (rollbackEx: Exception) {
                    Log.e(TAG, "Rollback also failed", rollbackEx)
                }
                safetyDir?.deleteRecursively()
                onProgress(BackupProgress.Error(e.message ?: "Restore failed"))
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Restore failed (pre-destructive phase)", e)
            // No rollback needed — we haven't modified anything yet
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

    // ======================== SAFETY SNAPSHOT ========================

    private fun createSafetySnapshot(): File? {
        return try {
            val safetyDir = File(context.cacheDir, "restore_safety_${System.currentTimeMillis()}")
            safetyDir.mkdirs()

            // Copy DB file
            val dbFile = context.getDatabasePath(DB_NAME)
            if (dbFile.exists()) {
                dbFile.copyTo(File(safetyDir, "db_backup"), overwrite = true)
            }

            // Copy DataStore files
            val dsBackupDir = File(safetyDir, "datastore")
            dsBackupDir.mkdirs()
            val dataStoreDir = File(context.filesDir.parentFile, "datastore")
            if (dataStoreDir.exists()) {
                dataStoreDir.listFiles()
                    ?.filter { it.isFile && it.name.endsWith(".preferences_pb") }
                    ?.forEach { it.copyTo(File(dsBackupDir, it.name), overwrite = true) }
            }

            Log.d(TAG, "Safety snapshot created at ${safetyDir.absolutePath}")
            safetyDir
        } catch (e: Exception) {
            Log.w(TAG, "Failed to create safety snapshot: ${e.message}")
            null
        }
    }

    private fun restoreFromSafetySnapshot(safetyDir: File?) {
        if (safetyDir == null || !safetyDir.exists()) {
            Log.w(TAG, "No safety snapshot available for rollback")
            return
        }

        // Restore DB
        val dbBackup = File(safetyDir, "db_backup")
        if (dbBackup.exists()) {
            val dbFile = context.getDatabasePath(DB_NAME)
            File(dbFile.path + "-wal").delete()
            File(dbFile.path + "-shm").delete()
            dbBackup.copyTo(dbFile, overwrite = true)
        }

        // Restore DataStore
        val dsBackupDir = File(safetyDir, "datastore")
        if (dsBackupDir.exists()) {
            val dataStoreDir = File(context.filesDir.parentFile, "datastore")
            dataStoreDir.mkdirs()
            dsBackupDir.listFiles()?.forEach { file ->
                file.copyTo(File(dataStoreDir, file.name), overwrite = true)
            }
        }

        Log.d(TAG, "Restored from safety snapshot")
    }

    // ======================== MANIFEST VALIDATION ========================

    private fun validateManifest(manifestData: ByteArray, entries: List<Triple<EntryType, String, ByteArray>>) {
        val manifestJson = JSONObject(String(manifestData, Charsets.UTF_8))
        var mismatches = 0

        // Build a quick lookup from entries by their checksum key
        for (entry in entries) {
            if (entry.first == EntryType.MANIFEST) continue

            val key = when (entry.first) {
                EntryType.DB_FILE -> entry.second
                EntryType.CHAT_EXPORT -> "chat:${entry.second}"
                EntryType.DATASTORE_FILE -> "ds:${entry.second}"
                EntryType.RAG_FILE -> "rag:${entry.second}"
                EntryType.AVATAR_FILE -> "avatar:${entry.second}"
                EntryType.MODEL_FILE -> "model:${entry.second}"
                EntryType.MEMORY_EXPORT -> "memory:${entry.second}"
                else -> continue
            }

            val expectedHash = manifestJson.optString(key, "")
            if (expectedHash.isNotEmpty()) {
                val actualHash = sha256(entry.third)
                if (actualHash != expectedHash) {
                    Log.w(TAG, "Checksum mismatch for $key")
                    mismatches++
                }
            }
        }

        if (mismatches > 0) {
            throw Exception("Backup integrity check failed: $mismatches file(s) corrupted")
        }
        Log.d(TAG, "Manifest validation passed")
    }

    // ======================== MODEL FILE COLLECTION ========================

    private suspend fun collectModelFiles(options: BackupOptions): List<Pair<String, ByteArray>> {
        val results = mutableListOf<Pair<String, ByteArray>>()
        val modelsDir = File(context.filesDir, "models")
        Log.d(TAG, "collectModelFiles: modelsDir=${modelsDir.absolutePath}, exists=${modelsDir.exists()}")
        if (!modelsDir.exists()) {
            Log.w(TAG, "collectModelFiles: models directory does not exist")
            return results
        }

        val db = AppContainer.getDatabase()
        val models = db.modelDao().getAllOnce()
        Log.d(TAG, "collectModelFiles: found ${models.size} models in DB, filter=${options.modelIdsToInclude}")

        for (model in models) {
            Log.d(TAG, "collectModelFiles: checking model '${model.modelName}' path=${model.modelPath} pathType=${model.pathType}")
            if (options.modelIdsToInclude.isNotEmpty() && model.id !in options.modelIdsToInclude) {
                Log.d(TAG, "collectModelFiles: skipping '${model.modelName}' — not in modelIdsToInclude")
                continue
            }
            if (model.pathType == PathType.CONTENT_URI) {
                Log.d(TAG, "collectModelFiles: skipping '${model.modelName}' — content:// URI")
                continue
            }

            val modelFile = File(model.modelPath)
            if (!modelFile.exists()) {
                Log.w(TAG, "collectModelFiles: skipping '${model.modelName}' — file not found at ${model.modelPath}")
                continue
            }
            if (!modelFile.absolutePath.startsWith(modelsDir.absolutePath)) {
                Log.d(TAG, "collectModelFiles: skipping '${model.modelName}' — path not under modelsDir (${modelFile.absolutePath})")
                continue
            }

            if (modelFile.isDirectory) {
                // Diffusion/TTS: walk directory
                modelFile.walkTopDown().filter { it.isFile }.forEach { file ->
                    if (file.length() <= MAX_MODEL_FILE_SIZE) {
                        val relativePath = file.relativeTo(modelsDir).path
                        results.add(relativePath to file.readBytes())
                    } else {
                        Log.w(TAG, "Skipping oversized model file: ${file.name} (${file.length()} bytes)")
                    }
                }
            } else {
                // GGUF: single file
                if (modelFile.length() <= MAX_MODEL_FILE_SIZE) {
                    val relativePath = modelFile.relativeTo(modelsDir).path
                    results.add(relativePath to modelFile.readBytes())
                } else {
                    Log.w(TAG, "Skipping oversized model file: ${modelFile.name} (${modelFile.length()} bytes)")
                }
            }
        }

        Log.i(TAG, "Collected ${results.size} model file entries")
        return results
    }

    private fun restoreModelEntry(relativePath: String, data: ByteArray) {
        val modelsDir = File(context.filesDir, "models")
        modelsDir.mkdirs()
        val destFile = File(modelsDir, relativePath)
        destFile.parentFile?.mkdirs()
        destFile.writeBytes(data)
    }

    // ======================== MEMORY EXPORT/RESTORE ========================

    private suspend fun collectMemoryExports(): List<Pair<String, ByteArray>> {
        val results = mutableListOf<Pair<String, ByteArray>>()
        val db = AppContainer.getDatabase()
        val memoryDao = db.aiMemoryDao()
        val relationDao = db.knowledgeRelationDao()
        val personaDao = db.personaDao()

        val allMemories = memoryDao.getAllOnce()
        val allRelations = relationDao.getAll()
        val personas = personaDao.getAllOnce()
        val personaNameMap = personas.associate { it.id to it.name }

        // Group memories by persona_id (null = global)
        val memoriesByPersona = allMemories.groupBy { it.personaId }

        for ((personaId, memories) in memoriesByPersona) {
            val memoryExports = memories.map { m ->
                MemoryFactExport(
                    id = m.id,
                    fact = m.fact,
                    category = m.category.name,
                    sourceChatId = m.sourceChatId,
                    createdAt = m.createdAt,
                    updatedAt = m.updatedAt,
                    lastAccessedAt = m.lastAccessedAt,
                    accessCount = m.accessCount,
                    isSummarized = m.isSummarized,
                    summaryGroupId = m.summaryGroupId,
                    personaId = m.personaId
                )
            }

            // KG relations matching this persona (or global)
            val matchingRelations = allRelations.filter { it.personaId == personaId }
            val kgExports = matchingRelations.map { r ->
                KgTripleExport(
                    id = r.id,
                    subjectId = r.subjectId,
                    predicate = r.predicate,
                    objectId = r.objectId,
                    confidence = r.confidence,
                    sourceFactId = r.sourceFactId,
                    createdAt = r.createdAt,
                    personaId = r.personaId
                )
            }

            val export = PersonaMemoryExport(
                personaId = personaId,
                personaName = personaId?.let { personaNameMap[it] },
                memories = memoryExports,
                kgTriples = kgExports,
                exportedAt = System.currentTimeMillis()
            )

            val exportJson = json.encodeToString(PersonaMemoryExport.serializer(), export)
            val key = personaId ?: "global"
            results.add(key to exportJson.toByteArray(Charsets.UTF_8))
        }

        Log.i(TAG, "Collected memory exports: ${results.size} persona groups, ${allMemories.size} memories, ${allRelations.size} relations")
        return results
    }

    private suspend fun restoreMemoryExports(memoryEntries: List<Triple<EntryType, String, ByteArray>>) {
        val db = AppContainer.getDatabase()
        val memoryDao = db.aiMemoryDao()
        val relationDao = db.knowledgeRelationDao()

        // Get existing IDs to avoid duplicates (DB restore may have already inserted them)
        val existingMemoryIds = memoryDao.getAllOnce().map { it.id }.toSet()
        val existingRelationIds = relationDao.getAll().map { it.id }.toSet()

        var memoriesInserted = 0
        var relationsInserted = 0

        for (entry in memoryEntries) {
            try {
                val exportJson = String(entry.third, Charsets.UTF_8)
                val export = json.decodeFromString(PersonaMemoryExport.serializer(), exportJson)

                // Insert memories not already present
                for (m in export.memories) {
                    if (m.id in existingMemoryIds) continue
                    val category = try { MemoryCategory.valueOf(m.category) } catch (_: Exception) { MemoryCategory.GENERAL }
                    memoryDao.insert(
                        AiMemory(
                            id = m.id,
                            fact = m.fact,
                            category = category,
                            sourceChatId = m.sourceChatId,
                            createdAt = m.createdAt,
                            updatedAt = m.updatedAt,
                            lastAccessedAt = m.lastAccessedAt,
                            accessCount = m.accessCount,
                            isSummarized = m.isSummarized,
                            summaryGroupId = m.summaryGroupId,
                            personaId = m.personaId
                            // embedding excluded — will be regenerated via backfillEmbeddings()
                        )
                    )
                    memoriesInserted++
                }

                // Insert KG relations not already present
                for (r in export.kgTriples) {
                    if (r.id in existingRelationIds) continue
                    relationDao.insert(
                        KnowledgeRelation(
                            id = r.id,
                            subjectId = r.subjectId,
                            predicate = r.predicate,
                            objectId = r.objectId,
                            confidence = r.confidence,
                            sourceFactId = r.sourceFactId,
                            createdAt = r.createdAt,
                            personaId = r.personaId
                        )
                    )
                    relationsInserted++
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to restore memory export ${entry.second}: ${e.message}")
            }
        }

        Log.i(TAG, "Memory restore: $memoriesInserted memories, $relationsInserted relations inserted")
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
            val type = if (typeOrdinal < types.size) types[typeOrdinal] else EntryType.VAULT_FILE // unknown → skip safely
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
                // Skip — device-bound Keystore encryption, not portable
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
            EntryType.MODEL_FILE -> { /* Handled separately via restoreModelEntry */ }
            EntryType.MANIFEST -> { /* Already validated */ }
            EntryType.MEMORY_EXPORT -> { /* Handled separately after DB restore */ }
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

    private fun sha256(data: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(data)
        return hash.joinToString("") { "%02x".format(it) }
    }

    private fun dirSize(dir: File): Long {
        if (!dir.exists()) return 0L
        return dir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
    }
}
