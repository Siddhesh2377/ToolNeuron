package com.dark.hxs

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HxsRecordTest {


    @Test
    fun build_putGetString() {
        val r = HxsRecord.build { putString(1, "hello") }
        assertEquals("hello", r.getString(1))
    }

    @Test
    fun getString_default() {
        val r = HxsRecord.build { }
        assertEquals("fallback", r.getString(99, "fallback"))
    }

    @Test
    fun putString_emptyString() {
        val r = HxsRecord.build { putString(1, "") }
        assertEquals("", r.getString(1))
    }

    @Test
    fun putString_unicode() {
        val r = HxsRecord.build { putString(1, "日本語🎉") }
        assertEquals("日本語🎉", r.getString(1))
    }


    @Test
    fun putGetInt_positive() {
        val r = HxsRecord.build { putInt(1, 42) }
        assertEquals(42L, r.getInt(1))
    }

    @Test
    fun putGetInt_negative() {
        val r = HxsRecord.build { putInt(1, -100) }
        assertEquals(-100L, r.getInt(1))
    }

    @Test
    fun putGetInt_zero() {
        val r = HxsRecord.build { putInt(1, 0) }
        assertEquals(0L, r.getInt(1))
    }

    @Test
    fun putGetInt_maxValue() {
        val r = HxsRecord.build { putInt(1, Long.MAX_VALUE) }
        assertEquals(Long.MAX_VALUE, r.getInt(1))
    }

    @Test
    fun putGetInt_minValue() {
        val r = HxsRecord.build { putInt(1, Long.MIN_VALUE) }
        assertEquals(Long.MIN_VALUE, r.getInt(1))
    }

    @Test
    fun getInt_default() {
        val r = HxsRecord.build { }
        assertEquals(99L, r.getInt(1, 99))
    }


    @Test
    fun putGetBool_true() {
        val r = HxsRecord.build { putBool(1, true) }
        assertTrue(r.getBool(1))
    }

    @Test
    fun putGetBool_false() {
        val r = HxsRecord.build { putBool(1, false) }
        assertFalse(r.getBool(1))
    }

    @Test
    fun getBool_default() {
        val r = HxsRecord.build { }
        assertTrue(r.getBool(1, true))
        assertFalse(r.getBool(1, false))
    }


    @Test
    fun putGetTimestamp() {
        val now = System.currentTimeMillis()
        val r = HxsRecord.build { putTimestamp(1, now) }
        assertEquals(now, r.getTimestamp(1))
    }

    @Test
    fun getTimestamp_default() {
        val r = HxsRecord.build { }
        assertEquals(0L, r.getTimestamp(1))
    }


    @Test
    fun putGetFloat() {
        val r = HxsRecord.build { putFloat(1, 3.14f) }
        assertEquals(3.14f, r.getFloat(1), 0.001f)
    }

    @Test
    fun putGetFloat_negative() {
        val r = HxsRecord.build { putFloat(1, -2.5f) }
        assertEquals(-2.5f, r.getFloat(1), 0.001f)
    }

    @Test
    fun getFloat_default() {
        val r = HxsRecord.build { }
        assertEquals(1.0f, r.getFloat(1, 1.0f), 0.001f)
    }


    @Test
    fun putGetDouble() {
        val r = HxsRecord.build { putDouble(1, 2.718281828) }
        assertEquals(2.718281828, r.getDouble(1), 0.000000001)
    }

    @Test
    fun getDouble_default() {
        val r = HxsRecord.build { }
        assertEquals(0.0, r.getDouble(1), 0.0)
    }


    @Test
    fun putGetBytes() {
        val data = byteArrayOf(0x01, 0x02, 0x03, 0xFF.toByte())
        val r = HxsRecord.build { putBytes(1, data) }
        assertTrue(data.contentEquals(r.getBytes(1)))
    }

    @Test
    fun getBytes_missing() {
        val r = HxsRecord.build { }
        assertNull(r.getBytes(1))
    }

    @Test
    fun putBytes_empty() {
        val r = HxsRecord.build { putBytes(1, byteArrayOf()) }
        assertNotNull(r.getBytes(1))
        assertEquals(0, r.getBytes(1)!!.size)
    }


    @Test
    fun multipleFields_allTypes() {
        val r = HxsRecord.build {
            putString(1, "name")
            putInt(2, 99)
            putBool(3, true)
            putTimestamp(4, 1234567890L)
            putFloat(5, 1.5f)
            putDouble(6, 2.5)
            putBytes(7, byteArrayOf(0x0A, 0x0B))
        }
        assertEquals("name", r.getString(1))
        assertEquals(99L, r.getInt(2))
        assertTrue(r.getBool(3))
        assertEquals(1234567890L, r.getTimestamp(4))
        assertEquals(1.5f, r.getFloat(5), 0.001f)
        assertEquals(2.5, r.getDouble(6), 0.001)
        assertTrue(byteArrayOf(0x0A, 0x0B).contentEquals(r.getBytes(7)))
    }


    @Test
    fun hasField_true() {
        val r = HxsRecord.build { putString(1, "x") }
        assertTrue(r.hasField(1))
    }

    @Test
    fun hasField_false() {
        val r = HxsRecord.build { }
        assertFalse(r.hasField(1))
    }

    @Test
    fun removeField_removesIt() {
        val r = HxsRecord.build { putString(1, "x") }
        r.removeField(1)
        assertFalse(r.hasField(1))
        assertEquals("default", r.getString(1, "default"))
    }

    @Test
    fun tags_returnsAllTags() {
        val r = HxsRecord.build {
            putString(1, "a")
            putInt(5, 10)
            putBool(10, true)
        }
        assertEquals(setOf(1, 5, 10), r.tags())
    }

    @Test
    fun tags_emptyRecord() {
        val r = HxsRecord.build { }
        assertTrue(r.tags().isEmpty())
    }


    @Test
    fun putString_overwritesPreviousValue() {
        val r = HxsRecord.build { putString(1, "old") }
        r.putString(1, "new")
        assertEquals("new", r.getString(1))
    }


    @Test
    fun build_withId() {
        val r = HxsRecord.build(42) { putString(1, "x") }
        assertEquals(42, r.id)
    }

    @Test
    fun build_defaultIdIsZero() {
        val r = HxsRecord.build { }
        assertEquals(0, r.id)
    }

    @Test
    fun flags_defaultZero() {
        val r = HxsRecord.build { }
        assertEquals(0, r.flags)
    }


    @Test
    fun encodeDecode_roundTrip() {
        val original = HxsRecord.build(7) {
            putString(1, "hello")
            putInt(2, -999)
            putBool(3, true)
            putTimestamp(4, 1700000000000L)
            putFloat(5, 3.14f)
            putDouble(6, 2.718)
            putBytes(7, byteArrayOf(0xDE.toByte(), 0xAD.toByte()))
        }

        val encoded = original.encode()
        val decoded = HxsRecord.decode(encoded)

        assertEquals(7, decoded.id)
        assertEquals("hello", decoded.getString(1))
        assertEquals(-999L, decoded.getInt(2))
        assertTrue(decoded.getBool(3))
        assertEquals(1700000000000L, decoded.getTimestamp(4))
        assertEquals(3.14f, decoded.getFloat(5), 0.001f)
        assertEquals(2.718, decoded.getDouble(6), 0.001)
        assertTrue(byteArrayOf(0xDE.toByte(), 0xAD.toByte()).contentEquals(decoded.getBytes(7)))
    }

    @Test
    fun encodeDecode_emptyRecord() {
        val original = HxsRecord.build { }
        val decoded = HxsRecord.decode(original.encode())
        assertTrue(decoded.tags().isEmpty())
    }

    @Test
    fun decode_tooShortReturnsEmpty() {
        val decoded = HxsRecord.decode(byteArrayOf(0x01, 0x02))
        assertTrue(decoded.tags().isEmpty())
    }

    @Test
    fun decode_wrongMagicReturnsEmpty() {
        val badData = ByteArray(16)
        val decoded = HxsRecord.decode(badData)
        assertTrue(decoded.tags().isEmpty())
    }

    @Test
    fun encode_containsMagicHeader() {
        val r = HxsRecord.build { putString(1, "x") }
        val encoded = r.encode()
        // HXSR magic = 0x48585352 little-endian
        assertEquals(0x52, encoded[0].toInt() and 0xFF)
        assertEquals(0x53, encoded[1].toInt() and 0xFF)
        assertEquals(0x58, encoded[2].toInt() and 0xFF)
        assertEquals(0x48, encoded[3].toInt() and 0xFF)
    }
}
