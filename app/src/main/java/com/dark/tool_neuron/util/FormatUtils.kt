package com.dark.tool_neuron.util

fun formatBytes(bytes: Long): String = when {
    bytes <= 0 -> "Unknown"
    bytes < 1024 * 1024 -> "${bytes / 1024} KB"
    bytes < 1024L * 1024 * 1024 -> "%.1f MB".format(bytes / (1024.0 * 1024.0))
    else -> "%.2f GB".format(bytes / (1024.0 * 1024.0 * 1024.0))
}

fun extractParameterCount(name: String): String? {
    val match = Regex("""(\d+\.?\d*)\s*[Bb]""").find(name)
    return match?.groupValues?.get(0)?.uppercase()?.replace(" ", "")
}

fun extractQuantization(name: String): String? {
    val patterns = listOf(
        Regex("""[_-](Q\d[\w_]*)""", RegexOption.IGNORE_CASE),
        Regex("""[_-](IQ\d[\w_]*)""", RegexOption.IGNORE_CASE),
        Regex("""[_-]([Bb][Ff]16)"""),
        Regex("""[_-]([Ff]16|[Ff]32)"""),
    )
    for (p in patterns) {
        val match = p.find(name)
        if (match != null) return match.groupValues[1]
    }
    return null
}
