package com.dark.tool_neuron.service.server

import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.max
import kotlin.math.min

data class ServerPcm(val sampleRate: Int, val samples: FloatArray)

object ServerWavCodec {

    fun writeWav(path: String, samples: FloatArray, sampleRate: Int, channels: Int = 1): Boolean {
        return try {
            val dataSize = samples.size * 2
            val bb = ByteBuffer.allocate(44 + dataSize).order(ByteOrder.LITTLE_ENDIAN)
            bb.put("RIFF".toByteArray())
            bb.putInt(36 + dataSize)
            bb.put("WAVE".toByteArray())
            bb.put("fmt ".toByteArray())
            bb.putInt(16)
            bb.putShort(1)
            bb.putShort(channels.toShort())
            bb.putInt(sampleRate)
            bb.putInt(sampleRate * channels * 2)
            bb.putShort((channels * 2).toShort())
            bb.putShort(16)
            bb.put("data".toByteArray())
            bb.putInt(dataSize)
            for (s in samples) {
                val clamped = max(-1.0f, min(1.0f, s))
                bb.putShort((clamped * 32767.0f).toInt().toShort())
            }
            FileOutputStream(path).use { it.write(bb.array()) }
            true
        } catch (_: Exception) {
            false
        }
    }

    fun readWav(path: String): ServerPcm? {
        return try {
            val bytes = File(path).readBytes()
            decode(bytes)
        } catch (_: Exception) {
            null
        }
    }

    private fun decode(bytes: ByteArray): ServerPcm? {
        if (bytes.size < 44) return null
        if (String(bytes, 0, 4) != "RIFF" || String(bytes, 8, 4) != "WAVE") return null
        val bb = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        var pos = 12
        var audioFormat = 0
        var channels = 0
        var sampleRate = 0
        var bitsPerSample = 0
        var dataOffset = -1
        var dataLen = 0
        while (pos + 8 <= bytes.size) {
            val id = String(bytes, pos, 4)
            val sz = bb.getInt(pos + 4)
            if (sz < 0 || pos + 8 + sz > bytes.size) return null
            when (id) {
                "fmt " -> {
                    if (sz >= 16) {
                        audioFormat   = bb.getShort(pos + 8).toInt() and 0xFFFF
                        channels      = bb.getShort(pos + 10).toInt() and 0xFFFF
                        sampleRate    = bb.getInt(pos + 12)
                        bitsPerSample = bb.getShort(pos + 22).toInt() and 0xFFFF
                    }
                }
                "data" -> {
                    dataOffset = pos + 8
                    dataLen    = sz
                }
            }
            pos += 8 + sz + (sz and 1)
            if (dataOffset >= 0) break
        }
        if (dataOffset < 0 || channels == 0 || sampleRate == 0) return null

        val floats = FloatArray(when {
            audioFormat == 1 && bitsPerSample == 16 -> dataLen / 2
            audioFormat == 3 && bitsPerSample == 32 -> dataLen / 4
            else -> return null
        })

        when {
            audioFormat == 1 && bitsPerSample == 16 -> {
                val ib = ByteBuffer.wrap(bytes, dataOffset, dataLen)
                    .order(ByteOrder.LITTLE_ENDIAN)
                    .asShortBuffer()
                for (i in floats.indices) {
                    floats[i] = ib.get(i) / 32768.0f
                }
            }
            audioFormat == 3 && bitsPerSample == 32 -> {
                val fb = ByteBuffer.wrap(bytes, dataOffset, dataLen)
                    .order(ByteOrder.LITTLE_ENDIAN)
                    .asFloatBuffer()
                fb.get(floats)
            }
        }

        if (channels == 1) return ServerPcm(sampleRate, floats)

        val frames = floats.size / channels
        val mono = FloatArray(frames)
        for (i in 0 until frames) {
            var sum = 0f
            for (c in 0 until channels) sum += floats[i * channels + c]
            mono[i] = sum / channels
        }
        return ServerPcm(sampleRate, mono)
    }
}
