package com.dark.tool_neuron.global

import org.junit.Assert.assertEquals
import org.junit.Test

class DeviceTunerTest {

    private fun createEliteTopology(): CpuTopology {
        val primeCluster = CpuCluster(listOf(0, 1), 4300000L, ClusterTier.PRIME)
        val perfCluster = CpuCluster(listOf(2, 3, 4, 5, 6, 7), 3500000L, ClusterTier.PERFORMANCE)
        return CpuTopology(
            clusters = listOf(primeCluster, perfCluster),
            totalPhysicalCores = 8,
            primeCoreCount = 2,
            performanceCoreCount = 6,
            efficiencyCoreCount = 0,
            scanSucceeded = true
        )
    }

    private fun createEliteProfile(ramMB: Int = 12288): HardwareProfile {
        return HardwareProfile(
            totalRamMB = ramMB,
            availableRamMB = ramMB - 2000,
            cpuCores = 8,
            cpuArch = "arm64-v8a",
            isLowRamDevice = false,
            sdkVersion = 35,
            deviceModel = "Samsung SM-S938B",
            scanTimestamp = System.currentTimeMillis(),
            cpuTopology = createEliteTopology()
        )
    }

    @Test
    fun testEliteTuningPerformanceMode() {
        // High RAM for Elite
        val profile = createEliteProfile(16384)
        val params = DeviceTuner.tune(profile, modelSizeMB = 4000, modelName = "Llama-3-8B-Q4_K_M.gguf", mode = PerformanceMode.PERFORMANCE)

        assertEquals("Threads mismatch", 7, params.threads)
        assertEquals("Batch size mismatch", 1024, params.batchSize)
        assertEquals("Cache type mismatch", 1, params.cacheTypeK)
    }

    @Test
    fun testEliteTuningBalancedMode() {
        val profile = createEliteProfile(12288)
        val params = DeviceTuner.tune(profile, modelSizeMB = 4000, modelName = "Llama-3-8B-Q4_K_M.gguf", mode = PerformanceMode.BALANCED)

        assertEquals("Threads mismatch", 4, params.threads)
        assertEquals("Batch size mismatch", 512, params.batchSize)
        assertEquals("Cache type mismatch", 9, params.cacheTypeK)
    }

    @Test
    fun testLegacyTopologyTuning() {
        val primeCluster = CpuCluster(listOf(0), 3000000L, ClusterTier.PRIME)
        val perfCluster = CpuCluster(listOf(1, 2, 3), 2500000L, ClusterTier.PERFORMANCE)
        val effCluster = CpuCluster(listOf(4, 5, 6, 7), 1800000L, ClusterTier.EFFICIENCY)
        val topo = CpuTopology(
            clusters = listOf(primeCluster, perfCluster, effCluster),
            totalPhysicalCores = 8,
            primeCoreCount = 1,
            performanceCoreCount = 3,
            efficiencyCoreCount = 4,
            scanSucceeded = true
        )
        val profile = HardwareProfile(12288, 8000, 8, "arm64", false, 31, "Pixel 7", 0L, topo)

        val params = DeviceTuner.tune(profile, 4000, "model.gguf", PerformanceMode.PERFORMANCE)

        assertEquals("Threads mismatch", 4, params.threads)
        assertEquals("Batch size mismatch", 512, params.batchSize)
    }
}
