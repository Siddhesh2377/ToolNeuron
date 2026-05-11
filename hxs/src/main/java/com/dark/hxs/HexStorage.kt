package com.dark.hxs

import java.nio.ByteBuffer
import java.nio.ByteOrder

class HexStorage {


    fun createPlaintext(basePath: String): Boolean =
        nativeCreatePlaintext(basePath)

    fun openPlaintext(basePath: String): Boolean =
        nativeOpenPlaintext(basePath)

    fun createEncrypted(basePath: String, appKey: ByteArray, userKey: ByteArray, encryptor: Any): Boolean =
        nativeCreateEncrypted(basePath, appKey, userKey, encryptor)

    fun openEncrypted(basePath: String, appKey: ByteArray, userKey: ByteArray, encryptor: Any): Boolean =
        nativeOpenEncrypted(basePath, appKey, userKey, encryptor)

    fun close() = nativeClose()

    fun exists(basePath: String): Boolean = nativeExists(basePath)


    fun ensureCollection(name: String): Boolean = nativeEnsureCollection(name)
    fun dropCollection(name: String) = nativeDropCollection(name)
    fun listCollections(): List<String> = nativeListCollections()?.toList() ?: emptyList()


    fun put(collection: String, record: HxsRecord): Int {
        val encoded = record.encode()
        return nativePut(collection, encoded)
    }

    fun update(collection: String, record: HxsRecord): Boolean {
        val encoded = record.encode()
        return nativeUpdate(collection, encoded)
    }

    fun get(collection: String, recordId: Int): HxsRecord? {
        val data = nativeGet(collection, recordId) ?: return null
        return HxsRecord.decode(data)
    }

    fun delete(collection: String, recordId: Int): Boolean =
        nativeDelete(collection, recordId)

    fun count(collection: String): Int = nativeCount(collection)

    fun getAll(collection: String): List<HxsRecord> {
        val arrays = nativeGetAll(collection) ?: return emptyList()
        return arrays.map { HxsRecord.decode(it) }
    }


    fun addIndex(collection: String, tag: Int, wireType: Int) =
        nativeAddIndex(collection, tag, wireType)

    fun removeIndex(collection: String, tag: Int) =
        nativeRemoveIndex(collection, tag)


    fun queryString(collection: String, tag: Int, value: String): List<HxsRecord> {
        val arrays = nativeQueryString(collection, tag, value) ?: return emptyList()
        return arrays.map { HxsRecord.decode(it) }
    }

    fun queryInt(collection: String, tag: Int, value: Long): List<HxsRecord> {
        val arrays = nativeQueryInt(collection, tag, value) ?: return emptyList()
        return arrays.map { HxsRecord.decode(it) }
    }

    fun queryRange(collection: String, tag: Int, minVal: Long, maxVal: Long): List<HxsRecord> {
        val arrays = nativeQueryRange(collection, tag, minVal, maxVal) ?: return emptyList()
        return arrays.map { HxsRecord.decode(it) }
    }


    fun flush(collection: String) = nativeFlush(collection)
    fun flushAll() = nativeFlushAll()


    fun getSchemaVersion(collection: String): Int = nativeGetSchemaVersion(collection)
    fun setSchemaVersion(collection: String, version: Int) = nativeSetSchemaVersion(collection, version)


    fun ragIngest(
        collection: String,
        docId: String,
        chatId: String,
        sourceId: String,
        chunkIndex: Int,
        text: String,
    ): Int = nativeRagIngest(collection, docId, chatId, sourceId, chunkIndex, text)

    fun ragRemoveDocument(collection: String, docId: String): Int =
        nativeRagRemoveDocument(collection, docId)

    fun ragClear(collection: String) = nativeRagClear(collection)

    fun ragDocCount(collection: String, docId: String): Int =
        nativeRagDocCount(collection, docId)

    fun ragQuery(collection: String, query: String, chatId: String, topK: Int): List<HxsRecord> {
        val arrays = nativeRagQuery(collection, query, chatId, topK) ?: return emptyList()
        return arrays.map { HxsRecord.decode(it) }
    }


    private external fun nativeCreatePlaintext(basePath: String): Boolean
    private external fun nativeOpenPlaintext(basePath: String): Boolean
    private external fun nativeCreateEncrypted(basePath: String, appKey: ByteArray, userKey: ByteArray, encryptor: Any): Boolean
    private external fun nativeOpenEncrypted(basePath: String, appKey: ByteArray, userKey: ByteArray, encryptor: Any): Boolean
    private external fun nativeClose()
    private external fun nativeExists(basePath: String): Boolean

    private external fun nativeEnsureCollection(name: String): Boolean
    private external fun nativeDropCollection(name: String)
    private external fun nativeListCollections(): Array<String>?

    private external fun nativePut(collection: String, recordData: ByteArray): Int
    private external fun nativeUpdate(collection: String, recordData: ByteArray): Boolean
    private external fun nativeGet(collection: String, recordId: Int): ByteArray?
    private external fun nativeDelete(collection: String, recordId: Int): Boolean
    private external fun nativeCount(collection: String): Int
    private external fun nativeGetAll(collection: String): Array<ByteArray>?

    private external fun nativeAddIndex(collection: String, tag: Int, wireType: Int)
    private external fun nativeRemoveIndex(collection: String, tag: Int)

    private external fun nativeQueryString(collection: String, tag: Int, value: String): Array<ByteArray>?
    private external fun nativeQueryInt(collection: String, tag: Int, value: Long): Array<ByteArray>?
    private external fun nativeQueryRange(collection: String, tag: Int, minVal: Long, maxVal: Long): Array<ByteArray>?

    private external fun nativeFlush(collection: String)
    private external fun nativeFlushAll()

    private external fun nativeGetSchemaVersion(collection: String): Int
    private external fun nativeSetSchemaVersion(collection: String, version: Int)

    private external fun nativeRagIngest(
        collection: String,
        docId: String,
        chatId: String,
        sourceId: String,
        chunkIndex: Int,
        text: String,
    ): Int

    private external fun nativeRagRemoveDocument(collection: String, docId: String): Int
    private external fun nativeRagClear(collection: String)
    private external fun nativeRagDocCount(collection: String, docId: String): Int
    private external fun nativeRagQuery(
        collection: String,
        query: String,
        chatId: String,
        topK: Int,
    ): Array<ByteArray>?

    companion object {
        const val WIRE_VARINT  = 0
        const val WIRE_FIXED64 = 1
        const val WIRE_BYTES   = 2
        const val WIRE_FIXED32 = 3

        init {
            System.loadLibrary("hxs")
        }
    }
}

/**
 * HxsRecord — Kotlin-side record builder/reader.
 *
 * Mirrors the C++ Record class. Encodes/decodes the HXS binary wire format
 * so you can build records purely in Kotlin without JNI for simple cases.
 *
 * Usage:
 * ```kotlin
 * val record = HxsRecord.build {
 *     putString(1, "hello")
 *     putInt(2, 42)
 *     putTimestamp(3, System.currentTimeMillis())
 *     putBool(4, true)
 *     putFloat(5, 3.14f)
 *     putBytes(6, byteArrayOf(0x01, 0x02))
 * }
 *
 * val name = record.getString(1)
 * val count = record.getInt(2)
 * ```
 */
class HxsRecord private constructor(
    var id: Int = 0,
    var flags: Int = 0,
    private val fields: MutableMap<Int, Field> = mutableMapOf(),
) {
    private data class Field(val tag: Int, val wireType: Int, val data: ByteArray)


    fun putInt(tag: Int, value: Long) {
        val buf = encodeVarint(zigzagEncode(value))
        fields[tag] = Field(tag, HexStorage.WIRE_VARINT, buf)
    }

    fun putBool(tag: Int, value: Boolean) = putInt(tag, if (value) 1L else 0L)

    fun putTimestamp(tag: Int, value: Long) {
        val buf = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN).putLong(value).array()
        fields[tag] = Field(tag, HexStorage.WIRE_FIXED64, buf)
    }

    fun putString(tag: Int, value: String) {
        fields[tag] = Field(tag, HexStorage.WIRE_BYTES, value.toByteArray(Charsets.UTF_8))
    }

    fun putBytes(tag: Int, value: ByteArray) {
        fields[tag] = Field(tag, HexStorage.WIRE_BYTES, value)
    }

    fun putFloat(tag: Int, value: Float) {
        val buf = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putFloat(value).array()
        fields[tag] = Field(tag, HexStorage.WIRE_FIXED32, buf)
    }

    fun putDouble(tag: Int, value: Double) {
        val buf = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN).putDouble(value).array()
        fields[tag] = Field(tag, HexStorage.WIRE_FIXED64, buf)
    }


    fun getInt(tag: Int, default: Long = 0): Long {
        val f = fields[tag] ?: return default
        if (f.wireType != HexStorage.WIRE_VARINT) return default
        val (value, _) = decodeVarint(f.data, 0)
        return zigzagDecode(value)
    }

    fun getBool(tag: Int, default: Boolean = false): Boolean =
        getInt(tag, if (default) 1L else 0L) != 0L

    fun getTimestamp(tag: Int, default: Long = 0): Long {
        val f = fields[tag] ?: return default
        if (f.wireType != HexStorage.WIRE_FIXED64 || f.data.size < 8) return default
        return ByteBuffer.wrap(f.data).order(ByteOrder.LITTLE_ENDIAN).getLong()
    }

    fun getString(tag: Int, default: String = ""): String {
        val f = fields[tag] ?: return default
        if (f.wireType != HexStorage.WIRE_BYTES) return default
        return String(f.data, Charsets.UTF_8)
    }

    fun getBytes(tag: Int): ByteArray? {
        val f = fields[tag] ?: return null
        if (f.wireType != HexStorage.WIRE_BYTES) return null
        return f.data
    }

    fun getFloat(tag: Int, default: Float = 0f): Float {
        val f = fields[tag] ?: return default
        if (f.wireType != HexStorage.WIRE_FIXED32 || f.data.size < 4) return default
        return ByteBuffer.wrap(f.data).order(ByteOrder.LITTLE_ENDIAN).getFloat()
    }

    fun getDouble(tag: Int, default: Double = 0.0): Double {
        val f = fields[tag] ?: return default
        if (f.wireType != HexStorage.WIRE_FIXED64 || f.data.size < 8) return default
        return ByteBuffer.wrap(f.data).order(ByteOrder.LITTLE_ENDIAN).getDouble()
    }

    fun hasField(tag: Int): Boolean = fields.containsKey(tag)
    fun removeField(tag: Int) { fields.remove(tag) }
    fun tags(): Set<Int> = fields.keys.toSet()


    fun encode(): ByteArray {
        val allFields = fields.values.toList()
        var dataSize = 0
        for (f in allFields) {
            dataSize += 3 // tag(2) + wireType(1)
            dataSize += when (f.wireType) {
                HexStorage.WIRE_VARINT -> 4 + f.data.size
                HexStorage.WIRE_FIXED64 -> 8
                HexStorage.WIRE_FIXED32 -> 4
                HexStorage.WIRE_BYTES -> 4 + f.data.size
                else -> 0
            }
        }

        val totalSize = 16 + dataSize
        val buf = ByteBuffer.allocate(totalSize).order(ByteOrder.LITTLE_ENDIAN)

        // Header
        buf.putInt(0x48585352) // HXSR magic
        buf.putInt(totalSize)
        buf.putInt(id)
        buf.putShort(allFields.size.toShort())
        buf.put(flags.toByte())
        buf.put(1) // version

        // Fields
        for (f in allFields) {
            buf.putShort(f.tag.toShort())
            buf.put(f.wireType.toByte())
            when (f.wireType) {
                HexStorage.WIRE_VARINT -> {
                    buf.putInt(f.data.size)
                    buf.put(f.data)
                }
                HexStorage.WIRE_FIXED64 -> buf.put(f.data, 0, 8)
                HexStorage.WIRE_FIXED32 -> buf.put(f.data, 0, 4)
                HexStorage.WIRE_BYTES -> {
                    buf.putInt(f.data.size)
                    buf.put(f.data)
                }
            }
        }

        return buf.array()
    }

    companion object {
        fun build(block: HxsRecord.() -> Unit): HxsRecord {
            val record = HxsRecord()
            record.block()
            return record
        }

        fun build(id: Int, block: HxsRecord.() -> Unit): HxsRecord {
            val record = HxsRecord(id = id)
            record.block()
            return record
        }

        fun decode(data: ByteArray): HxsRecord {
            if (data.size < 16) return HxsRecord()
            val buf = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)

            val magic = buf.getInt()
            if (magic != 0x48585352) return HxsRecord()

            val totalSize = buf.getInt()
            val id = buf.getInt()
            val fieldCount = buf.getShort().toInt() and 0xFFFF
            val flags = buf.get().toInt() and 0xFF
            buf.get() // version

            val record = HxsRecord(id = id, flags = flags)

            for (i in 0 until fieldCount) {
                if (buf.remaining() < 3) break
                val tag = buf.getShort().toInt() and 0xFFFF
                val wireType = buf.get().toInt() and 0xFF

                val fieldData = when (wireType) {
                    HexStorage.WIRE_VARINT -> {
                        if (buf.remaining() < 4) break
                        val dlen = buf.getInt()
                        if (buf.remaining() < dlen) break
                        ByteArray(dlen).also { buf.get(it) }
                    }
                    HexStorage.WIRE_FIXED64 -> {
                        if (buf.remaining() < 8) break
                        ByteArray(8).also { buf.get(it) }
                    }
                    HexStorage.WIRE_FIXED32 -> {
                        if (buf.remaining() < 4) break
                        ByteArray(4).also { buf.get(it) }
                    }
                    HexStorage.WIRE_BYTES -> {
                        if (buf.remaining() < 4) break
                        val dlen = buf.getInt()
                        if (buf.remaining() < dlen) break
                        ByteArray(dlen).also { buf.get(it) }
                    }
                    else -> break
                }

                record.fields[tag] = Field(tag, wireType, fieldData)
            }

            return record
        }

        // Varint encoding (zig-zag + LEB128)
        private fun zigzagEncode(v: Long): Long = (v shl 1) xor (v shr 63)
        private fun zigzagDecode(v: Long): Long = (v ushr 1) xor -(v and 1)

        private fun encodeVarint(v: Long): ByteArray {
            var value = v
            val out = mutableListOf<Byte>()
            do {
                val byte = (value and 0x7F).toByte()
                value = value ushr 7
                if (value != 0L) {
                    out.add((byte.toInt() or 0x80).toByte())
                } else {
                    out.add(byte)
                }
            } while (value != 0L)
            return out.toByteArray()
        }

        private fun decodeVarint(data: ByteArray, offset: Int): Pair<Long, Int> {
            var result = 0L
            var shift = 0
            var i = offset
            while (i < data.size && i - offset < 10) {
                val byte = data[i].toLong() and 0xFF
                result = result or ((byte and 0x7F) shl shift)
                i++
                if ((byte and 0x80) == 0L) break
                shift += 7
            }
            return Pair(result, i - offset)
        }
    }
}
