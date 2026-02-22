package com.memoryvault.example

import android.content.Context
import com.memoryvault.MemoryVault
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.File

class VaultExample(
    private val context: Context,
    private val keyAlias: String = "example_key_alias"
) {
    private val vault = MemoryVault(
        context = context,
        keyAlias = keyAlias
    )
    private val scope = CoroutineScope(Dispatchers.IO)

    fun initialize() {
        scope.launch {
            vault.initialize()
        }
    }

    fun addMessage(message: String) {
        scope.launch {
            val id = vault.addMessage(
                content = message,
                category = "conversations",
                tags = setOf("important", "work")
            )
            println("Message added with ID: $id")
        }
    }

    fun addFile(file: File) {
        scope.launch {
            val id = vault.addFile(
                file = file,
                mimeType = "image/jpeg",
                category = "photos",
                tags = setOf("vacation", "2024")
            )
            println("File added with ID: $id")
        }
    }

    fun addCustomData() {
        scope.launch {
            val data = JSONObject().apply {
                put("name", "John Doe")
                put("age", 30)
                put("email", "john@example.com")
            }
            
            val id = vault.addCustomData(
                dataType = "user_profile",
                data = data,
                category = "users"
            )
            println("Custom data added with ID: $id")
        }
    }

    fun addEmbedding(vector: FloatArray, contentId: String) {
        scope.launch {
            val id = vault.addEmbedding(
                vector = vector,
                linkedContentId = contentId,
                modelName = "sentence-transformers"
            )
            println("Embedding added with ID: $id")
        }
    }

    fun searchMessages(query: String) {
        scope.launch {
            val results = vault.textSearch(query)
            println("Found ${results.size} results")
            results.forEach { item ->
                println("Item: ${item.id}, timestamp: ${item.timestamp}")
            }
        }
    }

    fun semanticSearch(queryEmbedding: FloatArray) {
        scope.launch {
            val results = vault.semanticSearch(
                embedding = queryEmbedding,
                limit = 10,
                threshold = 0.7f
            )
            println("Found ${results.size} similar items")
            results.forEach { scored ->
                println("Item: ${scored.item.id}, score: ${scored.score}")
            }
        }
    }

    fun getRecentMessages() {
        scope.launch {
            val messages = vault.getMessages(
                category = "conversations",
                limit = 50
            )
            println("Recent messages: ${messages.size}")
        }
    }

    fun deleteItem(id: String) {
        scope.launch {
            vault.delete(id)
            println("Item deleted: $id")
        }
    }

    fun updateTags(id: String, newTags: Set<String>) {
        scope.launch {
            vault.updateTags(id, newTags)
            println("Tags updated for: $id")
        }
    }

    fun performDefrag() {
        scope.launch {
            vault.defragment { progress ->
                println("Defragmentation progress: ${(progress * 100).toInt()}%")
            }
            println("Defragmentation complete")
        }
    }

    fun validateVault() {
        scope.launch {
            val report = vault.validate()
            println("Header valid: ${report.headerValid}")
            println("Total blocks: ${report.totalBlocks}")
            println("Valid blocks: ${report.validBlocks}")
            println("Corrupted blocks: ${report.corruptedBlocks.size}")
            report.recommendations.forEach { println("- $it") }
        }
    }

    fun getStatistics() {
        scope.launch {
            val stats = vault.getStats()
            println("Total items: ${stats.totalItems}")
            println("Total size: ${stats.totalSizeBytes / 1024 / 1024} MB")
            println("Wasted space: ${stats.wastedSpaceBytes / 1024} KB")
            println("Messages: ${stats.messageCount}")
            println("Files: ${stats.fileCount}")
            println("Embeddings: ${stats.embeddingCount}")
            println("Compression ratio: ${stats.compressionRatio}")
        }
    }

    fun createBackup() {
        scope.launch {
            val backupFile = File(context.getExternalFilesDir(null), "vault_backup.mvlt.gz")
            val result = vault.backup(backupFile)
            println("Backup result: ${result.message}")
            println("Duration: ${result.durationMs}ms")
        }
    }

    fun restoreBackup() {
        scope.launch {
            val backupFile = File(context.getExternalFilesDir(null), "vault_backup.mvlt.gz")
            vault.restore(backupFile)
            println("Restore complete")
        }
    }

    fun shutdown() {
        scope.launch {
            vault.close()
            println("Vault closed")
        }
    }
}