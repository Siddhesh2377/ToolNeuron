package com.memoryvault.core

data class Block(
    val header: BlockHeader,
    val data: ByteArray
) {
    fun toBytes(): ByteArray {
        val headerBytes = header.toBytes()
        return headerBytes + data
    }

    fun validateChecksum(): Boolean {
        val calculated = BlockHeader.calculateChecksum(data)
        return calculated == header.checksum
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as Block
        if (header != other.header) return false
        if (!data.contentEquals(other.data)) return false
        return true
    }

    override fun hashCode(): Int {
        var result = header.hashCode()
        result = 31 * result + data.contentHashCode()
        return result
    }
}