package com.dark.tool_neuron.repo.research

internal object ResearchUrlUtil {

    private val ARXIV_ABS = Regex("""^(https?://(?:www\.)?arxiv\.org)/abs/([^?#\s/]+)/?(?:[?#].*)?$""", RegexOption.IGNORE_CASE)

    fun canonicalize(url: String): String {
        val m = ARXIV_ABS.matchEntire(url.trim()) ?: return url
        val base = m.groupValues[1]
        val id = m.groupValues[2]
        return "$base/pdf/$id.pdf"
    }

    fun looksBinaryDoc(url: String, contentType: String? = null): Boolean {
        val ct = contentType?.lowercase().orEmpty()
        if (ct.startsWith("application/pdf")) return true
        if (ct.startsWith("application/epub")) return true
        if (ct.contains("officedocument") || ct.contains("opendocument")) return true
        val lower = url.lowercase()
        val tail = lower.substringAfterLast('/')
        return tail.endsWith(".pdf") ||
            tail.endsWith(".epub") ||
            tail.endsWith(".docx") ||
            tail.endsWith(".odt") ||
            tail.endsWith(".rtf")
    }

    fun nameHintFrom(url: String): String {
        val tail = url.substringAfterLast('/').substringBefore('?').substringBefore('#')
        return tail.ifBlank { "document" }
    }
}
