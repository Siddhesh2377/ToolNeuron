package com.memoryvault.core

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID
import java.util.zip.CRC32

data class BlockHeader(
    val blockId: UUID,
    val blockType: BlockType,
    val contentSize: Long,
    val timestamp: Long,
    val checksum: Int,
    val compressionFlag: Boolean,
    val encryptionFlag: Boolean = false
) {
    fun toBytes(): ByteArray {
        val buffer = ByteBuffer.allocate(HEADER_SIZE).apply {
            order(ByteOrder.LITTLE_ENDIAN)
            putLong(blockId.mostSignificantBits)
            putLong(blockId.leastSignificantBits)
            put(blockType.code)
            putLong(contentSize)
            putLong(timestamp)
            putInt(checksum)
            put(if (compressionFlag) 1.toByte() else 0.toByte())
            put(if (encryptionFlag) 1.toByte() else 0.toByte())
        }
        return buffer.array()
    }

    companion object {
        const val HEADER_SIZE = 64

        fun fromBytes(bytes: ByteArray): BlockHeader {
            require(bytes.size >= HEADER_SIZE) { "Invalid block header size" }
            
            val buffer = ByteBuffer.wrap(bytes).apply {
                order(ByteOrder.LITTLE_ENDIAN)
            }
            
            val mostSig = buffer.long
            val leastSig = buffer.long
            val blockId = UUID(mostSig, leastSig)
            val blockType = BlockType.fromCode(buffer.get())
            val contentSize = buffer.long
            val timestamp = buffer.long
            val checksum = buffer.int
            val compressionFlag = buffer.get() == 1.toByte()
            val encryptionFlag = buffer.get() == 1.toByte()
            
            return BlockHeader(
                blockId = blockId,
                blockType = blockType,
                contentSize = contentSize,
                timestamp = timestamp,
                checksum = checksum,
                compressionFlag = compressionFlag,
                encryptionFlag = encryptionFlag
            )
        }

        fun calculateChecksum(data: ByteArray): Int {
            val crc = CRC32()
            crc.update(data)
            return crc.value.toInt()
        }
    }
}