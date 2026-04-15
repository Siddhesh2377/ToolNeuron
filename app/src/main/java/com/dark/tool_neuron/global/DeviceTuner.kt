package com.dark.tool_neuron.global

import android.app.ActivityManager
import android.content.Context
import com.dark.tool_neuron.models.engine_schema.GgufLoadingParams

// ── Performance Mode ──

enum class PerformanceMode {
    PERFORMANCE,
    BALANCED,
    POWER_SAVING
}

// ── Quantization Levels ──

enum class QuantLevel(val bitsPerWeight: Float, val kvBytesPerToken: Float) {
    Q2(2.5f, 0.25f),
    Q3(3.5f, 0.35f),
    Q4(4.5f, 0.45f),
    Q5(5.5f, 0.55f),
    Q6(6.5f, 0.65f),
    Q8(8.0f, 0.80f),
    F16(16.0f, 1.5f),
    UNKNOWN(4.5f, 0.45f);

    companion object {
        fun fromModelName(name: String): QuantLevel {
            val upper = name.uppercase()
            return when {
                upper.contains("F16") || upper.contains("FP16") -> F16
                upper.contains("Q8") -> Q8
                upper.contains("Q6") -> Q6
                upper.contains("Q5") -> Q5
                upper.contains("Q4") -> Q4
                upper.contains("Q3") -> Q3
                upper.contains("Q2") || upper.contains("IQ2") -> Q2
                else -> UNKNOWN
            }
        }
    }
}

// ── Device Tuner ──

object DeviceTuner {

    fun tune(
        profile: HardwareProfile,
        modelSizeMB: Int,
        modelName: String = "",
        mode: PerformanceMode = PerformanceMode.BALANCED
    ): GgufLoadingParams {
        val quant = QuantLevel.fromModelName(modelName)
        val topo = profile.cpuTopology

        // ── Thread selection ──
        val threads = if (topo.scanSucceeded) {
            clusterAwareThreads(topo, mode)
        } else {
            fallbackThreads(profile.cpuCores, mode)
        }

        // ── RAM budget ──
        val reserveRatio = when (mode) {
            PerformanceMode.PERFORMANCE -> 0.65
            PerformanceMode.BALANCED -> 0.60
            PerformanceMode.POWER_SAVING -> 0.50
        }
        val usableRamMB = (profile.totalRamMB * reserveRatio).toInt()
        val ramAfterModelMB = (usableRamMB - modelSizeMB).coerceAtLeast(256)

        // ── Context size ──
        val ctxCap = when (mode) {
            PerformanceMode.PERFORMANCE -> 32768
            PerformanceMode.BALANCED -> 16384
            PerformanceMode.POWER_SAVING -> 8192
        }
        val kvBudgetKB = ramAfterModelMB * 0.50 * 1024
        val rawCtx = (kvBudgetKB / quant.kvBytesPerToken).toInt()
        val ctxSize = ((rawCtx / 512) * 512).coerceIn(512, ctxCap)

        // ── Batch size ──
        val batchCap = when (mode) {
            PerformanceMode.PERFORMANCE -> 512
            PerformanceMode.BALANCED -> 512
            PerformanceMode.POWER_SAVING -> 256
        }
        val rawBatch = (ramAfterModelMB / 4).coerceIn(64, batchCap)
        val batchSize = (rawBatch / 64) * 64

        // ── KV cache quantization ──
        val ramRatio = ramAfterModelMB.toFloat() / modelSizeMB.coerceAtLeast(1)
        val cacheType = when {
            ramRatio < 0.5f -> 10  // Q4_0
            ramRatio < 1.0f -> 12  // Q5_0
            else -> 9              // Q8_0
        }

        val useMlock = usableRamMB > 6000

        return GgufLoadingParams(
            threads = threads,
            ctxSize = ctxSize,
            batchSize = batchSize,
            useMmap = true,
            useMlock = useMlock,
            flashAttn = true,
            cacheTypeK = cacheType,
            cacheTypeV = cacheType
        )
    }

    fun recommendContextSize(context: Context, modelSizeMB: Int, modelName: String = ""): Int {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        am.getMemoryInfo(memInfo)
        val availMB = (memInfo.availMem / (1024 * 1024)).toInt()
        val afterModel = (availMB - modelSizeMB).coerceAtLeast(256)
        val quant = QuantLevel.fromModelName(modelName)
        val kvBudgetKB = afterModel * 0.50 * 1024
        val raw = (kvBudgetKB / quant.kvBytesPerToken).toInt().coerceIn(512, 32768)
        return (raw / 512) * 512
    }

    fun recommendMaxTokens(ctxSize: Int): Int {
        return (ctxSize / 2).coerceIn(256, 4096)
    }

    // ── Private Helpers ──

    private fun clusterAwareThreads(topo: CpuTopology, mode: PerformanceMode): Int {
        val bigCores = topo.primeCoreCount + topo.performanceCoreCount
        return when (mode) {
            PerformanceMode.PERFORMANCE -> {
                bigCores.coerceIn(2, topo.totalPhysicalCores)
            }
            PerformanceMode.BALANCED -> {
                // Use all big cores if 4 or fewer; otherwise just performance tier
                if (bigCores <= 4) {
                    bigCores.coerceAtLeast(2)
                } else {
                    topo.performanceCoreCount.coerceAtLeast(2)
                }
            }
            PerformanceMode.POWER_SAVING -> 2
        }
    }

    private fun fallbackThreads(cpuCores: Int, mode: PerformanceMode): Int {
        return when (mode) {
            PerformanceMode.PERFORMANCE -> ((cpuCores * 0.75).toInt()).coerceIn(2, 8)
            PerformanceMode.BALANCED -> ((cpuCores * 0.50).toInt()).coerceIn(2, 6)
            PerformanceMode.POWER_SAVING -> 2
        }
    }
}
