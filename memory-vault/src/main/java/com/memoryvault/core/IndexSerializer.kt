package com.memoryvault.core

import android.util.Log
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

object IndexSerializer {
    private val MAGIC_BYTES = "MIDX".toByteArray()
    private const val VERSION: Short = 1
    private const val HEADER_SIZE = 64

    suspend fun serialize(metadata: List<BlockMetadata>): ByteArray {
        val output = ByteArrayOutputStream()

        // Write header WITHOUT padding - just the actual data
        val header = ByteBuffer.allocate(10).apply {  // Changed from HEADER_SIZE to 10
            order(ByteOrder.LITTLE_ENDIAN)
            put(MAGIC_BYTES)      // 4 bytes
            putShort(VERSION)     // 2 bytes
            putInt(metadata.size) // 4 bytes
        }
        output.write(header.array())

        metadata.forEach { meta ->
            output.write(meta.toBytes())
        }

        return output.toByteArray()
    }

    suspend fun deserialize(data: ByteArray): List<BlockMetadata> {
        val buffer = ByteBuffer.wrap(data).apply {
            order(ByteOrder.LITTLE_ENDIAN)
        }

        val magic = ByteArray(4)
        buffer.get(magic)
        require(magic.contentEquals(MAGIC_BYTES)) { "Invalid index magic bytes" }

        val version = buffer.short
        require(version == VERSION) { "Unsupported index version: $version" }

        val count = buffer.int

        Log.d("IndexSerializer", "After header, buffer position: ${buffer.position()}, will read $count items")

        // DON'T skip - continue from current position (10)
        val metadata = mutableListOf<BlockMetadata>()

        repeat(count) {
            val metaBytes = ByteArray(BlockMetadata.METADATA_SIZE)
            buffer.get(metaBytes)

            //Log.d("IndexSerializer", "Read metadata $it, buffer position now: ${buffer.position()}")

            metadata.add(BlockMetadata.fromBytes(metaBytes))
        }

        return metadata
    }
}