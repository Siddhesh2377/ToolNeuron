package com.memoryvault.core

class BlockReader(private val vaultFile: VaultFile) {

    suspend fun readBlock(offset: Long): Block {
        val headerBytes = vaultFile.readAt(offset, BlockHeader.HEADER_SIZE)
        val header = BlockHeader.fromBytes(headerBytes)

        val dataBytes = vaultFile.readAt(
            offset + BlockHeader.HEADER_SIZE,
            header.contentSize.toInt()
        )

        val block = Block(header, dataBytes)

        if (!block.validateChecksum()) {
            throw BlockCorruptedException("Block at offset $offset failed checksum validation")
        }

        return block
    }
}

class BlockCorruptedException(message: String) : Exception(message)
