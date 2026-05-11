package com.dark.tool_neuron.repo

object RagChunker {

    private const val DEFAULT_TARGET_CHARS = 1024
    private const val DEFAULT_MIN_CHARS = 200

    private val SEPARATORS = listOf("\n\n", "\n", ". ", "! ", "? ", "; ", ", ", " ")

    fun chunk(
        text: String,
        targetChars: Int = DEFAULT_TARGET_CHARS,
        minChars: Int = DEFAULT_MIN_CHARS,
    ): List<String> {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return emptyList()
        if (trimmed.length <= targetChars) return listOf(trimmed)

        val out = mutableListOf<String>()
        recursiveSplit(trimmed, 0, targetChars, minChars, out)
        return out.filter { it.isNotBlank() }.map { it.trim() }
    }

    private fun recursiveSplit(
        text: String,
        sepIdx: Int,
        target: Int,
        min: Int,
        out: MutableList<String>,
    ) {
        if (text.length <= target) {
            out += text
            return
        }
        if (sepIdx >= SEPARATORS.size) {
            var i = 0
            while (i < text.length) {
                val end = (i + target).coerceAtMost(text.length)
                out += text.substring(i, end)
                i = end
            }
            return
        }
        val sep = SEPARATORS[sepIdx]
        val parts = text.split(sep)
        if (parts.size <= 1) {
            recursiveSplit(text, sepIdx + 1, target, min, out)
            return
        }
        val merged = mutableListOf<String>()
        val current = StringBuilder()
        parts.forEachIndexed { idx, part ->
            val piece = if (idx == 0) part else sep + part
            if (current.isNotEmpty() && current.length + piece.length > target && current.length >= min) {
                merged += current.toString()
                current.clear()
                current.append(if (sep.startsWith("\n")) part else part)
            } else {
                current.append(piece)
            }
        }
        if (current.isNotEmpty()) merged += current.toString()
        merged.forEach { piece ->
            if (piece.length <= target) out += piece
            else recursiveSplit(piece, sepIdx + 1, target, min, out)
        }
    }
}
