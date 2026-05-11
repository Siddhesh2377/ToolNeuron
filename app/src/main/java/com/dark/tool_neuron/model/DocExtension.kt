package com.dark.tool_neuron.model

import androidx.compose.ui.graphics.Color

enum class DocExtension(
    val label: String,
    val mimeTypes: Set<String>,
    val extensions: Set<String>,
    val tint: Color,
) {
    PDF(
        label = "PDF",
        mimeTypes = setOf("application/pdf"),
        extensions = setOf("pdf"),
        tint = Color(0xFFE53935),
    ),
    DOCX(
        label = "DOCX",
        mimeTypes = setOf(
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/msword",
        ),
        extensions = setOf("docx", "doc"),
        tint = Color(0xFF1E88E5),
    ),
    XLSX(
        label = "XLSX",
        mimeTypes = setOf(
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "application/vnd.ms-excel",
        ),
        extensions = setOf("xlsx", "xls"),
        tint = Color(0xFF43A047),
    ),
    PPTX(
        label = "PPTX",
        mimeTypes = setOf(
            "application/vnd.openxmlformats-officedocument.presentationml.presentation",
            "application/vnd.ms-powerpoint",
        ),
        extensions = setOf("pptx", "ppt"),
        tint = Color(0xFFEF6C00),
    ),
    ODT(
        label = "ODT",
        mimeTypes = setOf("application/vnd.oasis.opendocument.text"),
        extensions = setOf("odt"),
        tint = Color(0xFF1565C0),
    ),
    EPUB(
        label = "EPUB",
        mimeTypes = setOf("application/epub+zip"),
        extensions = setOf("epub"),
        tint = Color(0xFF6A1B9A),
    ),
    RTF(
        label = "RTF",
        mimeTypes = setOf("application/rtf", "text/rtf"),
        extensions = setOf("rtf"),
        tint = Color(0xFF8E24AA),
    ),
    MD(
        label = "MD",
        mimeTypes = setOf("text/markdown", "text/x-markdown"),
        extensions = setOf("md", "markdown"),
        tint = Color(0xFF00897B),
    ),
    HTML(
        label = "HTML",
        mimeTypes = setOf("text/html", "application/xhtml+xml"),
        extensions = setOf("html", "htm", "xhtml"),
        tint = Color(0xFFE65100),
    ),
    JSON(
        label = "JSON",
        mimeTypes = setOf("application/json", "text/json"),
        extensions = setOf("json"),
        tint = Color(0xFFF57C00),
    ),
    XML(
        label = "XML",
        mimeTypes = setOf("application/xml", "text/xml"),
        extensions = setOf("xml"),
        tint = Color(0xFFFB8C00),
    ),
    CSV(
        label = "CSV",
        mimeTypes = setOf("text/csv", "application/csv"),
        extensions = setOf("csv", "tsv"),
        tint = Color(0xFF2E7D32),
    ),
    TXT(
        label = "TXT",
        mimeTypes = setOf("text/plain"),
        extensions = setOf("txt", "log"),
        tint = Color(0xFF546E7A),
    ),
    OTHER(
        label = "FILE",
        mimeTypes = emptySet(),
        extensions = emptySet(),
        tint = Color(0xFF607D8B),
    );

    companion object {
        fun resolve(mimeType: String?, fileName: String?): DocExtension {
            val mime = mimeType?.lowercase()?.substringBefore(';')?.trim()
            if (!mime.isNullOrBlank()) {
                entries.firstOrNull { mime in it.mimeTypes }?.let { return it }
            }
            val ext = fileName
                ?.substringAfterLast('.', missingDelimiterValue = "")
                ?.lowercase()
                ?.takeIf { it.isNotBlank() }
            if (ext != null) {
                entries.firstOrNull { ext in it.extensions }?.let { return it }
            }
            if (!mime.isNullOrBlank() && mime.startsWith("text/")) return TXT
            return OTHER
        }
    }
}
