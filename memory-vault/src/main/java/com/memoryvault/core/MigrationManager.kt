package com.memoryvault.core

import android.util.Log
import com.memoryvault.BackupManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.security.KeyStore

class MigrationManager(
    private val vaultFile: VaultFile,
    private val reader: BlockReader,
    private val vaultDir: File
) {
    companion object {
        private const val TAG = "MigrationManager"
        private const val OLD_KEY_ALIAS = "memory_vault_master_key"
    }

    suspend fun migrate(
        newKeyAlias: String,
        onProgress: (Float) -> Unit
    ): MigrationResult = withContext(Dispatchers.IO) {
        Log.d(TAG, "Starting encryption key migration")

        var backupFile: File? = null

        try {
            // Phase 1: Create backup
            Log.d(TAG, "Phase 1: Creating backup")
            backupFile = File(vaultDir, "vault_pre_migration_${System.currentTimeMillis()}.mvlt")
            val backupManager = BackupManager(vaultFile.file)
            val backupResult = backupManager.backup(backupFile, compress = false)

            if (!backupResult.success) {
                return@withContext MigrationResult.Failure(
                    error = Exception("Backup failed: ${backupResult.message}"),
                    backupLocation = null
                )
            }

            Log.d(TAG, "Backup created at: ${backupFile.absolutePath}")

            // Phase 2: Read header and check keyVersion
            Log.d(TAG, "Phase 2: Loading current vault state")
            val headerBytes = vaultFile.readAt(0, VaultHeader.HEADER_SIZE)
            val header = VaultHeader.fromBytes(headerBytes)

            if (header.keyVersion.toInt() == 1) {
                Log.d(TAG, "Vault already migrated to new key")
                return@withContext MigrationResult.Success(
                    blocksReEncrypted = 0,
                    backupLocation = backupFile.absolutePath
                )
            }

            // Phase 3: Determine actual old key alias
            val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
            val oldKeyExists = keyStore.containsAlias(OLD_KEY_ALIAS)

            if (!oldKeyExists) {
                // OLD_KEY_ALIAS was never used — data was encrypted with newKeyAlias.
                // No re-encryption needed, just bump keyVersion.
                Log.d(TAG, "Old key alias not found in KeyStore, data already uses current key. Bumping keyVersion.")
                val updatedHeader = header.copy(
                    keyVersion = 1,
                    modifiedTime = System.currentTimeMillis()
                )
                vaultFile.writeAt(0, updatedHeader.toBytes())

                return@withContext MigrationResult.Success(
                    blocksReEncrypted = 0,
                    backupLocation = backupFile.absolutePath
                )
            }

            // Phase 4: Create encryption managers (old key exists, full re-encryption needed)
            Log.d(TAG, "Phase 4: Setting up encryption managers for re-encryption")
            val oldEM = EncryptionManager(OLD_KEY_ALIAS)
            val newEM = EncryptionManager(newKeyAlias)

            val metadata = if (header.indexOffset > 0 && header.indexSize > 0) {
                val encryptedIndexData = vaultFile.readAt(header.indexOffset, header.indexSize.toInt())
                val oldEncryptedData = EncryptedData.fromBytes(encryptedIndexData)
                val decryptedIndexData = oldEM.decrypt(oldEncryptedData)
                IndexSerializer.deserialize(decryptedIndexData)
            } else {
                emptyList()
            }

            Log.d(TAG, "Loaded ${metadata.size} blocks from index")

            // Phase 5: Re-encrypt each block
            Log.d(TAG, "Phase 5: Re-encrypting blocks")
            val totalBlocks = metadata.size

            metadata.forEachIndexed { index, meta ->
                try {
                    // Read block
                    val block = reader.readBlock(meta.fileOffset)

                    // Only re-encrypt if the block is encrypted
                    if (block.header.encryptionFlag) {
                        // Decrypt with old key
                        val oldData = EncryptedData.fromBytes(block.data)
                        val decrypted = oldEM.decrypt(oldData)

                        // Re-encrypt with new key
                        val newEncrypted = newEM.encrypt(decrypted)
                        val newData = newEncrypted.toBytes()

                        // Write back at same offset (data portion only, not header)
                        vaultFile.writeAt(meta.fileOffset + BlockHeader.HEADER_SIZE, newData)

                        // Update block header to reflect new size if changed
                        val newHeader = block.header.copy(
                            contentSize = newData.size.toLong(),
                            checksum = BlockHeader.calculateChecksum(newData)
                        )
                        vaultFile.writeAt(meta.fileOffset, newHeader.toBytes())
                    }

                    // Update progress
                    onProgress((index + 1).toFloat() / totalBlocks)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to re-encrypt block ${meta.blockId}", e)
                    throw Exception("Failed to re-encrypt block ${meta.blockId}: ${e.message}", e)
                }
            }

            // Phase 6: Re-encrypt index with new key
            Log.d(TAG, "Phase 6: Re-encrypting index")
            val newIndexData = IndexSerializer.serialize(metadata)
            val newEncryptedIndex = newEM.encrypt(newIndexData)
            val newIndexBytes = newEncryptedIndex.toBytes()

            val newIndexOffset = vaultFile.size()
            vaultFile.writeAt(newIndexOffset, newIndexBytes)

            // Phase 7: Update header with keyVersion = 1
            Log.d(TAG, "Phase 7: Updating vault header")
            val newHeader = header.copy(
                keyVersion = 1,
                indexOffset = newIndexOffset,
                indexSize = newIndexBytes.size.toLong(),
                modifiedTime = System.currentTimeMillis()
            )
            vaultFile.writeAt(0, newHeader.toBytes())

            // Phase 8: Delete old key from Keystore
            Log.d(TAG, "Phase 8: Cleaning up old key")
            if (keyStore.containsAlias(OLD_KEY_ALIAS)) {
                keyStore.deleteEntry(OLD_KEY_ALIAS)
                Log.d(TAG, "Old key deleted from Keystore")
            }

            Log.d(TAG, "Migration completed successfully")
            MigrationResult.Success(
                blocksReEncrypted = totalBlocks,
                backupLocation = backupFile.absolutePath
            )

        } catch (e: Exception) {
            Log.e(TAG, "Migration failed", e)
            MigrationResult.Failure(
                error = e,
                backupLocation = backupFile?.absolutePath
            )
        }
    }

}
