package com.memoryvault.core

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID

enum class WALOperation(val code: Byte) {
    INSERT(1),
    DELETE(2),
    UPDATE(3);

    companion object {
        private val map = entries.associateBy { it.code }
        fun fromCode(code: Byte): WALOperation = map[code] ?: throw IllegalArgumentException("Unknown WAL operation: $code")
    }
}

data class WALEntry(
    val operation: WALOperation,
    val timestamp: Long,
    val blockId: UUID,
    val data: ByteArray?,
    val committed: Boolean = false
) {
    fun toBytes(): ByteArray {
        val dataSize = data?.size ?: 0
        val totalSize = HEADER_SIZE + dataSize
        
        val buffer = ByteBuffer.allocate(totalSize).apply {
            order(ByteOrder.LITTLE_ENDIAN)
            put(operation.code)
            putLong(timestamp)
            putLong(blockId.mostSignificantBits)
            putLong(blockId.leastSignificantBits)
            putInt(dataSize)
            put(if (committed) 1.toByte() else 0.toByte())
            
            data?.let { put(it) }
        }
        
        return buffer.array()
    }

    companion object {
        const val HEADER_SIZE = 42

        fun fromBytes(bytes: ByteArray): WALEntry {
            val buffer = ByteBuffer.wrap(bytes).apply {
                order(ByteOrder.LITTLE_ENDIAN)
            }
            
            val operation = WALOperation.fromCode(buffer.get())
            val timestamp = buffer.long
            val mostSig = buffer.long
            val leastSig = buffer.long
            val blockId = UUID(mostSig, leastSig)
            val dataSize = buffer.int
            val committed = buffer.get() == 1.toByte()
            
            val data = if (dataSize > 0) {
                ByteArray(dataSize).also { buffer.get(it) }
            } else null
            
            return WALEntry(operation, timestamp, blockId, data, committed)
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as WALEntry
        if (operation != other.operation) return false
        if (timestamp != other.timestamp) return false
        if (blockId != other.blockId) return false
        if (data != null) {
            if (other.data == null) return false
            if (!data.contentEquals(other.data)) return false
        } else if (other.data != null) return false
        if (committed != other.committed) return false
        return true
    }

    override fun hashCode(): Int {
        var result = operation.hashCode()
        result = 31 * result + timestamp.hashCode()
        result = 31 * result + blockId.hashCode()
        result = 31 * result + (data?.contentHashCode() ?: 0)
        result = 31 * result + committed.hashCode()
        return result
    }
}