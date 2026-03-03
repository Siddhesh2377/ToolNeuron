package com.dark.ums

import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.os.StatFs
import android.util.Log
import java.io.File

interface MigrationProgress {
    fun onPhaseStart(phase: Int, phaseName: String, totalItems: Int)
    fun onItemComplete(phase: Int, current: Int, total: Int)
    fun onItemSkipped(phase: Int, itemId: String, reason: String)
    fun onPhaseComplete(phase: Int, migrated: Int, skipped: Int)
    fun onComplete(totalMigrated: Int, totalSkipped: Int)
    fun onFatalError(phase: Int, error: String)
}

class MigrationEngine(
    private val context: Context,
    private val ums: UnifiedMemorySystem,
    private val progress: MigrationProgress
) {
    companion object {
        private const val TAG = "MigrationEngine"
        private const val ROOM_DB_NAME = "llm_models_database"
    }

    private var totalMigrated = 0
    private var totalSkipped = 0
    private val failures = mutableListOf<String>()

    fun hasOldData(): Boolean {
        val roomDb = context.getDatabasePath(ROOM_DB_NAME).exists()
        val vault = File(context.filesDir, "memory_vault/vault.mvlt").exists()
        val dataStore = File(context.filesDir, "datastore/app_settings.preferences_pb").exists()
        Log.i(TAG, "Detection: roomDb=$roomDb, vault=$vault, dataStore=$dataStore")
        return roomDb || vault || dataStore
    }

    fun checkDiskSpace(): Boolean {
        val filesDir = context.filesDir
        val stat = StatFs(filesDir.absolutePath)
        val availableBytes = stat.availableBytes

        var requiredBytes = 0L
        val roomDbFile = context.getDatabasePath(ROOM_DB_NAME)
        if (roomDbFile.exists()) requiredBytes += roomDbFile.length()
        val vaultFile = File(context.filesDir, "memory_vault/vault.mvlt")
        if (vaultFile.exists()) requiredBytes += vaultFile.length()

        // 20% overhead for encryption expansion
        requiredBytes = (requiredBytes * 1.2).toLong()

        val ok = availableBytes > requiredBytes
        Log.i(TAG, "Disk space check: available=${availableBytes / 1024}KB, " +
            "required=${requiredBytes / 1024}KB, ok=$ok")
        return ok
    }

    fun run() {
        Log.i(TAG, "Starting migration")

        try {
            // Phase 1: Room DB
            migrateRoomDb()

            // Phase 2: MemoryVault
            migrateMemoryVault()

            // Phase 3: DataStore
            migrateDataStore()

            // Phase 4: Verify
            verify()

            progress.onComplete(totalMigrated, totalSkipped)
            Log.i(TAG, "Migration complete: migrated=$totalMigrated, skipped=$totalSkipped")
        } catch (e: Exception) {
            Log.e(TAG, "Fatal migration error: ${e.message}", e)
            progress.onFatalError(0, e.message ?: "Unknown error")
        }
    }

    fun getFailures(): List<String> = failures

    // -------------------------------------------------------------------------
    // Phase 1: Room DB
    // -------------------------------------------------------------------------

    private fun migrateRoomDb() {
        val dbFile = context.getDatabasePath(ROOM_DB_NAME)
        if (!dbFile.exists()) {
            Log.i(TAG, "No Room DB found, skipping Phase 1")
            return
        }

        val db = try {
            SQLiteDatabase.openDatabase(dbFile.absolutePath, null, SQLiteDatabase.OPEN_READONLY)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open Room DB: ${e.message}")
            progress.onFatalError(1, "Failed to open database: ${e.message}")
            return
        }

        try {
            val tables = listOf(
                TableMigration("models", "models") { c -> migrateModelsRow(c) },
                TableMigration("model_config", "model_config") { c -> migrateModelConfigRow(c) },
                TableMigration("installed_rags", "rags") { c -> migrateRagsRow(c) },
                TableMigration("personas", "personas") { c -> migratePersonasRow(c) },
                TableMigration("ai_memories", "memories") { c -> migrateMemoriesRow(c) },
                TableMigration("knowledge_entities", "knowledge_entities") { c -> migrateKnowledgeEntitiesRow(c) },
                TableMigration("knowledge_relations", "knowledge_relations") { c -> migrateKnowledgeRelationsRow(c) }
            )

            val totalRows = tables.sumOf { countTable(db, it.tableName) }
            progress.onPhaseStart(1, "Room Database", totalRows)

            var globalCurrent = 0
            for (table in tables) {
                globalCurrent = migrateTable(db, table, 1, globalCurrent, totalRows)
            }

            progress.onPhaseComplete(1, totalMigrated, totalSkipped)
        } finally {
            db.close()
        }
    }

    private data class TableMigration(
        val tableName: String,
        val collectionName: String,
        val rowMapper: (Cursor) -> UmsRecord?
    )

    private fun countTable(db: SQLiteDatabase, table: String): Int {
        return try {
            val cursor = db.rawQuery("SELECT COUNT(*) FROM $table", null)
            cursor.use { if (it.moveToFirst()) it.getInt(0) else 0 }
        } catch (e: Exception) {
            Log.w(TAG, "Table '$table' not found (older DB version): ${e.message}")
            0
        }
    }

    private fun migrateTable(
        db: SQLiteDatabase, table: TableMigration,
        phase: Int, startCurrent: Int, totalRows: Int
    ): Int {
        var current = startCurrent
        val existingCount = ums.count(table.collectionName)
        val expectedCount = countTable(db, table.tableName)

        if (expectedCount == 0) {
            Log.i(TAG, "[${table.tableName}] Empty table, skipping")
            return current
        }

        if (existingCount >= expectedCount) {
            Log.i(TAG, "[${table.tableName}] Already migrated ($existingCount records), skipping")
            current += expectedCount
            return current
        }

        ums.ensureCollection(table.collectionName)
        Log.i(TAG, "[${table.tableName}] Reading $expectedCount rows...")

        val cursor = try {
            db.rawQuery("SELECT * FROM ${table.tableName}", null)
        } catch (e: Exception) {
            Log.e(TAG, "[${table.tableName}] Query failed: ${e.message}")
            return current
        }

        cursor.use {
            var migrated = 0
            var skipped = 0
            while (it.moveToNext()) {
                current++
                try {
                    val record = table.rowMapper(it)
                    if (record != null) {
                        ums.put(table.collectionName, record)
                        migrated++
                        totalMigrated++
                    } else {
                        skipped++
                        totalSkipped++
                        val id = safeGetString(it, "id") ?: "unknown"
                        val reason = "Row mapper returned null"
                        failures.add("[${table.tableName}] id=$id: $reason")
                        progress.onItemSkipped(phase, id, reason)
                    }
                } catch (e: Exception) {
                    skipped++
                    totalSkipped++
                    val id = safeGetString(it, "id") ?: "row ${it.position}"
                    val reason = e.message ?: "Unknown error"
                    failures.add("[${table.tableName}] id=$id: $reason")
                    progress.onItemSkipped(phase, id, reason)
                    Log.w(TAG, "[${table.tableName}] Skipped $id: $reason")
                }
                progress.onItemComplete(phase, current, totalRows)
            }
            Log.i(TAG, "[${table.tableName}] Wrote $migrated records, skipped $skipped")
        }
        return current
    }

    // --- Row mappers ---

    private fun migrateModelsRow(c: Cursor): UmsRecord? {
        val id = safeGetString(c, "id") ?: return null
        return UmsRecord.create()
            .putString(1, id)
            .putString(2, safeGetString(c, "model_name") ?: "")
            .putString(3, safeGetString(c, "model_path") ?: "")
            .putString(4, safeGetString(c, "path_type") ?: "")
            .putString(5, safeGetString(c, "provider_type") ?: "")
            .putLong(6, safeGetLong(c, "file_size"))
            .putBool(7, safeGetInt(c, "is_active") != 0)
            .build()
    }

    private fun migrateModelConfigRow(c: Cursor): UmsRecord? {
        val id = safeGetString(c, "id") ?: return null
        return UmsRecord.create()
            .putString(1, id)
            .putString(2, safeGetString(c, "model_id") ?: "")
            .putString(3, safeGetString(c, "model_loading_params") ?: "")
            .putString(4, safeGetString(c, "model_inference_params") ?: "")
            .build()
    }

    private fun migrateRagsRow(c: Cursor): UmsRecord? {
        val id = safeGetString(c, "id") ?: return null
        return UmsRecord.create()
            .putString(1, id)
            .putString(2, safeGetString(c, "name") ?: "")
            .putString(3, safeGetString(c, "description") ?: "")
            .putString(4, safeGetString(c, "source_type") ?: "")
            .putString(5, safeGetString(c, "file_path") ?: "")
            .putInt(6, safeGetInt(c, "node_count"))
            .putInt(7, safeGetInt(c, "embedding_dimension"))
            .putString(8, safeGetString(c, "embedding_model") ?: "")
            .putString(9, safeGetString(c, "domain") ?: "")
            .putString(10, safeGetString(c, "language") ?: "")
            .putString(11, safeGetString(c, "version") ?: "")
            .putString(12, safeGetString(c, "tags") ?: "")
            .putString(13, safeGetString(c, "status") ?: "")
            .putBool(14, safeGetInt(c, "is_enabled") != 0)
            .putTimestamp(15, safeGetLong(c, "created_at"))
            .putTimestamp(16, safeGetLong(c, "updated_at"))
            .putTimestamp(17, safeGetLong(c, "last_loaded_at"))
            .putLong(18, safeGetLong(c, "size_bytes"))
            .putString(19, safeGetString(c, "metadata_json") ?: "")
            .putBool(20, safeGetInt(c, "is_encrypted") != 0)
            .putInt(21, safeGetInt(c, "loading_mode"))
            .putBool(22, safeGetInt(c, "has_admin_access") != 0)
            .build()
    }

    private fun migratePersonasRow(c: Cursor): UmsRecord? {
        val id = safeGetString(c, "id") ?: return null
        return UmsRecord.create()
            .putString(1, id)
            .putString(2, safeGetString(c, "name") ?: "")
            .putString(3, safeGetString(c, "avatar") ?: "")
            .putString(4, safeGetString(c, "system_prompt") ?: "")
            .putString(5, safeGetString(c, "greeting") ?: "")
            .putBool(6, safeGetInt(c, "is_default") != 0)
            .putTimestamp(7, safeGetLong(c, "created_at"))
            .putString(8, safeGetString(c, "description") ?: "")
            .putString(9, safeGetString(c, "personality") ?: "")
            .putString(10, safeGetString(c, "scenario") ?: "")
            .putString(11, safeGetString(c, "example_messages") ?: "")
            .putString(12, safeGetString(c, "alternate_greetings") ?: "")
            .putString(13, safeGetString(c, "tags") ?: "")
            .putString(14, safeGetString(c, "avatar_uri") ?: "")
            .putString(15, safeGetString(c, "creator_notes") ?: "")
            .putString(16, safeGetString(c, "sampling_profile") ?: "")
            .putString(17, safeGetString(c, "control_vectors") ?: "")
            .build()
    }

    private fun migrateMemoriesRow(c: Cursor): UmsRecord? {
        val id = safeGetString(c, "id") ?: return null
        val categoryStr = safeGetString(c, "category") ?: "GENERAL"
        val categoryInt = when (categoryStr) {
            "PERSONAL" -> 0; "PREFERENCE" -> 1; "WORK" -> 2
            "INTEREST" -> 3; else -> 4
        }
        val builder = UmsRecord.create()
            .putString(1, id)
            .putString(2, safeGetString(c, "fact") ?: "")
            .putInt(3, categoryInt)
            .putString(4, safeGetString(c, "source_chat_id") ?: "")
            .putTimestamp(5, safeGetLong(c, "created_at"))
            .putTimestamp(6, safeGetLong(c, "updated_at"))
            .putTimestamp(7, safeGetLong(c, "last_accessed_at"))
            .putInt(8, safeGetInt(c, "access_count"))

        val embedding = safeGetBlob(c, "embedding")
        if (embedding != null) builder.putBytes(9, embedding)

        builder.putBool(10, safeGetInt(c, "is_summarized") != 0)
            .putString(11, safeGetString(c, "summary_group_id") ?: "")
            .putString(12, safeGetString(c, "persona_id") ?: "")

        return builder.build()
    }

    private fun migrateKnowledgeEntitiesRow(c: Cursor): UmsRecord? {
        val id = safeGetString(c, "id") ?: return null
        val builder = UmsRecord.create()
            .putString(1, id)
            .putString(2, safeGetString(c, "name") ?: "")
            .putString(3, safeGetString(c, "type") ?: "")

        val embedding = safeGetBlob(c, "embedding")
        if (embedding != null) builder.putBytes(4, embedding)

        builder.putTimestamp(5, safeGetLong(c, "first_seen"))
            .putTimestamp(6, safeGetLong(c, "last_seen"))
            .putInt(7, safeGetInt(c, "mention_count"))

        return builder.build()
    }

    private fun migrateKnowledgeRelationsRow(c: Cursor): UmsRecord? {
        val id = safeGetString(c, "id") ?: return null
        return UmsRecord.create()
            .putString(1, id)
            .putString(2, safeGetString(c, "subject_id") ?: "")
            .putString(3, safeGetString(c, "predicate") ?: "")
            .putString(4, safeGetString(c, "object_id") ?: "")
            .putFloat(5, safeGetFloat(c, "confidence"))
            .putString(6, safeGetString(c, "source_fact_id") ?: "")
            .putTimestamp(7, safeGetLong(c, "created_at"))
            .putString(8, safeGetString(c, "persona_id") ?: "")
            .build()
    }

    // --- Safe cursor helpers (handle missing columns in older DB versions) ---

    private fun safeGetString(c: Cursor, col: String): String? {
        return try {
            val idx = c.getColumnIndex(col)
            if (idx >= 0 && !c.isNull(idx)) c.getString(idx) else null
        } catch (e: Exception) { null }
    }

    private fun safeGetInt(c: Cursor, col: String): Int {
        return try {
            val idx = c.getColumnIndex(col)
            if (idx >= 0 && !c.isNull(idx)) c.getInt(idx) else 0
        } catch (e: Exception) { 0 }
    }

    private fun safeGetLong(c: Cursor, col: String): Long {
        return try {
            val idx = c.getColumnIndex(col)
            if (idx >= 0 && !c.isNull(idx)) c.getLong(idx) else 0L
        } catch (e: Exception) { 0L }
    }

    private fun safeGetFloat(c: Cursor, col: String): Float {
        return try {
            val idx = c.getColumnIndex(col)
            if (idx >= 0 && !c.isNull(idx)) c.getFloat(idx) else 0f
        } catch (e: Exception) { 0f }
    }

    private fun safeGetBlob(c: Cursor, col: String): ByteArray? {
        return try {
            val idx = c.getColumnIndex(col)
            if (idx >= 0 && !c.isNull(idx)) c.getBlob(idx) else null
        } catch (e: Exception) { null }
    }

    // -------------------------------------------------------------------------
    // Phase 2: MemoryVault
    // -------------------------------------------------------------------------

    private fun migrateMemoryVault() {
        val vaultFile = File(context.filesDir, "memory_vault/vault.mvlt")
        if (!vaultFile.exists()) {
            Log.i(TAG, "No MemoryVault found, skipping Phase 2")
            return
        }

        val reader = VaultReader(vaultFile)
        if (!reader.open()) {
            Log.e(TAG, "Failed to open MemoryVault, skipping Phase 2")
            val reason = "MemoryVault could not be opened"
            failures.add("[vault] $reason")
            progress.onPhaseStart(2, "MemoryVault", 0)
            progress.onPhaseComplete(2, 0, 0)
            return
        }

        val blocks = reader.blocks().filter {
            it.blockType != VaultReader.BlockType.REFERENCE &&
            it.blockType != VaultReader.BlockType.METADATA
        }

        progress.onPhaseStart(2, "MemoryVault", blocks.size)

        // Ensure collections
        ums.ensureCollection("vault_messages")
        ums.ensureCollection("vault_files")
        ums.ensureCollection("vault_embeddings")
        ums.ensureCollection("vault_custom_data")

        var migrated = 0
        var skipped = 0

        for ((i, meta) in blocks.withIndex()) {
            val content = reader.readBlock(meta)
            if (content == null) {
                skipped++
                totalSkipped++
                val reason = "Block unreadable (CRC/decrypt/decompress failure)"
                failures.add("[vault] block=${meta.blockId}: $reason")
                progress.onItemSkipped(2, meta.blockId.toString(), reason)
                progress.onItemComplete(2, i + 1, blocks.size)
                continue
            }

            val collectionName = when (meta.blockType) {
                VaultReader.BlockType.MESSAGE -> "vault_messages"
                VaultReader.BlockType.FILE -> "vault_files"
                VaultReader.BlockType.EMBEDDING -> "vault_embeddings"
                VaultReader.BlockType.CUSTOM_DATA -> "vault_custom_data"
                else -> continue
            }

            val record = UmsRecord.create()
                .putString(1, meta.blockId.toString())
                .putBytes(2, content.data)
                .putString(3, meta.category)
                .putString(4, meta.tags)
                .putTimestamp(5, meta.timestamp)
                .putString(6, meta.contentHash)
                .putString(7, meta.searchableText)
                .build()

            ums.put(collectionName, record)
            migrated++
            totalMigrated++
            progress.onItemComplete(2, i + 1, blocks.size)

            if ((i + 1) % 50 == 0) {
                Log.i(TAG, "[vault] Progress: ${i + 1}/${blocks.size}")
            }
        }

        Log.i(TAG, "[vault] Wrote $migrated blocks, skipped $skipped")
        progress.onPhaseComplete(2, migrated, skipped)
    }

    // -------------------------------------------------------------------------
    // Phase 3: DataStore settings
    // -------------------------------------------------------------------------

    private fun migrateDataStore() {
        val dataStoreDir = File(context.filesDir, "datastore")
        if (!dataStoreDir.exists()) {
            Log.i(TAG, "No DataStore found, skipping Phase 3")
            return
        }

        ums.ensureCollection("settings")

        val settingsKeys = listOf(
            "app.streaming_enabled", "app.chat_memory_enabled",
            "app.tool_calling_enabled", "app.tool_calling_bypass_enabled",
            "app.image_blur_enabled", "app.load_tts_on_start",
            "app.code_highlight_enabled", "app.ai_memory_enabled",
            "app.uncensored_enabled", "app.last_chat_id",
            "app.last_model_id", "app.active_persona_id",
            "app.selected_theme_id",
            "setup.setup_skipped", "setup.setup_completed",
            "repo.model_repositories", "repo.deleted_default_repo_ids",
            "tts.voice", "tts.speed", "tts.steps",
            "tts.language", "tts.auto_speak", "tts.use_nnapi"
        )

        progress.onPhaseStart(3, "DataStore Settings", settingsKeys.size)

        Log.i(TAG, "DataStore settings will be lazily migrated on first access (${settingsKeys.size} keys)")
        progress.onPhaseComplete(3, 0, 0)
    }

    // -------------------------------------------------------------------------
    // Phase 4: Verify and finalize
    // -------------------------------------------------------------------------

    private fun verify() {
        progress.onPhaseStart(4, "Verification", 0)

        val collections = listOf(
            "models", "model_config", "rags", "personas", "memories",
            "knowledge_entities", "knowledge_relations",
            "vault_messages", "vault_files", "vault_embeddings", "vault_custom_data",
            "settings"
        )

        for (name in collections) {
            val count = ums.count(name)
            if (count > 0) {
                Log.i(TAG, "[verify] $name: $count records")
            }
        }

        // Set migration_complete flag
        ums.setFlags(ums.getFlags() or UnifiedMemorySystem.FLAG_MIGRATION_COMPLETE)
        Log.i(TAG, "Set migration_complete flag in manifest")

        progress.onPhaseComplete(4, 0, 0)
    }
}
