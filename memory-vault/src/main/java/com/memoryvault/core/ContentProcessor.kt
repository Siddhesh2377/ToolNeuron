package com.memoryvault.core

class ContentProcessor(
    private val encryptionManager: EncryptionManager
) {

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
