package com.memoryvault.core

class ContentProcessor(
    private val encryptionManager: EncryptionManager
) {
    
    fun processForWrite(data: ByteArray, shouldEncrypt: Boolean): ProcessedContent {
        var processed = data
        var compressed = false
        
        if (CompressionUtils.shouldCompress(data)) {
            processed = CompressionUtils.compress(processed)
            compressed = true
        }
        
        val encrypted = if (shouldEncrypt) {
            val encryptedData = encryptionManager.encrypt(processed)
            processed = encryptedData.toBytes()
            true
        } else {
            false
        }
        
        return ProcessedContent(
            data = processed,
            originalSize = data.size,
            compressed = compressed,
            encrypted = encrypted
        )
    }
    
    fun processForRead(
        data: ByteArray,
        originalSize: Int,
        compressed: Boolean,
        encrypted: Boolean
    ): ByteArray {
        var processed = data
        
        if (encrypted) {
            val encryptedData = EncryptedData.fromBytes(processed)
            processed = encryptionManager.decrypt(encryptedData)
        }
        
        if (compressed) {
            processed = CompressionUtils.decompress(processed, originalSize)
        }
        
        return processed
    }
}

data class ProcessedContent(
    val data: ByteArray,
    val originalSize: Int,
    val compressed: Boolean,
    val encrypted: Boolean
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as ProcessedContent
        if (!data.contentEquals(other.data)) return false
        if (originalSize != other.originalSize) return false
        if (compressed != other.compressed) return false
        if (encrypted != other.encrypted) return false
        return true
    }

    override fun hashCode(): Int {
        var result = data.contentHashCode()
        result = 31 * result + originalSize
        result = 31 * result + compressed.hashCode()
        result = 31 * result + encrypted.hashCode()
        return result
    }
}