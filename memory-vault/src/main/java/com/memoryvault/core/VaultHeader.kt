package com.memoryvault.core

import java.nio.ByteBuffer
import java.nio.ByteOrder

data class VaultHeader(
    val magic: ByteArray = MAGIC_BYTES,
    val version: Short = CURRENT_VERSION,
    val indexOffset: Long = 0L,
    val indexSize: Long = 0L,
    val contentOffset: Long = HEADER_SIZE.toLong(),
    val createdTime: Long = System.currentTimeMillis(),
    val modifiedTime: Long = System.currentTimeMillis()
) {
    fun toBytes(): ByteArray {
        val buffer = ByteBuffer.allocate(HEADER_SIZE).apply {
            order(ByteOrder.LITTLE_ENDIAN)
            put(magic)
            putShort(version)
            putLong(indexOffset)
            putLong(indexSize)
            putLong(contentOffset)
            putLong(createdTime)
            putLong(modifiedTime)
        }
        return buffer.array()
    }

    fun isValid(): Boolean = magic.contentEquals(MAGIC_BYTES)

    companion object {
        const val HEADER_SIZE = 256
        const val CURRENT_VERSION: Short = 1
        val MAGIC_BYTES = "MVLT".toByteArray()

        fun fromBytes(bytes: ByteArray): VaultHeader {
            require(bytes.size >= HEADER_SIZE) { "Invalid header size" }
            
            val buffer = ByteBuffer.wrap(bytes).apply {
                order(ByteOrder.LITTLE_ENDIAN)
            }
            
            val magic = ByteArray(4)
            buffer.get(magic)
            
            return VaultHeader(
                magic = magic,
                version = buffer.short,
                indexOffset = buffer.long,
                indexSize = buffer.long,
                contentOffset = buffer.long,
                createdTime = buffer.long,
                modifiedTime = buffer.long
            )
        }
    }
}