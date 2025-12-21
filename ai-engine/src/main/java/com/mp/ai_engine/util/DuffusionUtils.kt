package com.mp.ai_engine.util

import android.os.Build

fun getDeviceSoc(): String {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        Build.SOC_MODEL
    } else {
        "CPU"
    }
}

private val chipsetModelSuffixes = mapOf(
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

fun getChipsetSuffix(soc: String): String? {
    if (soc in chipsetModelSuffixes) {
        return chipsetModelSuffixes[soc]
    }
    if (soc.startsWith("SM")) {
        return "min"
    }
    return null
}