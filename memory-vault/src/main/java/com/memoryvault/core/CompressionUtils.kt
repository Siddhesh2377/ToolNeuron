package com.memoryvault.core

import net.jpountz.lz4.LZ4Factory
import kotlin.math.min

object CompressionUtils {
    private val factory = LZ4Factory.fastestInstance()
    private val compressor = factory.fastCompressor()
    private val decompressor = factory.fastDecompressor()
    
    private const val SAMPLE_SIZE = 1024
    private const val COMPRESSION_THRESHOLD = 0.9f
    
    fun shouldCompress(data: ByteArray): Boolean {
        if (data.size < SAMPLE_SIZE) {
            return false
        }
        
        val sampleSize = min(SAMPLE_SIZE, data.size)
        val sample = data.copyOf(sampleSize)
        val maxCompressedSize = compressor.maxCompressedLength(sampleSize)
        val compressed = ByteArray(maxCompressedSize)
        
        val compressedSize = compressor.compress(sample, 0, sampleSize, compressed, 0, maxCompressedSize)
        val ratio = compressedSize.toFloat() / sampleSize.toFloat()
        
        return ratio < COMPRESSION_THRESHOLD
    }
    
    fun compress(data: ByteArray): ByteArray {
        val maxCompressedSize = compressor.maxCompressedLength(data.size)
        val compressed = ByteArray(maxCompressedSize)
        val compressedSize = compressor.compress(data, 0, data.size, compressed, 0, maxCompressedSize)
        return compressed.copyOf(compressedSize)
    }
    
    fun decompress(compressed: ByteArray, originalSize: Int): ByteArray {
        val decompressed = ByteArray(originalSize)
        decompressor.decompress(compressed, 0, decompressed, 0, originalSize)
        return decompressed
    }
}