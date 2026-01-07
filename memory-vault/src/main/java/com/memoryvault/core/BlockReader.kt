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
    
    suspend fun readHeader(offset: Long): BlockHeader {
        val headerBytes = vaultFile.readAt(offset, BlockHeader.HEADER_SIZE)
        return BlockHeader.fromBytes(headerBytes)
    }
    
    suspend fun readBlockData(offset: Long, size: Int): ByteArray {
        return vaultFile.readAt(offset + BlockHeader.HEADER_SIZE, size)
    }
}

class BlockCorruptedException(message: String) : Exception(message)