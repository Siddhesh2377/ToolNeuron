package com.dark.file_ops

class FileOps {

    private external fun nativeInit(basePath: String): Boolean
    private external fun nativeWrite(path: String, data: ByteArray, offset: Long): Boolean
    private external fun nativeRead(path: String, offset: Long, length: Long): ByteArray?
    private external fun nativeAppend(path: String, data: ByteArray): Boolean
    private external fun nativeDelete(path: String): Boolean
    private external fun nativeExists(path: String): Boolean
    private external fun nativeGetSize(path: String): Long
    private external fun nativeMakeDir(path: String): Boolean
    private external fun nativeListDir(path: String): Array<String>?
    private external fun nativeRename(from: String, to: String): Boolean
    private external fun nativeFsync(path: String): Boolean

    fun init(basePath: String): Boolean = nativeInit(basePath)
    fun write(path: String, data: ByteArray): Boolean = nativeWrite(path, data, 0)
    fun read(path: String, offset: Long = 0, length: Long = 0): ByteArray =
        nativeRead(path, offset, length) ?: throw IllegalStateException("Read failed: $path")
    fun append(path: String, data: ByteArray): Boolean = nativeAppend(path, data)
    fun delete(path: String): Boolean = nativeDelete(path)
    fun exists(path: String): Boolean = nativeExists(path)
    fun getSize(path: String): Long = nativeGetSize(path)
    fun makeDir(path: String): Boolean = nativeMakeDir(path)
    fun listDir(path: String): List<String> = nativeListDir(path)?.toList() ?: emptyList()
    fun rename(from: String, to: String): Boolean = nativeRename(from, to)
    fun fsync(path: String): Boolean = nativeFsync(path)

    companion object {
        init {
            System.loadLibrary("file_ops")
        }
    }
}
