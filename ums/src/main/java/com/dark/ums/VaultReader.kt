package com.dark.ums

import android.util.Log
import net.jpountz.lz4.LZ4Factory
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.KeyStore
import java.util.UUID
import java.util.zip.CRC32
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Standalone reader for old MemoryVault vault.mvlt binary format.
 * Decrypts using Android Keystore, decompresses with LZ4.
 */
class VaultReader(private val vaultFile: File) {

    companion object {
        private const val TAG = "VaultReader"
        private const val VAULT_HEADER_SIZE = 256
        private const val BLOCK_HEADER_SIZE = 64
        private const val INDEX_ENTRY_SIZE = 512
        private const val INDEX_HEADER_SIZE = 10
        private const val GCM_TAG_BITS = 128
        private const val IV_SIZE = 12
        private val VAULT_MAGIC = byteArrayOf(0x4D, 0x56, 0x4C, 0x54) // "MVLT"
        private val INDEX_MAGIC = byteArrayOf(0x4D, 0x49, 0x44, 0x58) // "MIDX"
        private const val OLD_KEY_ALIAS = BuildConfig.KEY_ALIAS
    }

    data class VaultHeader(
        val version: Int,
        val keyVersion: Int,
        val indexOffset: Long,
        val indexSize: Long,
        val contentOffset: Long,
        val createdTime: Long,
        val modifiedTime: Long
    )

    enum class BlockType(val code: Int) {
        MESSAGE(1), FILE(2), CUSTOM_DATA(3), EMBEDDING(4), REFERENCE(5), METADATA(6);
        companion object {
            fun fromCode(code: Int) = entries.firstOrNull { it.code == code }
        }
    }

    data class BlockMetadata(
        val blockId: UUID,
        val blockType: BlockType,
        val fileOffset: Long,
        val compressedSize: Long,
        val uncompressedSize: Long,
        val timestamp: Long,
        val category: String,
        val tags: String,
        val contentHash: String,
        val searchableText: String
    )

    data class BlockContent(
        val metadata: BlockMetadata,
        val data: ByteArray
    )

    private var header: VaultHeader? = null
    private var index: List<BlockMetadata> = emptyList()
    private var secretKey: SecretKey? = null
    private val lz4 = LZ4Factory.fastestInstance()

    /**
     * Opens the vault file: reads header, loads keystore key, decrypts index.
     * Returns true if successful, false if vault is unreadable.
     */
    fun open(): Boolean {
        if (!vaultFile.exists()) {
            Log.e(TAG, "Vault file not found: ${vaultFile.absolutePath}")
            return false
        }
        if (vaultFile.length() < VAULT_HEADER_SIZE) {
            Log.e(TAG, "Vault file too small: ${vaultFile.length()} bytes")
            return false
        }

        val raf = RandomAccessFile(vaultFile, "r")
        try {
            // Read header
            header = readHeader(raf) ?: return false
            val h = header!!

            Log.i(TAG, "Vault header: version=${h.version}, keyVersion=${h.keyVersion}, " +
                "indexOffset=${h.indexOffset}, indexSize=${h.indexSize}")

            // Load keystore key
            val alias = if (h.keyVersion == 0) OLD_KEY_ALIAS else OLD_KEY_ALIAS
            secretKey = loadKeystoreKey(alias)
            if (secretKey == null) {
                Log.e(TAG, "Keystore key not found for alias '$alias'")
                return false
            }

            // Read and decrypt index
            if (h.indexSize <= 0 || h.indexOffset <= 0) {
                Log.w(TAG, "No index in vault (offset=${h.indexOffset}, size=${h.indexSize})")
                index = emptyList()
                return true
            }

            val encryptedIndex = ByteArray(h.indexSize.toInt())
            raf.seek(h.indexOffset)
            raf.readFully(encryptedIndex)

            val decryptedIndex = decrypt(encryptedIndex)
            if (decryptedIndex == null) {
                Log.e(TAG, "Failed to decrypt vault index")
                return false
            }

            index = parseIndex(decryptedIndex)
            Log.i(TAG, "Loaded ${index.size} blocks from index")
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open vault: ${e.message}", e)
            return false
        } finally {
            raf.close()
        }
    }

    /**
     * Returns all block metadata entries (for iteration).
     */
    fun blockCount(): Int = index.size

    fun blocks(): List<BlockMetadata> = index

    /**
     * Reads and decrypts a single block by its metadata.
     * Returns null if the block is unreadable.
     */
    fun readBlock(meta: BlockMetadata): BlockContent? {
        val raf = RandomAccessFile(vaultFile, "r")
        try {
            // Read block header
            raf.seek(meta.fileOffset)
            val headerBytes = ByteArray(BLOCK_HEADER_SIZE)
            raf.readFully(headerBytes)
            val bb = ByteBuffer.wrap(headerBytes).order(ByteOrder.LITTLE_ENDIAN)

            bb.getLong() // blockId MSB
            bb.getLong() // blockId LSB
            bb.get()     // blockType
            val contentSize = bb.getLong()
            bb.getLong()     // timestamp
            val checksum = bb.getInt()
            val compressionFlag = bb.get().toInt()
            val encryptionFlag = bb.get().toInt()

            // Read block data
            val blockData = ByteArray(contentSize.toInt())
            raf.seek(meta.fileOffset + BLOCK_HEADER_SIZE)
            raf.readFully(blockData)

            // Validate CRC32
            val crc = CRC32()
            crc.update(blockData)
            if (crc.value.toInt() != checksum) {
                Log.w(TAG, "CRC32 mismatch for block ${meta.blockId}: " +
                    "expected=$checksum, got=${crc.value.toInt()}")
                return null
            }

            // Decrypt
            var processed = blockData
            if (encryptionFlag == 1) {
                processed = decrypt(processed) ?: run {
                    Log.w(TAG, "Failed to decrypt block ${meta.blockId}")
                    return null
                }
            }

            // Decompress
            if (compressionFlag == 1) {
                processed = decompress(processed, meta.uncompressedSize.toInt()) ?: run {
                    Log.w(TAG, "Failed to decompress block ${meta.blockId}")
                    return null
                }
            }

            return BlockContent(meta, processed)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to read block ${meta.blockId}: ${e.message}")
            return null
        } finally {
            raf.close()
        }
    }

    // --- Private helpers ---

    private fun readHeader(raf: RandomAccessFile): VaultHeader? {
        val bytes = ByteArray(VAULT_HEADER_SIZE)
        raf.seek(0)
        raf.readFully(bytes)

        // Validate magic
        if (!bytes.sliceArray(0..3).contentEquals(VAULT_MAGIC)) {
            Log.e(TAG, "Invalid vault magic")
            return null
        }

        val bb = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        bb.position(4) // skip magic

        return VaultHeader(
            version = bb.getShort().toInt(),
            keyVersion = bb.getShort().toInt(),
            indexOffset = bb.getLong(),
            indexSize = bb.getLong(),
            contentOffset = bb.getLong(),
            createdTime = bb.getLong(),
            modifiedTime = bb.getLong()
        )
    }

    private fun parseIndex(data: ByteArray): List<BlockMetadata> {
        if (data.size < INDEX_HEADER_SIZE) {
            Log.e(TAG, "Index data too small: ${data.size} bytes")
            return emptyList()
        }

        // Validate magic
        if (!data.sliceArray(0..3).contentEquals(INDEX_MAGIC)) {
            Log.e(TAG, "Invalid index magic")
            return emptyList()
        }

        val bb = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)
        bb.position(4) // skip magic
        val version = bb.getShort().toInt()
        val count = bb.getInt()

        Log.i(TAG, "Index: version=$version, count=$count")

        val entries = mutableListOf<BlockMetadata>()
        for (i in 0 until count) {
            val entryOffset = INDEX_HEADER_SIZE + i * INDEX_ENTRY_SIZE
            if (entryOffset + INDEX_ENTRY_SIZE > data.size) {
                Log.w(TAG, "Index truncated at entry $i")
                break
            }

            try {
                entries.add(parseIndexEntry(data, entryOffset))
            } catch (e: Exception) {
                Log.w(TAG, "Skipping corrupted index entry $i: ${e.message}")
            }
        }
        return entries
    }

    private fun parseIndexEntry(data: ByteArray, offset: Int): BlockMetadata {
        val bb = ByteBuffer.wrap(data, offset, INDEX_ENTRY_SIZE).order(ByteOrder.LITTLE_ENDIAN)
        val msb = bb.getLong()
        val lsb = bb.getLong()
        val typeCode = bb.get().toInt()
        val fileOffset = bb.getLong()
        val compressedSize = bb.getLong()
        val uncompressedSize = bb.getLong()
        val timestamp = bb.getLong()

        val categoryLen = bb.getShort().toInt().coerceIn(0, 64)
        val categoryBytes = ByteArray(64)
        bb.get(categoryBytes)
        val category = String(categoryBytes, 0, categoryLen)

        val tagsLen = bb.getShort().toInt().coerceIn(0, 128)
        val tagsBytes = ByteArray(128)
        bb.get(tagsBytes)
        val tags = String(tagsBytes, 0, tagsLen)

        val hashBytes = ByteArray(64)
        bb.get(hashBytes)
        val contentHash = String(hashBytes).trimEnd('\u0000')

        val textLen = bb.getShort().toInt().coerceIn(0, 200)
        val textBytes = ByteArray(200)
        bb.get(textBytes)
        val searchableText = String(textBytes, 0, textLen)

        return BlockMetadata(
            blockId = UUID(msb, lsb),
            blockType = BlockType.fromCode(typeCode) ?: BlockType.MESSAGE,
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

    private fun loadKeystoreKey(alias: String): SecretKey? {
        return try {
            val keyStore = KeyStore.getInstance("AndroidKeyStore")
            keyStore.load(null)
            keyStore.getKey(alias, null) as? SecretKey
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load keystore key '$alias': ${e.message}")
            null
        }
    }

    private fun decrypt(data: ByteArray): ByteArray? {
        if (data.size < IV_SIZE) return null
        val key = secretKey ?: return null
        return try {
            val iv = data.copyOfRange(0, IV_SIZE)
            val encrypted = data.copyOfRange(IV_SIZE, data.size)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
            cipher.doFinal(encrypted)
        } catch (e: Exception) {
            Log.w(TAG, "Decryption failed: ${e.message}")
            null
        }
    }

    private fun decompress(data: ByteArray, originalSize: Int): ByteArray? {
        return try {
            val decompressor = lz4.fastDecompressor()
            val output = ByteArray(originalSize)
            decompressor.decompress(data, 0, output, 0, originalSize)
            output
        } catch (e: Exception) {
            Log.w(TAG, "LZ4 decompression failed: ${e.message}")
            null
        }
    }
}
