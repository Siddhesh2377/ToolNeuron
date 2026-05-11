package com.dark.plugins.expense

import java.io.File
import java.text.Normalizer

internal class WordPieceTokenizer(
    private val vocab: Map<String, Int>,
    private val unkId: Int,
    private val clsId: Int,
    private val sepId: Int,
    private val padId: Int,
    private val maxInputCharsPerWord: Int = 100,
) {

    data class Encoded(
        val inputIds: LongArray,
        val attentionMask: LongArray,
        val tokenTypeIds: LongArray,
    )

    fun encode(text: String, maxLen: Int): Encoded {
        val tokens = ArrayList<Int>(maxLen)
        tokens.add(clsId)
        for (word in basicSplit(text)) {
            val pieces = wordPiece(word)
            for (p in pieces) {
                if (tokens.size >= maxLen - 1) break
                tokens.add(p)
            }
            if (tokens.size >= maxLen - 1) break
        }
        tokens.add(sepId)

        val inputIds = LongArray(maxLen) { padId.toLong() }
        val attention = LongArray(maxLen)
        val tokenType = LongArray(maxLen)
        for (i in tokens.indices) {
            inputIds[i] = tokens[i].toLong()
            attention[i] = 1L
        }
        return Encoded(inputIds, attention, tokenType)
    }

    private fun basicSplit(raw: String): List<String> {
        val cleaned = clean(raw)
        val lowered = cleaned.lowercase()
        val stripped = stripAccents(lowered)
        val out = ArrayList<String>()
        val buf = StringBuilder()
        for (ch in stripped) {
            when {
                ch.isWhitespace() -> {
                    if (buf.isNotEmpty()) {
                        out.add(buf.toString())
                        buf.setLength(0)
                    }
                }
                isPunctuation(ch) -> {
                    if (buf.isNotEmpty()) {
                        out.add(buf.toString())
                        buf.setLength(0)
                    }
                    out.add(ch.toString())
                }
                else -> buf.append(ch)
            }
        }
        if (buf.isNotEmpty()) out.add(buf.toString())
        return out
    }

    private fun clean(text: String): String {
        val sb = StringBuilder(text.length)
        for (ch in text) {
            val code = ch.code
            if (code == 0 || code == 0xFFFD || isControl(ch)) continue
            sb.append(if (ch == '\t' || ch == '\n' || ch == '\r') ' ' else ch)
        }
        return sb.toString()
    }

    private fun stripAccents(s: String): String {
        val nfd = Normalizer.normalize(s, Normalizer.Form.NFD)
        val sb = StringBuilder(nfd.length)
        for (ch in nfd) {
            if (Character.getType(ch) != Character.NON_SPACING_MARK.toInt()) sb.append(ch)
        }
        return sb.toString()
    }

    private fun isControl(ch: Char): Boolean {
        if (ch == '\t' || ch == '\n' || ch == '\r') return false
        val cat = Character.getType(ch)
        return cat == Character.CONTROL.toInt() ||
            cat == Character.FORMAT.toInt() ||
            cat == Character.PRIVATE_USE.toInt() ||
            cat == Character.SURROGATE.toInt() ||
            cat == Character.UNASSIGNED.toInt()
    }

    private fun isPunctuation(ch: Char): Boolean {
        val code = ch.code
        if (code in 33..47 || code in 58..64 || code in 91..96 || code in 123..126) return true
        val cat = Character.getType(ch)
        return cat == Character.CONNECTOR_PUNCTUATION.toInt() ||
            cat == Character.DASH_PUNCTUATION.toInt() ||
            cat == Character.START_PUNCTUATION.toInt() ||
            cat == Character.END_PUNCTUATION.toInt() ||
            cat == Character.INITIAL_QUOTE_PUNCTUATION.toInt() ||
            cat == Character.FINAL_QUOTE_PUNCTUATION.toInt() ||
            cat == Character.OTHER_PUNCTUATION.toInt()
    }

    private fun wordPiece(word: String): List<Int> {
        if (word.length > maxInputCharsPerWord) return listOf(unkId)
        val out = ArrayList<Int>()
        var start = 0
        while (start < word.length) {
            var end = word.length
            var current = -1
            while (start < end) {
                val piece = if (start == 0) word.substring(start, end) else "##" + word.substring(start, end)
                val id = vocab[piece]
                if (id != null) {
                    current = id
                    break
                }
                end -= 1
            }
            if (current == -1) return listOf(unkId)
            out.add(current)
            start = end
        }
        return out
    }

    companion object {
        fun fromVocabFile(file: File): WordPieceTokenizer {
            val vocab = HashMap<String, Int>(32000)
            file.useLines { lines ->
                var idx = 0
                for (line in lines) {
                    vocab[line.trim()] = idx
                    idx += 1
                }
            }
            return WordPieceTokenizer(
                vocab = vocab,
                unkId = vocab["[UNK]"] ?: 100,
                clsId = vocab["[CLS]"] ?: 101,
                sepId = vocab["[SEP]"] ?: 102,
                padId = vocab["[PAD]"] ?: 0,
            )
        }
    }
}
