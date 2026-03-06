package com.dark.tool_neuron.global

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import kotlinx.serialization.Serializable
import java.io.File

// ── CPU Topology Data ──

@Serializable
enum class ClusterTier { PRIME, PERFORMANCE, EFFICIENCY }

@Serializable
data class CpuCluster(
    val coreIndices: List<Int>,
    val maxFreqKHz: Long,
    val tier: ClusterTier
)

@Serializable
data class CpuTopology(
    val clusters: List<CpuCluster> = emptyList(),
    val totalPhysicalCores: Int = Runtime.getRuntime().availableProcessors(),
    val primeCoreCount: Int = 0,
    val performanceCoreCount: Int = 0,
    val efficiencyCoreCount: Int = 0,
    val scanSucceeded: Boolean = false
)

// ── Hardware Profile ──

@Serializable
data class HardwareProfile(
    val totalRamMB: Int,
    val availableRamMB: Int,
    val cpuCores: Int,
    val cpuArch: String,
    val isLowRamDevice: Boolean,
    val sdkVersion: Int,
    val deviceModel: String,
    val scanTimestamp: Long,
    val cpuTopology: CpuTopology = CpuTopology()
)

// ── Scanner ──

object HardwareScanner {

    fun scan(context: Context): HardwareProfile {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        am.getMemoryInfo(memInfo)
        val topology = scanCpuTopology()

        return HardwareProfile(
            totalRamMB = (memInfo.totalMem / (1024 * 1024)).toInt(),
            availableRamMB = (memInfo.availMem / (1024 * 1024)).toInt(),
            cpuCores = topology.totalPhysicalCores,
            cpuArch = Build.SUPPORTED_ABIS.firstOrNull() ?: "unknown",
            isLowRamDevice = am.isLowRamDevice,
            sdkVersion = Build.VERSION.SDK_INT,
            deviceModel = "${Build.MANUFACTURER} ${Build.MODEL}",
            scanTimestamp = System.currentTimeMillis(),
            cpuTopology = topology
        )
    }

    // ── Sysfs Topology Scanner ──

    private fun scanCpuTopology(): CpuTopology {
        return try {
            val presentFile = File("/sys/devices/system/cpu/present")
            if (!presentFile.exists() || !presentFile.canRead()) {
                return fallbackTopology()
            }

            val coreRange = parseCpuRange(presentFile.readText().trim())
            val totalCores = coreRange.size

            // Read max frequency for each core
            val coreFreqs = mutableMapOf<Int, Long>()
            for (coreId in coreRange) {
                val freqFile = File("/sys/devices/system/cpu/cpu$coreId/cpufreq/cpuinfo_max_freq")
                if (freqFile.exists() && freqFile.canRead()) {
                    freqFile.readText().trim().toLongOrNull()?.let { coreFreqs[coreId] = it }
                }
            }

            if (coreFreqs.isEmpty()) return fallbackTopology(totalCores)

            // Group by frequency (highest first)
            val grouped = coreFreqs.entries
                .groupBy({ it.value }, { it.key })
                .toSortedMap(compareByDescending { it })

            val sortedFreqs = grouped.keys.toList()

            val clusters = sortedFreqs.mapIndexed { index, freq ->
                val tier = when {
                    sortedFreqs.size == 1 -> ClusterTier.PERFORMANCE
                    sortedFreqs.size == 2 -> if (index == 0) ClusterTier.PRIME else ClusterTier.EFFICIENCY
                    else -> when (index) {
                        0 -> ClusterTier.PRIME
                        sortedFreqs.lastIndex -> ClusterTier.EFFICIENCY
                        else -> ClusterTier.PERFORMANCE
                    }
                }
                CpuCluster(
                    coreIndices = grouped[freq]!!.sorted(),
                    maxFreqKHz = freq,
                    tier = tier
                )
            }

            // Cores without readable frequency → efficiency fallback
            val mappedCores = coreFreqs.keys
            val unmappedCores = coreRange.filter { it !in mappedCores }
            val finalClusters = if (unmappedCores.isNotEmpty()) {
                clusters + CpuCluster(unmappedCores, 0L, ClusterTier.EFFICIENCY)
            } else clusters

            CpuTopology(
                clusters = finalClusters,
                totalPhysicalCores = totalCores,
                primeCoreCount = finalClusters.filter { it.tier == ClusterTier.PRIME }.sumOf { it.coreIndices.size },
                performanceCoreCount = finalClusters.filter { it.tier == ClusterTier.PERFORMANCE }.sumOf { it.coreIndices.size },
                efficiencyCoreCount = finalClusters.filter { it.tier == ClusterTier.EFFICIENCY }.sumOf { it.coreIndices.size },
                scanSucceeded = true
            )
        } catch (_: Exception) {
            fallbackTopology()
        }
    }

    private fun fallbackTopology(cores: Int = Runtime.getRuntime().availableProcessors()): CpuTopology {
        return CpuTopology(totalPhysicalCores = cores)
    }

    private fun parseCpuRange(text: String): List<Int> {
        return text.split(",").flatMap { part ->
            if ("-" in part) {
                val (start, end) = part.split("-").map { it.trim().toInt() }
                (start..end).toList()
            } else {
                listOf(part.trim().toInt())
            }
        }
    }
}
