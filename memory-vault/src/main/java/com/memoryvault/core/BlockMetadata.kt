package com.memoryvault.core

import android.util.Log
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import java.util.UUID

data class BlockMetadata(
    val blockId: UUID,
    val blockType: BlockType,
    val fileOffset: Long,
    val compressedSize: Long,
    val uncompressedSize: Long,
    val timestamp: Long,
    val category: String?,
    val tags: Set<String>,
    val contentHash: String,
    val searchableText: String?
) {
    fun toBytes(): ByteArray {
        val buffer = ByteBuffer.allocate(METADATA_SIZE).apply {
            order(ByteOrder.LITTLE_ENDIAN)

            putLong(blockId.mostSignificantBits)
            putLong(blockId.leastSignificantBits)

            //Log.d("BlockMetadata", "Writing blockType ${blockType.code} at position ${position()}")
            put(blockType.code)

            putLong(fileOffset)
            putLong(compressedSize)
            putLong(uncompressedSize)
            putLong(timestamp)

            val categoryBytes = (category ?: "").toByteArray()
            putShort(categoryBytes.size.toShort())
            put(categoryBytes.copyOf(MAX_CATEGORY_SIZE))

            val tagsStr = tags.joinToString(",")
            val tagsBytes = tagsStr.toByteArray()
            putShort(tagsBytes.size.toShort())
            put(tagsBytes.copyOf(MAX_TAGS_SIZE))

            val hashBytes = contentHash.toByteArray()
            put(hashBytes.copyOf(HASH_SIZE))

            val textBytes = (searchableText ?: "").toByteArray()
            putShort(textBytes.size.toShort())
            put(textBytes.copyOf(MAX_TEXT_SIZE))
        }

        val result = buffer.array()
        //Log.d("BlockMetadata", "Serialized metadata: ${result.size} bytes, first 20 bytes: ${result.take(20).joinToString()}")
        return result
    }

    companion object {
        const val METADATA_SIZE = 512
        const val MAX_CATEGORY_SIZE = 64
        const val MAX_TAGS_SIZE = 128
        const val HASH_SIZE = 64
        const val MAX_TEXT_SIZE = 200

        fun fromBytes(bytes: ByteArray): BlockMetadata {
            require(bytes.size >= METADATA_SIZE) { "Invalid metadata size" }
            
            val buffer = ByteBuffer.wrap(bytes).apply {
                order(ByteOrder.LITTLE_ENDIAN)
            }
            
            val mostSig = buffer.long
            val leastSig = buffer.long
            val blockId = UUID(mostSig, leastSig)

            val blockTypeByte = buffer.get()
            //Log.d("BlockMetadata", "Reading blockType byte: $blockTypeByte at position ${buffer.position()}")


            val blockType = BlockType.fromCode(blockTypeByte)
            val fileOffset = buffer.long
            val compressedSize = buffer.long
            val uncompressedSize = buffer.long
            val timestamp = buffer.long
            
            val categorySize = buffer.short.toInt()
            val categoryBytes = ByteArray(MAX_CATEGORY_SIZE)
            buffer.get(categoryBytes)
            val category = if (categorySize > 0) {
                String(categoryBytes, 0, categorySize)
            } else null
            
            val tagsSize = buffer.short.toInt()
            val tagsBytes = ByteArray(MAX_TAGS_SIZE)
            buffer.get(tagsBytes)
            val tags = if (tagsSize > 0) {
                String(tagsBytes, 0, tagsSize).split(",").toSet()
            } else emptySet()
            
            val hashBytes = ByteArray(HASH_SIZE)
            buffer.get(hashBytes)
            val contentHash = String(hashBytes).trimEnd('\u0000')
            
            val textSize = buffer.short.toInt()
            val textBytes = ByteArray(MAX_TEXT_SIZE)
            buffer.get(textBytes)
            val searchableText = if (textSize > 0) {
                String(textBytes, 0, textSize)
            } else null
            
            return BlockMetadata(
                blockId = blockId,
                blockType = blockType,
                fileOffset = fileOffset,
                compressedSize = compressedSize,
                uncompressedSize = uncompressedSize,
                timestamp = timestamp,
                category = category,
                tags = tags,
                contentHash = contentHash,
                searchableText = searchableText
            )
        }

        fun calculateContentHash(data: ByteArray): String {
            val digest = MessageDigest.getInstance("SHA-256")
            val hash = digest.digest(data)
            return hash.joinToString("") { "%02x".format(it) }
        }
    }
}