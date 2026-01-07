package com.memoryvault.core

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID

class WALManager(private val walFile: File) {
    private val mutex = Mutex()
    private var raf: RandomAccessFile? = null
    private val entries = mutableListOf<WALEntry>()
    
    private val MAGIC_BYTES = "WLOG".toByteArray()
    private val VERSION: Short = 1
    private val HEADER_SIZE = 128

    suspend fun open() = withContext(Dispatchers.IO) {
        mutex.withLock {
            if (!walFile.exists()) {
                walFile.createNewFile()
                writeHeader()
            }
            raf = RandomAccessFile(walFile, "rw")
            loadEntries()
        }
    }

    suspend fun close() = withContext(Dispatchers.IO) {
        mutex.withLock {
            raf?.close()
            raf = null
        }
    }

    suspend fun append(entry: WALEntry) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val entryBytes = entry.toBytes()
            raf?.let { file ->
                file.seek(file.length())
                file.write(entryBytes)
                file.fd.sync()
            }
            entries.add(entry)
        }
    }

    suspend fun markCommitted(blockId: UUID) = withContext(Dispatchers.IO) {
        mutex.withLock {
            entries.find { it.blockId == blockId }?.let { entry ->
                val index = entries.indexOf(entry)
                entries[index] = entry.copy(committed = true)
            }
        }
    }

    suspend fun getUncommittedEntries(): List<WALEntry> {
        return mutex.withLock {
            entries.filter { !it.committed }
        }
    }

    suspend fun truncate() = withContext(Dispatchers.IO) {
        mutex.withLock {
            raf?.close()
            walFile.delete()
            walFile.createNewFile()
            writeHeader()
            raf = RandomAccessFile(walFile, "rw")
            entries.clear()
        }
    }

    suspend fun checkpoint(checkpointOffset: Long) = withContext(Dispatchers.IO) {
        mutex.withLock {
            updateCheckpointOffset(checkpointOffset)
        }
    }

    private fun writeHeader() {
        val header = ByteBuffer.allocate(HEADER_SIZE).apply {
            order(ByteOrder.LITTLE_ENDIAN)
            put(MAGIC_BYTES)
            putShort(VERSION)
            putLong(0L) // sequence number
            putLong(0L) // checkpoint offset
        }
        
        RandomAccessFile(walFile, "rw").use { file ->
            file.write(header.array())
            file.fd.sync()
        }
    }

    private fun updateCheckpointOffset(offset: Long) {
        raf?.let { file ->
            file.seek(14L) // Skip magic and version
            file.writeLong(offset)
            file.fd.sync()
        }
    }

    private fun loadEntries() {
        raf?.let { file ->
            if (file.length() <= HEADER_SIZE) return
            
            file.seek(HEADER_SIZE.toLong())
            
            while (file.filePointer < file.length()) {
                try {
                    val headerBytes = ByteArray(WALEntry.HEADER_SIZE)
                    file.read(headerBytes)
                    
                    val buffer = ByteBuffer.wrap(headerBytes).apply {
                        order(ByteOrder.LITTLE_ENDIAN)
                    }
                    
                    buffer.position(25) // Skip to data size
                    val dataSize = buffer.int
                    
                    val allBytes = ByteArray(WALEntry.HEADER_SIZE + dataSize)
                    System.arraycopy(headerBytes, 0, allBytes, 0, WALEntry.HEADER_SIZE)
                    
                    if (dataSize > 0) {
                        file.read(allBytes, WALEntry.HEADER_SIZE, dataSize)
                    }
                    
                    val entry = WALEntry.fromBytes(allBytes)
                    entries.add(entry)
                } catch (e: Exception) {
                    break
                }
            }
        }
    }
}