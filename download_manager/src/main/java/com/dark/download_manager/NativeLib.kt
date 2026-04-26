package com.dark.download_manager

internal object HxdNative {

    init { System.loadLibrary("download_manager") }

    external fun nativePrepare(id: Int, destPath: String): Long

    external fun nativeSetTotal(id: Int, total: Long)

    external fun nativeWriteChunk(id: Int, data: ByteArray, offset: Int, len: Int): Boolean

    external fun nativeComplete(id: Int): Boolean

    external fun nativeFail(id: Int)

    external fun nativeCancel(id: Int)

    external fun nativePause(id: Int)

    external fun nativeGetProgress(id: Int): LongArray

    external fun nativeCleanup(id: Int)

    external fun nativeSaveQueue(
        path: String,
        ids: IntArray,
        states: ByteArray,
        downloaded: LongArray,
        total: LongArray,
        urls: Array<String>,
        destPaths: Array<String>
    ): Boolean

    external fun nativeLoadQueue(path: String): Array<Array<String>>
}
