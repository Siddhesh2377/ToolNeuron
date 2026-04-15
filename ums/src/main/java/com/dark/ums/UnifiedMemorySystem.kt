package com.dark.ums

class UmsRecord internal constructor(internal val data: ByteArray) {
    companion object {
        private const val HEADER_SIZE = 16
        private const val MAGIC = 0x554D5352u

        fun create(): Builder = Builder()
    }

    class Builder {
        private val fields = mutableListOf<Triple<Int, Int, ByteArray>>() // tag, wireType, data
        private var id: Int = 0

        fun id(id: Int) = apply { this.id = id }

        fun putInt(tag: Int, value: Int) = apply {
            putLong(tag, value.toLong())
        }

        fun putLong(tag: Int, value: Long) = apply {
            // Zig-zag encode then LEB128
            val zigzag = (value shl 1) xor (value shr 63)
            val buf = ByteArray(10)
            var v = zigzag.toULong()
            var i = 0
            while (v > 0x7Fu) {
                buf[i++] = ((v.toInt() and 0x7F) or 0x80).toByte()
                v = v shr 7
            }
            buf[i++] = v.toByte()
            fields.add(Triple(tag, 0, buf.copyOf(i)))
        }

        fun putBool(tag: Int, value: Boolean) = putLong(tag, if (value) 1L else 0L)

        fun putTimestamp(tag: Int, value: Long) = apply {
            val buf = ByteArray(8)
            for (i in 0..7) buf[i] = ((value ushr (i * 8)) and 0xFF).toByte()
            fields.add(Triple(tag, 1, buf))
        }

        fun putString(tag: Int, value: String) = putBytes(tag, value.toByteArray())

        fun putBytes(tag: Int, value: ByteArray) = apply {
            fields.add(Triple(tag, 2, value))
        }

        fun putFloat(tag: Int, value: Float) = apply {
            val bits = java.lang.Float.floatToIntBits(value)
            val buf = ByteArray(4)
            for (i in 0..3) buf[i] = ((bits ushr (i * 8)) and 0xFF).toByte()
            fields.add(Triple(tag, 3, buf))
        }

        fun build(): UmsRecord {
            val body = mutableListOf<Byte>()

            for ((tag, wireType, data) in fields) {
                // tag: 2 bytes LE
                body.add((tag and 0xFF).toByte())
                body.add((tag shr 8).toByte())
                // wire type: 1 byte
                body.add(wireType.toByte())
                // length prefix for bytes type
                if (wireType == 2) {
                    val len = data.size
                    body.add((len and 0xFF).toByte())
                    body.add(((len shr 8) and 0xFF).toByte())
                    body.add(((len shr 16) and 0xFF).toByte())
                    body.add(((len shr 24) and 0xFF).toByte())
                }
                data.forEach { body.add(it) }
            }

            val bodyBytes = body.toByteArray()
            val totalSize = HEADER_SIZE + bodyBytes.size
            val out = ByteArray(totalSize)

            // Magic (LE)
            val magic = MAGIC.toInt()
            out[0] = (magic and 0xFF).toByte()
            out[1] = ((magic shr 8) and 0xFF).toByte()
            out[2] = ((magic shr 16) and 0xFF).toByte()
            out[3] = ((magic shr 24) and 0xFF).toByte()

            // Record size (total including header)
            out[4] = (totalSize and 0xFF).toByte()
            out[5] = ((totalSize shr 8) and 0xFF).toByte()
            out[6] = ((totalSize shr 16) and 0xFF).toByte()
            out[7] = ((totalSize shr 24) and 0xFF).toByte()

            // Record ID (LE)
            out[8] = (id and 0xFF).toByte()
            out[9] = ((id shr 8) and 0xFF).toByte()
            out[10] = ((id shr 16) and 0xFF).toByte()
            out[11] = ((id shr 24) and 0xFF).toByte()

            // Field count (LE)
            val fc = fields.size
            out[12] = (fc and 0xFF).toByte()
            out[13] = ((fc shr 8) and 0xFF).toByte()

            // Flags + reserved
            out[14] = 0
            out[15] = 0

            bodyBytes.copyInto(out, HEADER_SIZE)
            return UmsRecord(out)
        }
    }

    // ID is at bytes 8-11 (LE)
    val id: Int get() {
        if (data.size < 12) return 0
        return (data[8].toInt() and 0xFF) or
               ((data[9].toInt() and 0xFF) shl 8) or
               ((data[10].toInt() and 0xFF) shl 16) or
               ((data[11].toInt() and 0xFF) shl 24)
    }

    // parsed field cache: tag -> (wireType, raw bytes)
    private class Field(val wireType: Int, val bytes: ByteArray)

    private val fields: Map<Int, Field> by lazy { parseFields() }

    private fun parseFields(): Map<Int, Field> {
        val result = mutableMapOf<Int, Field>()
        if (data.size < HEADER_SIZE) return result
        var pos = HEADER_SIZE
        while (pos + 3 <= data.size) {
            val tag = (data[pos].toInt() and 0xFF) or ((data[pos + 1].toInt() and 0xFF) shl 8)
            val wt = data[pos + 2].toInt() and 0xFF
            pos += 3
            when (wt) {
                0 -> { // varint (LEB128)
                    val start = pos
                    while (pos < data.size && (data[pos].toInt() and 0x80) != 0) pos++
                    if (pos < data.size) pos++
                    result[tag] = Field(0, data.copyOfRange(start, pos))
                }
                1 -> { // fixed64
                    if (pos + 8 <= data.size) {
                        result[tag] = Field(1, data.copyOfRange(pos, pos + 8))
                        pos += 8
                    }
                }
                2 -> { // length-prefixed bytes
                    if (pos + 4 <= data.size) {
                        val len = (data[pos].toInt() and 0xFF) or
                                  ((data[pos + 1].toInt() and 0xFF) shl 8) or
                                  ((data[pos + 2].toInt() and 0xFF) shl 16) or
                                  ((data[pos + 3].toInt() and 0xFF) shl 24)
                        pos += 4
                        if (pos + len <= data.size) {
                            result[tag] = Field(2, data.copyOfRange(pos, pos + len))
                            pos += len
                        }
                    }
                }
                3 -> { // fixed32
                    if (pos + 4 <= data.size) {
                        result[tag] = Field(3, data.copyOfRange(pos, pos + 4))
                        pos += 4
                    }
                }
            }
        }
        return result
    }

    // read a varint field, zig-zag decoded
    fun getLong(tag: Int): Long? {
        val f = fields[tag] ?: return null
        if (f.wireType != 0) return null
        var result = 0UL
        var shift = 0
        for (b in f.bytes) {
            result = result or ((b.toLong() and 0x7F).toULong() shl shift)
            shift += 7
            if ((b.toInt() and 0x80) == 0) break
        }
        val zz = result.toLong()
        return (zz ushr 1) xor -(zz and 1)
    }

    fun getInt(tag: Int): Int? = getLong(tag)?.toInt()

    fun getBool(tag: Int): Boolean? = getLong(tag)?.let { it != 0L }

    // read a fixed64 field (timestamp, raw long)
    fun getTimestamp(tag: Int): Long? {
        val f = fields[tag] ?: return null
        if (f.wireType != 1) return null
        var v = 0L
        for (i in 0..7) v = v or ((f.bytes[i].toLong() and 0xFF) shl (i * 8))
        return v
    }

    // read a length-prefixed bytes field as String
    fun getString(tag: Int): String? {
        val f = fields[tag] ?: return null
        if (f.wireType != 2) return null
        return String(f.bytes)
    }

    // read a length-prefixed bytes field as raw ByteArray
    fun getBytes(tag: Int): ByteArray? {
        val f = fields[tag] ?: return null
        if (f.wireType != 2) return null
        return f.bytes
    }

    // read a fixed32 field as Float
    fun getFloat(tag: Int): Float? {
        val f = fields[tag] ?: return null
        if (f.wireType != 3) return null
        val bits = (f.bytes[0].toInt() and 0xFF) or
                   ((f.bytes[1].toInt() and 0xFF) shl 8) or
                   ((f.bytes[2].toInt() and 0xFF) shl 16) or
                   ((f.bytes[3].toInt() and 0xFF) shl 24)
        return java.lang.Float.intBitsToFloat(bits)
    }

}

class UnifiedMemorySystem {

    private external fun nativeExists(basePath: String): Boolean
    private external fun nativeClose()
    private external fun nativeEnsureCollection(name: String): Boolean
    private external fun nativePut(collection: String, recordData: ByteArray): Int
    private external fun nativeDelete(collection: String, recordId: Int): Boolean
    private external fun nativeCount(collection: String): Int
    private external fun nativeGetAll(collection: String): Array<ByteArray>?
    private external fun nativeCreateWithPassphrase(basePath: String, appKey: ByteArray, passphrase: ByteArray): Boolean
    private external fun nativeOpenWithPassphrase(basePath: String, appKey: ByteArray, passphrase: ByteArray): Boolean
    private external fun nativeCreatePlaintext(basePath: String): Boolean
    private external fun nativeOpenPlaintext(basePath: String): Boolean
    private external fun nativeGetFlags(): Int
    private external fun nativeSetFlags(flags: Int): Boolean
    private external fun nativeAddIndex(collection: String, tag: Int, wireType: Int): Boolean
    private external fun nativeQueryString(collection: String, tag: Int, value: String): Array<ByteArray>?
    fun createWithPassphrase(basePath: String, appKey: ByteArray, passphrase: String): Boolean =
        nativeCreateWithPassphrase(basePath, appKey, passphrase.toByteArray())

    fun openWithPassphrase(basePath: String, appKey: ByteArray, passphrase: String): Boolean =
        nativeOpenWithPassphrase(basePath, appKey, passphrase.toByteArray())

    fun createPlaintext(basePath: String): Boolean = nativeCreatePlaintext(basePath)

    fun openPlaintext(basePath: String): Boolean = nativeOpenPlaintext(basePath)

    fun exists(basePath: String): Boolean = nativeExists(basePath)

    fun close() = nativeClose()

    fun ensureCollection(name: String): Boolean = nativeEnsureCollection(name)

    fun put(collection: String, record: UmsRecord): Int =
        nativePut(collection, record.data)

    fun delete(collection: String, recordId: Int): Boolean =
        nativeDelete(collection, recordId)

    fun count(collection: String): Int = nativeCount(collection)

    fun getAll(collection: String): List<UmsRecord> =
        nativeGetAll(collection)?.map { UmsRecord(it) } ?: emptyList()

    fun getFlags(): Int = nativeGetFlags()
    fun setFlags(flags: Int): Boolean = nativeSetFlags(flags)

    // register an in-memory index on a field tag for a collection
    fun addIndex(collection: String, tag: Int, wireType: Int): Boolean =
        nativeAddIndex(collection, tag, wireType)

    // query by string field equality (uses index if available, else linear scan)
    fun queryString(collection: String, tag: Int, value: String): List<UmsRecord> =
        nativeQueryString(collection, tag, value)?.map { UmsRecord(it) } ?: emptyList()

    companion object {
        const val FLAG_MIGRATION_COMPLETE = 0x0001

        // wire type constants matching C++ WireType enum
        const val WIRE_FIXED64 = 1
        const val WIRE_BYTES = 2

        init {
            System.loadLibrary("ums")
        }
    }
}
