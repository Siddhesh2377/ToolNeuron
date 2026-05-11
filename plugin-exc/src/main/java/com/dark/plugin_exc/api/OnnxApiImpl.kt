package com.dark.plugin_exc.api

import ai.onnxruntime.OnnxJavaType
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import ai.onnxruntime.TensorInfo
import com.dark.plugin_api.PluginCapability
import com.dark.plugin_api.api.OnnxApi
import com.dark.plugin_api.api.OnnxOptions
import com.dark.plugin_api.api.OnnxSession
import com.dark.plugin_api.api.OnnxTensor
import com.dark.plugin_exc.CapabilityGate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer
import java.nio.FloatBuffer
import java.nio.IntBuffer
import java.nio.LongBuffer
import ai.onnxruntime.OnnxTensor as OrtTensor

internal class OnnxApiImpl(
    private val ortEnv: OrtEnvironment,
    private val gate: CapabilityGate,
    private val epProvider: () -> String = { EP_CPU },
) : OnnxApi {

    override suspend fun loadSession(
        modelPath: String,
        options: OnnxOptions,
    ): OnnxSession = withContext(Dispatchers.IO) {
        gate.require(PluginCapability.AI_ONNX)
        val sessionOptions = OrtSession.SessionOptions().apply {
            if (options.intraOpThreads > 0) setIntraOpNumThreads(options.intraOpThreads)
            if (options.interOpThreads > 0) setInterOpNumThreads(options.interOpThreads)
            when (epProvider()) {
                EP_NNAPI -> runCatching { addNnapi() }
                EP_XNNPACK -> runCatching { addXnnpack(emptyMap<String, String>()) }
            }
        }
        val session = ortEnv.createSession(modelPath, sessionOptions)
        OnnxSessionImpl(ortEnv, session, sessionOptions)
    }

    companion object {
        const val EP_CPU = "cpu"
        const val EP_NNAPI = "nnapi"
        const val EP_XNNPACK = "xnnpack"
    }
}

private class OnnxSessionImpl(
    private val ortEnv: OrtEnvironment,
    private val session: OrtSession,
    private val sessionOptions: OrtSession.SessionOptions,
) : OnnxSession {

    override val inputNames: List<String> = session.inputInfo.keys.toList()
    override val outputNames: List<String> = session.outputInfo.keys.toList()

    override suspend fun run(
        inputs: Map<String, OnnxTensor>,
    ): Map<String, OnnxTensor> = withContext(Dispatchers.IO) {
        val ortInputs = HashMap<String, OrtTensor>(inputs.size)
        try {
            inputs.forEach { (name, tensor) ->
                ortInputs[name] = tensor.toOrt(ortEnv)
            }
            val result = session.run(ortInputs)
            val output = LinkedHashMap<String, OnnxTensor>(result.size())
            for ((name, value) in result) {
                if (value is OrtTensor) output[name] = value.toPluginTensor()
            }
            result.close()
            output
        } finally {
            ortInputs.values.forEach { runCatching { it.close() } }
        }
    }

    override fun close() {
        runCatching { session.close() }
        runCatching { sessionOptions.close() }
    }
}

private fun OnnxTensor.toOrt(env: OrtEnvironment): OrtTensor = when (this) {
    is OnnxTensor.F32 -> OrtTensor.createTensor(env, FloatBuffer.wrap(data), shape)
    is OnnxTensor.I64 -> OrtTensor.createTensor(env, LongBuffer.wrap(data), shape)
    is OnnxTensor.I32 -> OrtTensor.createTensor(env, IntBuffer.wrap(data), shape)
    is OnnxTensor.U8 -> OrtTensor.createTensor(env, ByteBuffer.wrap(data), shape, OnnxJavaType.UINT8)
    is OnnxTensor.Str -> OrtTensor.createTensor(env, data, shape)
}

private fun OrtTensor.toPluginTensor(): OnnxTensor {
    val info = this.info as TensorInfo
    val shape = info.shape
    return when (info.type) {
        OnnxJavaType.FLOAT -> {
            val flat = floatBuffer.asReadOnlyBuffer()
            val arr = FloatArray(flat.remaining()).also { flat.get(it) }
            OnnxTensor.F32(arr, shape)
        }
        OnnxJavaType.INT64 -> {
            val flat = longBuffer.asReadOnlyBuffer()
            val arr = LongArray(flat.remaining()).also { flat.get(it) }
            OnnxTensor.I64(arr, shape)
        }
        OnnxJavaType.INT32 -> {
            val flat = intBuffer.asReadOnlyBuffer()
            val arr = IntArray(flat.remaining()).also { flat.get(it) }
            OnnxTensor.I32(arr, shape)
        }
        OnnxJavaType.UINT8, OnnxJavaType.INT8 -> {
            val flat = byteBuffer.asReadOnlyBuffer()
            val arr = ByteArray(flat.remaining()).also { flat.get(it) }
            OnnxTensor.U8(arr, shape)
        }
        OnnxJavaType.STRING -> {
            val raw = value as Array<*>
            val arr = Array(raw.size) { raw[it]?.toString() ?: "" }
            OnnxTensor.Str(arr, shape)
        }
        else -> {
            val flat = floatBuffer.asReadOnlyBuffer()
            val arr = FloatArray(flat.remaining()).also { flat.get(it) }
            OnnxTensor.F32(arr, shape)
        }
    }
}
