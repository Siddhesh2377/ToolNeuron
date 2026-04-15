package com.memoryvault.core

import java.util.UUID

class BlockWriter(private val vaultFile: VaultFile) {

    suspend fun writeBlockWithId(
        blockId: UUID,
        blockType: BlockType,
        data: ByteArray,
        compressed: Boolean = false,
        encrypted: Boolean = false,
        timestamp: Long = System.currentTimeMillis()
    ): BlockWriteResult {
        val checksum = BlockHeader.calculateChecksum(data)

        val header = BlockHeader(
            blockId = blockId,
            blockType = blockType,
            contentSize = data.size.toLong(),
            timestamp = timestamp,
            checksum = checksum,
            compressionFlag = compressed,
            encryptionFlag = encrypted
        )

        val block = Block(header, data)
        val offset = vaultFile.append(block.toBytes())

        return BlockWriteResult(
            blockId = blockId,
            offset = offset,
            size = (BlockHeader.HEADER_SIZE + data.size).toLong(),
            timestamp = timestamp
        )
    }
}

data class BlockWriteResult(
    val blockId: UUID,
    val offset: Long,
    val size: Long,
    val timestamp: Long
)
