package com.dark.tool_neuron.global

import org.junit.Assert.assertEquals
import org.junit.Test

class HardwareScannerTest {

    @Test
    fun testEliteTopologyDetection() {
        val freqMap = mapOf(
            0 to 4300000L, 1 to 4300000L,
            2 to 3500000L, 3 to 3500000L, 4 to 3500000L, 5 to 3500000L, 6 to 3500000L, 7 to 3500000L
        )

        val sortedFreqs = listOf(4300000L, 3500000L)
        val grouped = mapOf(
            4300000L to listOf(0, 1),
            3500000L to listOf(2, 3, 4, 5, 6, 7)
        )

        val clusters = sortedFreqs.mapIndexed { index, freq ->
            val tier = when {
                sortedFreqs.size == 1 -> ClusterTier.PERFORMANCE
                sortedFreqs.size == 2 -> {
                    if (index == 0) ClusterTier.PRIME
                    else {
                        if (freq > 2400000) ClusterTier.PERFORMANCE else ClusterTier.EFFICIENCY
                    }
                }
                else -> ClusterTier.EFFICIENCY
            }
            CpuCluster(grouped[freq]!!, freq, tier)
        }

        assertEquals(ClusterTier.PRIME, clusters[0].tier)
        assertEquals(ClusterTier.PERFORMANCE, clusters[1].tier)
    }

    @Test
    fun testLegacyTopologyDetection() {
        val sortedFreqs = listOf(3000000L, 2500000L, 1800000L)

        val clusters = sortedFreqs.mapIndexed { index, freq ->
            val tier = when {
                sortedFreqs.size == 1 -> ClusterTier.PERFORMANCE
                sortedFreqs.size == 2 -> ClusterTier.EFFICIENCY
                else -> when (index) {
                    0 -> ClusterTier.PRIME
                    sortedFreqs.lastIndex -> {
                        if (freq > 2000000) ClusterTier.PERFORMANCE else ClusterTier.EFFICIENCY
                    }
                    else -> ClusterTier.PERFORMANCE
                }
            }
            CpuCluster(emptyList(), freq, tier)
        }

        assertEquals(ClusterTier.PRIME, clusters[0].tier)
        assertEquals(ClusterTier.PERFORMANCE, clusters[1].tier)
        assertEquals(ClusterTier.EFFICIENCY, clusters[2].tier)
    }
}
