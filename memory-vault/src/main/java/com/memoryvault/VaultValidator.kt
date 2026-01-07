package com.memoryvault

import com.memoryvault.core.BlockHeader
import com.memoryvault.core.BlockMetadata
import com.memoryvault.core.BlockReader
import com.memoryvault.core.VaultFile
import com.memoryvault.core.VaultHeader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class VaultValidator(
    private val vaultFile: VaultFile,
    private val reader: BlockReader
) {
    
    suspend fun validate(): ValidationReport = withContext(Dispatchers.IO) {
        val headerBytes = vaultFile.readAt(0, VaultHeader.HEADER_SIZE)
        val header = VaultHeader.fromBytes(headerBytes)
        val headerValid = header.isValid()
        
        val corruptedBlocks = mutableListOf<CorruptedBlock>()
        var totalBlocks = 0
        var validBlocks = 0
        
        var currentOffset = header.contentOffset
        val fileSize = vaultFile.size()
        
        while (currentOffset < fileSize && currentOffset < header.indexOffset) {
            try {
                val block = reader.readBlock(currentOffset)
                totalBlocks++
                
                if (block.validateChecksum()) {
                    validBlocks++
                } else {
                    corruptedBlocks.add(
                        CorruptedBlock(currentOffset, "Checksum validation failed")
                    )
                }
                
                currentOffset += BlockHeader.HEADER_SIZE + block.data.size
            } catch (e: Exception) {
                corruptedBlocks.add(
                    CorruptedBlock(currentOffset, e.message ?: "Unknown error")
                )
                break
            }
        }
        
        val indexValid = try {
            if (header.indexOffset > 0 && header.indexSize > 0) {
                val indexData = vaultFile.readAt(header.indexOffset, header.indexSize.toInt())
                indexData.size == header.indexSize.toInt()
            } else {
                false
            }
        } catch (e: Exception) {
            false
        }
        
        val canRecover = validBlocks > 0
        
        val recommendations = mutableListOf<String>()
        if (corruptedBlocks.isNotEmpty()) {
            recommendations.add("Found ${corruptedBlocks.size} corrupted blocks")
            recommendations.add("Consider running defragmentation to remove corrupted data")
        }
        if (!indexValid) {
            recommendations.add("Index is corrupted or missing")
            recommendations.add("Rebuild index from valid blocks")
        }
        if (validBlocks == totalBlocks && headerValid && indexValid) {
            recommendations.add("Vault is healthy")
        }
        
        ValidationReport(
            headerValid = headerValid,
            totalBlocks = totalBlocks,
            validBlocks = validBlocks,
            corruptedBlocks = corruptedBlocks,
            indexValid = indexValid,
            canRecover = canRecover,
            recommendations = recommendations
        )
    }
    
    suspend fun rebuildIndex(): List<BlockMetadata> = withContext(Dispatchers.IO) {
        val headerBytes = vaultFile.readAt(0, VaultHeader.HEADER_SIZE)
        val header = VaultHeader.fromBytes(headerBytes)
        
        val metadata = mutableListOf<BlockMetadata>()
        var currentOffset = header.contentOffset
        val fileSize = vaultFile.size()
        
        while (currentOffset < fileSize) {
            try {
                val block = reader.readBlock(currentOffset)
                
                if (block.validateChecksum()) {
                    val meta = BlockMetadata(
                        blockId = block.header.blockId,
                        blockType = block.header.blockType,
                        fileOffset = currentOffset,
                        compressedSize = (BlockHeader.HEADER_SIZE + block.data.size).toLong(),
                        uncompressedSize = block.header.contentSize,
                        timestamp = block.header.timestamp,
                        category = null,
                        tags = emptySet(),
                        contentHash = BlockMetadata.calculateContentHash(block.data),
                        searchableText = null
                    )
                    metadata.add(meta)
                }
                
                currentOffset += BlockHeader.HEADER_SIZE + block.data.size
            } catch (e: Exception) {
                break
            }
        }
        
        metadata
    }
}