package com.memoryvault.core

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import java.io.File

class DefragManager(
    private val vaultFile: VaultFile,
    private val reader: BlockReader
) {

    suspend fun defragment(
        metadata: List<BlockMetadata>,
        onProgress: (Float) -> Unit = {}
    ): DefragResult = withContext(Dispatchers.IO) {
        val tempFile = File(vaultFile.file.parent, "${vaultFile.file.name}.tmp")
        val tempVault = VaultFile(tempFile)

        try {
            tempVault.open()

            val header = VaultHeader()
            tempVault.writeAt(0, header.toBytes())

            var currentOffset = VaultHeader.HEADER_SIZE.toLong()
            val newMetadata = mutableListOf<BlockMetadata>()
            var skippedBlocks = 0

            metadata.forEachIndexed { index, meta ->
                try {
                    val block = reader.readBlock(meta.fileOffset)

                    tempVault.writeAt(currentOffset, block.toBytes())

                    val updatedMeta = meta.copy(
                        fileOffset = currentOffset,
                        compressedSize = (BlockHeader.HEADER_SIZE + block.data.size).toLong()
                    )
                    newMetadata.add(updatedMeta)

                    currentOffset += BlockHeader.HEADER_SIZE + block.data.size
                } catch (e: Exception) {
                    // Skip corrupted blocks (e.g. Unknown block type: 0) instead of aborting
                    skippedBlocks++
                }

                if (index % 100 == 0) {
                    onProgress(index.toFloat() / metadata.size)
                    yield()
                }
            }

            onProgress(1f)

            tempVault.close()
            vaultFile.close()

            vaultFile.file.delete()
            tempFile.renameTo(vaultFile.file)

            vaultFile.open()

            val oldSize = metadata.sumOf { it.compressedSize }
            val newSize = currentOffset
            val spaceReclaimed = oldSize - newSize

            DefragResult(
                success = true,
                originalSize = oldSize,
                newSize = newSize,
                spaceReclaimed = spaceReclaimed,
                blocksProcessed = newMetadata.size,
                skippedBlocks = skippedBlocks
            )
        } catch (e: Exception) {
            tempVault.close()
            tempFile.delete()
            throw e
        }
    }
}

data class DefragResult(
    val success: Boolean,
    val originalSize: Long,
    val newSize: Long,
    val spaceReclaimed: Long,
    val blocksProcessed: Int,
    val skippedBlocks: Int = 0
)