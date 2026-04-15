package com.dark.tool_neuron.utils

enum class SizeCategory {
    SMALL,    // < 1GB
    MEDIUM,   // 1-5GB
    LARGE;    // > 5GB

    val displayName: String
        get() = when (this) {
            SMALL -> "Small (<1GB)"
            MEDIUM -> "Medium (1-5GB)"
            LARGE -> "Large (>5GB)"
        }
}

object ModelMetadataExtractor {
    private val parameterRegex = Regex("""(\d+\.?\d*)B""", RegexOption.IGNORE_CASE)
    private val quantizationRegex = Regex("""Q(\d+_\d+|[0-9]+_K_[MS]|[0-9]+)""", RegexOption.IGNORE_CASE)

    /**
     * Extracts parameter count from model name (e.g., "8B", "0.5B", "32B")
     * Returns null if no parameter count found
     */
    fun extractParameterCount(name: String): String? {
        val match = parameterRegex.find(name)
        return match?.groupValues?.get(1)?.let { "${it}B" }
    }

    /**
     * Extracts quantization level from filename (e.g., "Q4_0", "Q5_K_M")
     * Returns null if no quantization found
     */
    fun extractQuantization(fileName: String): String? {
        val match = quantizationRegex.find(fileName)
        return match?.value?.uppercase()
    }

    /**
     * Extracts size category from size string (e.g., "500 MB" -> SMALL, "3 GB" -> MEDIUM)
     * Returns SizeCategory.MEDIUM as default for unparseable sizes
     */
    fun extractSizeCategory(sizeStr: String): SizeCategory {
        val sizeInBytes = parseSizeToBytes(sizeStr)
        return when {
            sizeInBytes < 1_000_000_000L -> SizeCategory.SMALL   // < 1GB
            sizeInBytes <= 5_000_000_000L -> SizeCategory.MEDIUM // 1-5GB
            else -> SizeCategory.LARGE                            // > 5GB
        }
    }

    /**
     * Parses size string to bytes for comparison
     * Supports formats like "500 MB", "3 GB", "1.5 GB"
     */
    fun parseSizeToBytes(sizeStr: String): Long {
        val cleanedSize = sizeStr.trim().uppercase()
        val parts = cleanedSize.split(" ")

        if (parts.size < 2) return 0L

        val value = parts[0].toDoubleOrNull() ?: return 0L
        val unit = parts[1]

        return when (unit) {
            "B", "BYTES" -> value.toLong()
            "KB" -> (value * 1_024).toLong()
            "MB" -> (value * 1_024 * 1_024).toLong()
            "GB" -> (value * 1_024 * 1_024 * 1_024).toLong()
            "TB" -> (value * 1_024 * 1_024 * 1_024 * 1_024).toLong()
            else -> 0L
        }
    }

}
