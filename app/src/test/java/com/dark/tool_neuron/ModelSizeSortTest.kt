package com.dark.tool_neuron

import com.dark.tool_neuron.model.HuggingFaceModel
import com.dark.tool_neuron.model.SizeCategory
import com.dark.tool_neuron.model.sortedBySize
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ModelSizeSortTest {

    private val gb = 1024L * 1024 * 1024

    private fun model(id: String, approx: String, bytes: Long = 0) = HuggingFaceModel(
        id = id, name = id, fileName = "$id.gguf", fileUri = "", approximateSize = approx, sizeBytes = bytes,
    )

    @Test
    fun parsesCommaDecimalLikeDotDecimal() {
        assertEquals(SizeCategory.parseSizeToBytes("1.25 GB"), SizeCategory.parseSizeToBytes("1,25 GB"))
        assertEquals((1.25 * gb).toLong(), SizeCategory.parseSizeToBytes("1,25 GB"))
    }

    @Test
    fun parsesApproximatePrefixAndUnits() {
        assertEquals(70L * 1024 * 1024, SizeCategory.parseSizeToBytes("~70 MB"))
        assertEquals(0L, SizeCategory.parseSizeToBytes("Unknown"))
    }

    @Test
    fun effectiveSizePrefersExactBytes() {
        assertEquals(5L, model("a", "9,99 GB", bytes = 5).effectiveSizeBytes)
        assertEquals(2 * gb, model("b", "2 GB").effectiveSizeBytes)
    }

    @Test
    fun sortsAscendingWithUnknownLast() {
        val sorted = listOf(
            model("unknown", "Unknown"),
            model("big", "4,20 GB", bytes = 4_500_000_000L),
            model("small", "700,5 MB", bytes = 734_000_000L),
            model("mid", "1,10 GB", bytes = 1_181_000_000L),
        ).sortedBySize().map { it.id }
        assertEquals(listOf("small", "mid", "big", "unknown"), sorted)
    }

    @Test
    fun categoriesFromBytes() {
        assertNull(SizeCategory.fromBytes(0))
        assertEquals(SizeCategory.SMALL, SizeCategory.fromBytes(gb - 1))
        assertEquals(SizeCategory.MEDIUM, SizeCategory.fromBytes(gb))
        assertEquals(SizeCategory.LARGE, SizeCategory.fromBytes(5 * gb))
    }
}
