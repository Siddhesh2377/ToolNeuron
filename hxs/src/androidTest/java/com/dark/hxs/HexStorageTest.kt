package com.dark.hxs

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class HexStorageTest {

    private lateinit var storage: HexStorage
    private lateinit var basePath: String

    @Before
    fun setup() {
        storage = HexStorage()
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        val dir = ctx.filesDir.resolve("hxs_test_${System.nanoTime()}")
        dir.mkdirs()
        basePath = dir.absolutePath
        assertTrue(storage.createPlaintext(basePath))
    }

    @After
    fun teardown() {
        storage.close()
        File(basePath).deleteRecursively()
    }


    @Test
    fun exists_returnsTrueAfterCreate() {
        assertTrue(storage.exists(basePath))
    }

    @Test
    fun exists_returnsFalseForNonExistent() {
        assertFalse(storage.exists("/tmp/does_not_exist_hxs"))
    }

    @Test
    fun openPlaintext_afterCreate() {
        storage.close()
        val s2 = HexStorage()
        assertTrue(s2.openPlaintext(basePath))
        s2.close()
    }


    @Test
    fun ensureCollection_createsCollection() {
        assertTrue(storage.ensureCollection("test"))
        val collections = storage.listCollections()
        assertTrue(collections.contains("test"))
    }

    @Test
    fun ensureCollection_idempotent() {
        assertTrue(storage.ensureCollection("col"))
        assertTrue(storage.ensureCollection("col"))
        assertEquals(1, storage.listCollections().count { it == "col" })
    }

    @Test
    fun listCollections_multipleCollections() {
        storage.ensureCollection("alpha")
        storage.ensureCollection("beta")
        storage.ensureCollection("gamma")
        val list = storage.listCollections()
        assertTrue(list.containsAll(listOf("alpha", "beta", "gamma")))
    }

    @Test
    fun dropCollection_removesIt() {
        storage.ensureCollection("temp")
        assertTrue(storage.listCollections().contains("temp"))
        storage.dropCollection("temp")
        assertFalse(storage.listCollections().contains("temp"))
    }


    @Test
    fun put_returnsPositiveId() {
        storage.ensureCollection("c")
        val record = HxsRecord.build { putString(1, "hello") }
        val id = storage.put("c", record)
        assertTrue(id > 0)
    }

    @Test
    fun get_returnsRecordById() {
        storage.ensureCollection("c")
        val record = HxsRecord.build {
            putString(1, "world")
            putInt(2, 42)
        }
        val id = storage.put("c", record)
        val fetched = storage.get("c", id)
        assertNotNull(fetched)
        assertEquals("world", fetched!!.getString(1))
        assertEquals(42L, fetched.getInt(2))
    }

    @Test
    fun get_nonExistentReturnsNull() {
        storage.ensureCollection("c")
        assertNull(storage.get("c", 99999))
    }

    @Test
    fun put_multipleSameCollection() {
        storage.ensureCollection("c")
        val id1 = storage.put("c", HxsRecord.build { putString(1, "a") })
        val id2 = storage.put("c", HxsRecord.build { putString(1, "b") })
        val id3 = storage.put("c", HxsRecord.build { putString(1, "c") })
        assertNotEquals(id1, id2)
        assertNotEquals(id2, id3)
        assertEquals("a", storage.get("c", id1)?.getString(1))
        assertEquals("b", storage.get("c", id2)?.getString(1))
        assertEquals("c", storage.get("c", id3)?.getString(1))
    }


    @Test
    fun update_modifiesExistingRecord() {
        storage.ensureCollection("c")
        val record = HxsRecord.build { putString(1, "original") }
        val id = storage.put("c", record)
        val fetched = storage.get("c", id)!!
        fetched.putString(1, "updated")
        assertTrue(storage.update("c", fetched))
        assertEquals("updated", storage.get("c", id)?.getString(1))
    }


    @Test
    fun delete_removesRecord() {
        storage.ensureCollection("c")
        val id = storage.put("c", HxsRecord.build { putString(1, "bye") })
        assertTrue(storage.delete("c", id))
        assertNull(storage.get("c", id))
    }

    @Test
    fun delete_nonExistentReturnsFalse() {
        storage.ensureCollection("c")
        assertFalse(storage.delete("c", 99999))
    }


    @Test
    fun count_emptyCollection() {
        storage.ensureCollection("c")
        assertEquals(0, storage.count("c"))
    }

    @Test
    fun count_afterPuts() {
        storage.ensureCollection("c")
        storage.put("c", HxsRecord.build { putString(1, "a") })
        storage.put("c", HxsRecord.build { putString(1, "b") })
        storage.put("c", HxsRecord.build { putString(1, "c") })
        assertEquals(3, storage.count("c"))
    }

    @Test
    fun count_afterDelete() {
        storage.ensureCollection("c")
        val id = storage.put("c", HxsRecord.build { putString(1, "a") })
        storage.put("c", HxsRecord.build { putString(1, "b") })
        assertEquals(2, storage.count("c"))
        storage.delete("c", id)
        assertEquals(1, storage.count("c"))
    }


    @Test
    fun getAll_returnsAllRecords() {
        storage.ensureCollection("c")
        storage.put("c", HxsRecord.build { putString(1, "x") })
        storage.put("c", HxsRecord.build { putString(1, "y") })
        val all = storage.getAll("c")
        assertEquals(2, all.size)
        val values = all.map { it.getString(1) }.toSet()
        assertTrue(values.containsAll(setOf("x", "y")))
    }

    @Test
    fun getAll_emptyCollection() {
        storage.ensureCollection("c")
        assertTrue(storage.getAll("c").isEmpty())
    }


    @Test
    fun queryString_findsMatchingRecords() {
        storage.ensureCollection("c")
        storage.addIndex("c", 1, HexStorage.WIRE_BYTES)
        storage.put("c", HxsRecord.build { putString(1, "alice"); putInt(2, 10) })
        storage.put("c", HxsRecord.build { putString(1, "bob"); putInt(2, 20) })
        storage.put("c", HxsRecord.build { putString(1, "alice"); putInt(2, 30) })

        val results = storage.queryString("c", 1, "alice")
        assertEquals(2, results.size)
        assertTrue(results.all { it.getString(1) == "alice" })
    }

    @Test
    fun queryString_noMatch() {
        storage.ensureCollection("c")
        storage.addIndex("c", 1, HexStorage.WIRE_BYTES)
        storage.put("c", HxsRecord.build { putString(1, "alice") })
        val results = storage.queryString("c", 1, "nobody")
        assertTrue(results.isEmpty())
    }

    @Test
    fun queryInt_findsMatch() {
        storage.ensureCollection("c")
        storage.addIndex("c", 2, HexStorage.WIRE_VARINT)
        storage.put("c", HxsRecord.build { putString(1, "a"); putInt(2, 100) })
        storage.put("c", HxsRecord.build { putString(1, "b"); putInt(2, 200) })
        storage.put("c", HxsRecord.build { putString(1, "c"); putInt(2, 100) })

        val results = storage.queryInt("c", 2, 100)
        assertEquals(2, results.size)
    }

    @Test
    fun queryRange_findsInRange() {
        storage.ensureCollection("c")
        storage.addIndex("c", 1, HexStorage.WIRE_FIXED64)
        for (i in 1..10) {
            storage.put("c", HxsRecord.build { putTimestamp(1, i.toLong()) })
        }
        val results = storage.queryRange("c", 1, 3, 7)
        assertEquals(5, results.size)
        assertTrue(results.all { it.getTimestamp(1) in 3..7 })
    }

    @Test
    fun queryRange_emptyRange() {
        storage.ensureCollection("c")
        storage.addIndex("c", 1, HexStorage.WIRE_FIXED64)
        storage.put("c", HxsRecord.build { putTimestamp(1, 5) })
        val results = storage.queryRange("c", 1, 100, 200)
        assertTrue(results.isEmpty())
    }

    @Test
    fun removeIndex_doesNotCrash() {
        storage.ensureCollection("c")
        storage.addIndex("c", 1, HexStorage.WIRE_BYTES)
        storage.put("c", HxsRecord.build { putString(1, "test") })
        assertEquals(1, storage.queryString("c", 1, "test").size)
        storage.removeIndex("c", 1)
        // After removing, query may still work via fallback scan or return empty
        // Just verify it doesn't crash
        storage.queryString("c", 1, "test")
    }


    @Test
    fun flushAll_dataPersistedAcrossReopen() {
        storage.ensureCollection("persist")
        storage.put("persist", HxsRecord.build {
            putString(1, "saved")
            putInt(2, 777)
        })
        storage.flushAll()
        storage.close()

        val s2 = HexStorage()
        assertTrue(s2.openPlaintext(basePath))
        assertEquals(1, s2.count("persist"))
        val record = s2.getAll("persist").first()
        assertEquals("saved", record.getString(1))
        assertEquals(777L, record.getInt(2))
        s2.close()
    }

    @Test
    fun flush_singleCollection() {
        storage.ensureCollection("a")
        storage.put("a", HxsRecord.build { putString(1, "val") })
        storage.flush("a")
        storage.close()

        val s2 = HexStorage()
        assertTrue(s2.openPlaintext(basePath))
        assertEquals(1, s2.count("a"))
        s2.close()
    }


    @Test
    fun schemaVersion_defaultsToZero() {
        storage.ensureCollection("c")
        assertEquals(0, storage.getSchemaVersion("c"))
    }

    @Test
    fun schemaVersion_setAndGet() {
        storage.ensureCollection("c")
        storage.setSchemaVersion("c", 5)
        assertEquals(5, storage.getSchemaVersion("c"))
    }

    @Test
    fun schemaVersion_setAndGetInSameSession() {
        storage.ensureCollection("c")
        storage.setSchemaVersion("c", 3)
        assertEquals(3, storage.getSchemaVersion("c"))
        storage.setSchemaVersion("c", 7)
        assertEquals(7, storage.getSchemaVersion("c"))
    }
}
