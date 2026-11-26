package com.dark.tool_neuron.service.inference

internal object ModelMemoryCheck {

    private const val MB = 1024L * 1024L

    // MemAvailable under-reports on Android (lmkd reclaims cached apps and the GGUF
    // is a file-backed mmap), so only a model larger than physical RAM is refused.
    fun blockReason(modelSize: Long, memTotal: Long): String? {
        if (modelSize <= 0 || memTotal <= 0) return null
        if (modelSize <= memTotal) return null
        return "Model is larger than this device's memory: it needs ~${modelSize / MB} MB, " +
            "the device has ${memTotal / MB} MB in total. Pick a smaller quant."
    }

    fun isTight(modelSize: Long, memAvail: Long): Boolean =
        modelSize > 0 && memAvail in 1 until (modelSize * 6 / 5)
}
