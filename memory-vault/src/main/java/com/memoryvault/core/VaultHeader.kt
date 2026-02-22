package com.memoryvault.core

import java.nio.ByteBuffer
import java.nio.ByteOrder

data class VaultHeader(
    val magic: ByteArray = MAGIC_BYTES,
    val version: Short = CURRENT_VERSION,
    val keyVersion: Short = 0,  // 0 = old key, 1 = new key
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
            putShort(keyVersion)
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
            val version = buffer.short

            // Save position to potentially re-read
            val afterVersionPos = buffer.position()

            // Try reading as new format first
            val possibleKeyVersion = buffer.short
            val indexOffset = buffer.long
            val indexSize = buffer.long
            val contentOffset = buffer.long

            // Validate if this looks like a new format header
            // New format should have: keyVersion in [0,1], contentOffset = 256
            val looksLikeNewFormat = possibleKeyVersion in 0..1 && contentOffset == HEADER_SIZE.toLong()

            if (looksLikeNewFormat) {
                // Continue reading rest of new format
                val createdTime = buffer.long
                val modifiedTime = buffer.long

                return VaultHeader(
                    magic = magic,
                    version = version,
                    keyVersion = possibleKeyVersion,
                    indexOffset = indexOffset,
                    indexSize = indexSize,
                    contentOffset = contentOffset,
                    createdTime = createdTime,
                    modifiedTime = modifiedTime
                )
            } else {
                // Old format: no keyVersion field, re-read from after version
                buffer.position(afterVersionPos)

                return VaultHeader(
                    magic = magic,
                    version = version,
                    keyVersion = 0,  // Default to 0 for old format (needs migration)
                    indexOffset = buffer.long,
                    indexSize = buffer.long,
                    contentOffset = buffer.long,
                    createdTime = buffer.long,
                    modifiedTime = buffer.long
                )
            }
        }
    }
}