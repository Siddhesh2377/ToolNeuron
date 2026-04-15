package com.memoryvault.core

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.channels.FileChannel

class VaultFile(val file: File) {
    private val mutex = Mutex()
    private var raf: RandomAccessFile? = null
    private var channel: FileChannel? = null

    suspend fun open() = withContext(Dispatchers.IO) {
        mutex.withLock {
            if (raf == null) {
                raf = RandomAccessFile(file, "rw")
                channel = raf?.channel
            }
        }
    }

    suspend fun close() = withContext(Dispatchers.IO) {
        mutex.withLock {
            channel?.close()
            raf?.close()
            channel = null
            raf = null
        }
    }

    suspend fun writeAt(offset: Long, data: ByteArray) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val ch = channel ?: throw IllegalStateException("File not open")
            val buffer = ByteBuffer.wrap(data)
            ch.position(offset)
            ch.write(buffer)
            ch.force(true)
        }
    }

    suspend fun readAt(offset: Long, size: Int): ByteArray = withContext(Dispatchers.IO) {
        mutex.withLock {
            val ch = channel ?: throw IllegalStateException("File not open")
            val buffer = ByteBuffer.allocate(size)
            ch.position(offset)
            ch.read(buffer)
            buffer.array()
        }
    }

    suspend fun append(data: ByteArray): Long = withContext(Dispatchers.IO) {
        mutex.withLock {
            val ch = channel ?: throw IllegalStateException("File not open")
            val offset = ch.size()
            val buffer = ByteBuffer.wrap(data)
            ch.position(offset)
            ch.write(buffer)
            ch.force(true)
            offset
        }
    }

    suspend fun size(): Long = withContext(Dispatchers.IO) {
        mutex.withLock {
            channel?.size() ?: 0L
        }
    }

    suspend fun truncate(size: Long) = withContext(Dispatchers.IO) {
        mutex.withLock {
            channel?.truncate(size)
            channel?.force(true)
        }
    }

    fun exists(): Boolean = file.exists()
}