package com.dark.plugin_api.api

interface OnnxApi {
    suspend fun loadSession(
        modelPath: String,
        options: OnnxOptions = OnnxOptions(),
    ): OnnxSession
}

interface OnnxSession {
    val inputNames: List<String>
    val outputNames: List<String>

    suspend fun run(inputs: Map<String, OnnxTensor>): Map<String, OnnxTensor>
    fun close()
}

data class OnnxOptions(
    val intraOpThreads: Int = 0,
    val interOpThreads: Int = 0,
    val useNnapi: Boolean = false,
    val useXnnpack: Boolean = true,
)

sealed class OnnxTensor {
    abstract val shape: LongArray

    data class F32(val data: FloatArray, override val shape: LongArray) : OnnxTensor()
    data class I64(val data: LongArray, override val shape: LongArray) : OnnxTensor()
    data class I32(val data: IntArray,  override val shape: LongArray) : OnnxTensor()
    data class U8 (val data: ByteArray, override val shape: LongArray) : OnnxTensor()
    data class Str(val data: Array<String>, override val shape: LongArray) : OnnxTensor()
}
