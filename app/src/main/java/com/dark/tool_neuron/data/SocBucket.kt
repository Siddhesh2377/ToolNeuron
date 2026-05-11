package com.dark.tool_neuron.data

import android.os.Build

object SocBucket {

    private val CHIPSET_TO_SUFFIX = mapOf(
        "SM8475" to "8gen1",
        "SM8450" to "8gen1",

        "SM8550" to "8gen2",
        "SM8550P" to "8gen2",
        "QCS8550" to "8gen2",
        "QCM8550" to "8gen2",
        "SM8650" to "8gen2",
        "SM8650P" to "8gen2",
        "SM8750" to "8gen2",
        "SM8750P" to "8gen2",
        "SM8850" to "8gen2",
        "SM8850P" to "8gen2",
        "SM8735" to "8gen2",
        "SM8845" to "8gen2",
    )

    private val SDXL_ELIGIBLE_SOCS = setOf(
        "SM8650", "SM8845", "SM8750", "SM8750P", "SM8850", "SM8850P",
    )

    private val QUALCOMM_PREFIXES = listOf(
        "SM", "QCS", "QCM", "CQ", "IPQ", "SXR", "AIC", "SSG",
        "SC", "SA", "SDM", "MSM", "QRB", "X1E", "X1P",
    )

    fun socModel(): String =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) Build.SOC_MODEL else "CPU"

    fun bucket(soc: String = socModel()): String? {
        CHIPSET_TO_SUFFIX[soc]?.let { return it }
        if (soc.startsWith("SM")) return "min"
        return null
    }

    fun isQualcommDevice(soc: String = socModel()): Boolean {
        val u = soc.uppercase()
        return QUALCOMM_PREFIXES.any { u.startsWith(it) }
    }

    fun supportsNpu(soc: String = socModel()): Boolean = bucket(soc) != null

    fun isSdxlCapable(soc: String = socModel()): Boolean = soc in SDXL_ELIGIBLE_SOCS
}
