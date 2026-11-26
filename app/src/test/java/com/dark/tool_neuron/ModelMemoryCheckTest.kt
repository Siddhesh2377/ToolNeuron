package com.dark.tool_neuron

import com.dark.tool_neuron.service.inference.ModelMemoryCheck
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelMemoryCheckTest {

    private val gb = 1024L * 1024 * 1024

    @Test
    fun allowsModelWhenFreeMemoryIsLowButDeviceHasTheRam() {
        assertNull(ModelMemoryCheck.blockReason(modelSize = 5 * gb, memTotal = 16 * gb))
        assertTrue(ModelMemoryCheck.isTight(modelSize = 5 * gb, memAvail = 3 * gb))
    }

    @Test
    fun blocksModelLargerThanPhysicalRam() {
        val reason = ModelMemoryCheck.blockReason(modelSize = 12 * gb, memTotal = 8 * gb)
        assertNotNull(reason)
        assertTrue(reason!!.contains("${12 * 1024} MB"))
    }

    @Test
    fun skipsWhenSizesAreUnknown() {
        assertNull(ModelMemoryCheck.blockReason(modelSize = 0, memTotal = 8 * gb))
        assertNull(ModelMemoryCheck.blockReason(modelSize = 12 * gb, memTotal = -1))
        assertFalse(ModelMemoryCheck.isTight(modelSize = 5 * gb, memAvail = -1))
        assertFalse(ModelMemoryCheck.isTight(modelSize = 5 * gb, memAvail = 7 * gb))
    }
}
