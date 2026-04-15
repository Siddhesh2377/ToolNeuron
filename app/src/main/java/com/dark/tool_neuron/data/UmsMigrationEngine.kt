package com.dark.tool_neuron.data

import android.content.Context
import android.util.Log
import com.dark.tool_neuron.database.AppDatabase
import com.dark.tool_neuron.models.vault.ChatInfo
import com.dark.tool_neuron.vault.VaultHelper
import com.dark.ums.UnifiedMemorySystem
import com.dark.tool_neuron.global.AppPaths
import java.io.File

interface MigrationProgress {
    fun onPhaseStart(phase: Int, phaseName: String, totalItems: Int)
    fun onItemComplete(phase: Int, current: Int, total: Int)
    fun onItemSkipped(phase: Int, itemId: String, reason: String)
    fun onPhaseComplete(phase: Int, migrated: Int, skipped: Int)
    fun onComplete(totalMigrated: Int, totalSkipped: Int)
    fun onFatalError(phase: Int, error: String)
}

class UmsMigrationEngine(
    private val context: Context,
    private val progress: MigrationProgress
) {
    companion object {
        private const val TAG = "UmsMigration"
    }

    private val failures = mutableListOf<String>()
    private var totalMigrated = 0
    private var totalSkipped = 0

    fun needsMigration(): Boolean {
        val roomDb = context.getDatabasePath("llm_models_database").exists()
        val vault = AppPaths.vaultFile(context).exists()
        return roomDb || vault
    }

    fun checkDiskSpace(): Boolean {
        val dataDir = context.filesDir
        val usable = dataDir.usableSpace
        val dbFile = context.getDatabasePath("llm_models_database")
        val dbSize = if (dbFile.exists()) dbFile.length() else 0L
        val vaultDir = AppPaths.memoryVault(context)
        val vaultSize = if (vaultDir.exists()) {
            vaultDir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
        } else 0L
        val needed = (dbSize + vaultSize) * 2 // 2x safety margin
        return usable > needed
    }

    suspend fun run() {
        // Guard: all repos must be non-null before migration
        if (VaultManager.modelRepo == null || VaultManager.configRepo == null ||
            VaultManager.personaRepo == null || VaultManager.memoryRepo == null ||
            VaultManager.knowledgeRepo == null || VaultManager.chatRepo == null
        ) {
            progress.onFatalError(0, "VaultManager repos not initialized — cannot migrate")
            return
        }

        try {
            migrateRoomEntities()
            migrateVaultMessages()
            verify()
        } catch (e: Exception) {
            Log.e(TAG, "Migration failed", e)
            progress.onFatalError(0, "Migration failed: ${e.message}")
        } finally {
            // Close legacy VaultHelper if it was initialized during migration
            try {
                if (VaultHelper.isInitialized()) {
                    VaultHelper.close()
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to close VaultHelper", e)
            }
        }
    }

    fun getFailures(): List<String> = failures.toList()

    // ── Phase 1: Room DB → UMS ──

    private suspend fun migrateRoomEntities() {
        val dbFile = context.getDatabasePath("llm_models_database")
        if (!dbFile.exists()) {
            Log.i(TAG, "No Room DB found, skipping phase 1")
            return
        }

        val db = AppDatabase.getDatabase(context)
        val modelDao = db.modelDao()
        val configDao = db.modelConfigDao()
        val personaDao = db.personaDao()
        val memoryDao = db.aiMemoryDao()
        val entityDao = db.knowledgeEntityDao()
        val relationDao = db.knowledgeRelationDao()

        // Capture repos with null guard — bail if VaultManager not ready
        val modelRepo = VaultManager.modelRepo ?: run {
            failures.add("VaultManager not initialized — cannot migrate"); return
        }
        val configRepo = VaultManager.configRepo ?: run {
            failures.add("VaultManager not initialized — cannot migrate"); return
        }
        val personaRepo = VaultManager.personaRepo ?: run {
            failures.add("VaultManager not initialized — cannot migrate"); return
        }
        val memoryRepo = VaultManager.memoryRepo ?: run {
            failures.add("VaultManager not initialized — cannot migrate"); return
        }
        val knowledgeRepo = VaultManager.knowledgeRepo ?: run {
            failures.add("VaultManager not initialized — cannot migrate"); return
        }

        // Models
        val models = modelDao.getAllOnce()
        progress.onPhaseStart(1, "Models", models.size)
        var migrated = 0; var skipped = 0
        for ((i, model) in models.withIndex()) {
            try {
                modelRepo.insert(model)
                migrated++
            } catch (e: Exception) {
                failures.add("Model ${model.id}: ${e.message}")
                skipped++
            }
            progress.onItemComplete(1, i + 1, models.size)
        }
        progress.onPhaseComplete(1, migrated, skipped)
        totalMigrated += migrated; totalSkipped += skipped

        // Model configs
        val configs = models.mapNotNull { configDao.getByModelId(it.id) }
        progress.onPhaseStart(2, "Model Configs", configs.size)
        migrated = 0; skipped = 0
        for ((i, config) in configs.withIndex()) {
            try {
                configRepo.insert(config)
                migrated++
            } catch (e: Exception) {
                failures.add("Config ${config.id}: ${e.message}")
                skipped++
            }
            progress.onItemComplete(2, i + 1, configs.size)
        }
        progress.onPhaseComplete(2, migrated, skipped)
        totalMigrated += migrated; totalSkipped += skipped

        // Personas
        val personas = personaDao.getAllOnce()
        progress.onPhaseStart(3, "Personas", personas.size)
        migrated = 0; skipped = 0
        for ((i, persona) in personas.withIndex()) {
            try {
                personaRepo.insert(persona)
                migrated++
            } catch (e: Exception) {
                failures.add("Persona ${persona.id}: ${e.message}")
                skipped++
            }
            progress.onItemComplete(3, i + 1, personas.size)
        }
        progress.onPhaseComplete(3, migrated, skipped)
        totalMigrated += migrated; totalSkipped += skipped

        // AI Memories
        val memories = memoryDao.getAllOnce()
        progress.onPhaseStart(4, "AI Memories", memories.size)
        migrated = 0; skipped = 0
        for ((i, memory) in memories.withIndex()) {
            try {
                memoryRepo.insert(memory)
                migrated++
            } catch (e: Exception) {
                failures.add("Memory ${memory.id}: ${e.message}")
                skipped++
            }
            progress.onItemComplete(4, i + 1, memories.size)
        }
        progress.onPhaseComplete(4, migrated, skipped)
        totalMigrated += migrated; totalSkipped += skipped

        // Knowledge entities
        val kgEntities = entityDao.getAll()
        progress.onPhaseStart(5, "Knowledge Entities", kgEntities.size)
        migrated = 0; skipped = 0
        for ((i, entity) in kgEntities.withIndex()) {
            try {
                knowledgeRepo.insertEntity(entity)
                migrated++
            } catch (e: Exception) {
                failures.add("KG Entity ${entity.id}: ${e.message}")
                skipped++
            }
            progress.onItemComplete(5, i + 1, kgEntities.size)
        }
        progress.onPhaseComplete(5, migrated, skipped)
        totalMigrated += migrated; totalSkipped += skipped

        // Knowledge relations
        val kgRelations = relationDao.getAll()
        progress.onPhaseStart(6, "Knowledge Relations", kgRelations.size)
        migrated = 0; skipped = 0
        for ((i, relation) in kgRelations.withIndex()) {
            try {
                knowledgeRepo.insertRelation(relation)
                migrated++
            } catch (e: Exception) {
                failures.add("KG Relation ${relation.id}: ${e.message}")
                skipped++
            }
            progress.onItemComplete(6, i + 1, kgRelations.size)
        }
        progress.onPhaseComplete(6, migrated, skipped)
        totalMigrated += migrated; totalSkipped += skipped
    }

    // ── Phase 2: VaultHelper → UMS ──

    private suspend fun migrateVaultMessages() {
        val vaultDir = AppPaths.vaultFile(context)
        if (!vaultDir.exists()) {
            Log.i(TAG, "No vault found, skipping phase 2")
            return
        }

        // Ensure VaultHelper is ready
        if (!VaultHelper.isInitialized()) {
            VaultHelper.initialize(context)
            if (!VaultHelper.awaitReady(30_000)) {
                progress.onFatalError(7, "VaultHelper failed to initialize")
                return
            }
        }

        val allChats: List<ChatInfo> = try {
            VaultHelper.getAllChats()
        } catch (e: Exception) {
            progress.onFatalError(7, "Failed to read chats: ${e.message}")
            return
        }

        val chatRepo = VaultManager.chatRepo ?: run {
            progress.onFatalError(7, "VaultManager chat repo not initialized"); return
        }

        progress.onPhaseStart(7, "Chat Messages", allChats.sumOf { it.messageCount })
        var migrated = 0; var skipped = 0
        var itemIndex = 0
        val totalItems = allChats.sumOf { it.messageCount }

        for (chat in allChats) {
            try {
                chatRepo.createChat(chat.chatId)

                val messages = VaultHelper.getMessagesForChat(chat.chatId)
                for (msg in messages) {
                    try {
                        // Legacy messages get no modelId/personaId (they're null by default)
                        chatRepo.addMessage(chat.chatId, msg)
                        migrated++
                    } catch (e: Exception) {
                        failures.add("Message ${msg.msgId}: ${e.message}")
                        skipped++
                    }
                    itemIndex++
                    progress.onItemComplete(7, itemIndex, totalItems)
                }
            } catch (e: Exception) {
                failures.add("Chat ${chat.chatId}: ${e.message}")
                skipped += chat.messageCount
                itemIndex += chat.messageCount
                progress.onItemComplete(7, itemIndex, totalItems)
            }
        }
        progress.onPhaseComplete(7, migrated, skipped)
        totalMigrated += migrated; totalSkipped += skipped
    }

    // ── Phase 3: Verification ──

    private fun verify() {
        val flags = VaultManager.ums.getFlags()
        VaultManager.ums.setFlags(flags or UnifiedMemorySystem.FLAG_MIGRATION_COMPLETE)
        progress.onComplete(totalMigrated, totalSkipped)
        Log.i(TAG, "Migration complete: $totalMigrated migrated, $totalSkipped skipped")
    }
}
