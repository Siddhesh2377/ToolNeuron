package com.dark.tool_neuron.util

import android.content.Context
import android.net.Uri
import android.util.Log
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import nl.siegmann.epublib.epub.EpubReader
import org.apache.poi.hssf.usermodel.HSSFWorkbook
import org.apache.poi.hwpf.HWPFDocument
import org.apache.poi.hwpf.extractor.WordExtractor
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.apache.poi.xwpf.usermodel.XWPFDocument
import java.io.InputStream

/**
 * Utility class for parsing various document formats into plain text.
 * Supports: PDF, EPUB, Excel (.xlsx, .xls), Word (.docx, .doc), and plain text files.
 */
object DocumentParser {
    private const val TAG = "DocumentParser"
    @Volatile private var pdfBoxInitialized = false

    /**
     * Supported document MIME types
     */
    object MimeTypes {
        const val PDF = "application/pdf"
        const val EPUB = "application/epub+zip"
        const val XLSX = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
        const val XLS = "application/vnd.ms-excel"
        const val DOCX = "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
        const val DOC = "application/msword"
    }

    /**
     * Parse a document from a URI into plain text.
     *
     * @param uri The URI of the document
     * @param context Android context for accessing content resolver
     * @param mimeType Optional MIME type hint. If not provided, will be inferred from URI
     * @return Result containing the extracted text or an error
     */
    suspend fun parseDocument(
        uri: Uri,
        context: Context,
        mimeType: String? = null
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val contentResolver = context.contentResolver
            val detectedMimeType = mimeType ?: contentResolver.getType(uri)

            Log.d(TAG, "Parsing document: $uri, MIME type: $detectedMimeType")

            contentResolver.openInputStream(uri)?.use { inputStream ->
                val text = when (detectedMimeType) {
                    MimeTypes.PDF -> parsePdf(inputStream, context)
                    MimeTypes.EPUB -> parseEpub(inputStream)
                    MimeTypes.XLSX -> parseXlsx(inputStream)
                    MimeTypes.XLS -> parseXls(inputStream)
                    MimeTypes.DOCX -> parseDocx(inputStream)
                    MimeTypes.DOC -> parseDoc(inputStream)
                    "text/plain" -> parsePlainText(inputStream)
                    else -> {
                        // Try to infer from file extension
                        val fileName = uri.lastPathSegment ?: ""
                        when {
                            fileName.endsWith(".pdf", ignoreCase = true) -> parsePdf(inputStream, context)
                            fileName.endsWith(".epub", ignoreCase = true) -> parseEpub(inputStream)
                            fileName.endsWith(".xlsx", ignoreCase = true) -> parseXlsx(inputStream)
                            fileName.endsWith(".xls", ignoreCase = true) -> parseXls(inputStream)
                            fileName.endsWith(".docx", ignoreCase = true) -> parseDocx(inputStream)
                            fileName.endsWith(".doc", ignoreCase = true) -> parseDoc(inputStream)
                            fileName.endsWith(".txt", ignoreCase = true) -> parsePlainText(inputStream)
                            else -> {
                                Log.w(TAG, "Unknown file type: $detectedMimeType / $fileName, treating as plain text")
                                parsePlainText(inputStream)
                            }
                        }
                    }
                }

                Log.d(TAG, "Successfully parsed document, extracted ${text.length} characters")
                Result.success(text)
            } ?: Result.failure(Exception("Failed to open input stream for URI: $uri"))
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing document: ${e.message}", e)
            Result.failure(Exception("Failed to parse document: ${e.message}", e))
        }
    }

    /**
     * Parse a PDF document using PDFBox-Android
     */
    private fun parsePdf(inputStream: InputStream, context: Context): String {
        return try {
            // Initialize PDFBox-Android once (thread-safe via volatile flag)
            if (!pdfBoxInitialized) {
                synchronized(this) {
                    if (!pdfBoxInitialized) {
                        PDFBoxResourceLoader.init(context.applicationContext)
                        pdfBoxInitialized = true
                    }
                }
            }

            PDDocument.load(inputStream).use { document ->
                val stripper = PDFTextStripper()
                stripper.getText(document)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing PDF: ${e.message}", e)
            throw Exception("Failed to parse PDF: ${e.message}", e)
        }
    }

    /**
     * Parse an EPUB document
     */
    private fun parseEpub(inputStream: InputStream): String {
        return try {
            val book = EpubReader().readEpub(inputStream)
            val textBuilder = StringBuilder()

            // Extract text from all chapters/resources
            book.contents.forEach { resource ->
                try {
                    val content = String(resource.data, Charsets.UTF_8)
                    // Remove HTML tags for plain text
                    val plainText = content
                        .replace(Regex("<[^>]*>"), " ")
                        .replace(Regex("\\s+"), " ")
                        .trim()

                    if (plainText.isNotBlank()) {
                        textBuilder.append(plainText)
                        textBuilder.append("\n\n")
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to parse EPUB resource: ${resource.href}", e)
                }
            }

            textBuilder.toString().trim()
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing EPUB: ${e.message}", e)
            throw Exception("Failed to parse EPUB: ${e.message}", e)
        }
    }

    /**
     * Parse an Excel .xlsx file (Office Open XML format)
     */
    private fun parseXlsx(inputStream: InputStream): String {
        return try {
            val workbook = XSSFWorkbook(inputStream)
            val textBuilder = StringBuilder()

            for (sheetIndex in 0 until workbook.numberOfSheets) {
                val sheet = workbook.getSheetAt(sheetIndex)
                textBuilder.append("Sheet: ${sheet.sheetName}\n")
                textBuilder.append("=" .repeat(40))
                textBuilder.append("\n\n")

                for (row in sheet) {
                    val rowText = row.mapNotNull { cell ->
                        when (cell.cellType) {
                            org.apache.poi.ss.usermodel.CellType.STRING -> cell.stringCellValue
                            org.apache.poi.ss.usermodel.CellType.NUMERIC -> cell.numericCellValue.toString()
                            org.apache.poi.ss.usermodel.CellType.BOOLEAN -> cell.booleanCellValue.toString()
                            org.apache.poi.ss.usermodel.CellType.FORMULA -> {
                                try {
                                    cell.stringCellValue
                                } catch (e: Exception) {
                                    cell.numericCellValue.toString()
                                }
                            }
                            else -> null
                        }
                    }.joinToString("\t")

                    if (rowText.isNotBlank()) {
                        textBuilder.append(rowText)
                        textBuilder.append("\n")
                    }
                }
                textBuilder.append("\n")
            }

            workbook.close()
            textBuilder.toString().trim()
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing XLSX: ${e.message}", e)
            throw Exception("Failed to parse Excel file (.xlsx): ${e.message}", e)
        }
    }

    /**
     * Parse an Excel .xls file (legacy binary format)
     */
    private fun parseXls(inputStream: InputStream): String {
        return try {
            val workbook = HSSFWorkbook(inputStream)
            val textBuilder = StringBuilder()

            for (sheetIndex in 0 until workbook.numberOfSheets) {
                val sheet = workbook.getSheetAt(sheetIndex)
                textBuilder.append("Sheet: ${sheet.sheetName}\n")
                textBuilder.append("=".repeat(40))
                textBuilder.append("\n\n")

                for (row in sheet) {
                    val rowText = row.mapNotNull { cell ->
                        when (cell.cellType) {
                            org.apache.poi.ss.usermodel.CellType.STRING -> cell.stringCellValue
                            org.apache.poi.ss.usermodel.CellType.NUMERIC -> cell.numericCellValue.toString()
                            org.apache.poi.ss.usermodel.CellType.BOOLEAN -> cell.booleanCellValue.toString()
                            org.apache.poi.ss.usermodel.CellType.FORMULA -> {
                                try {
                                    cell.stringCellValue
                                } catch (e: Exception) {
                                    cell.numericCellValue.toString()
                                }
                            }
                            else -> null
                        }
                    }.joinToString("\t")

                    if (rowText.isNotBlank()) {
                        textBuilder.append(rowText)
                        textBuilder.append("\n")
                    }
                }
                textBuilder.append("\n")
            }

            workbook.close()
            textBuilder.toString().trim()
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing XLS: ${e.message}", e)
            throw Exception("Failed to parse Excel file (.xls): ${e.message}", e)
        }
    }

    /**
     * Parse a Word .docx file (Office Open XML format)
     */
    private fun parseDocx(inputStream: InputStream): String {
        return try {
            val document = XWPFDocument(inputStream)
            val textBuilder = StringBuilder()

            // Extract text from paragraphs
            document.paragraphs.forEach { paragraph ->
                val text = paragraph.text?.trim()
                if (!text.isNullOrBlank()) {
                    textBuilder.append(text)
                    textBuilder.append("\n")
                }
            }

            // Extract text from tables
            document.tables.forEach { table ->
                table.rows.forEach { row ->
                    val rowText = row.tableCells.mapNotNull { cell ->
                        cell.text?.trim()
                    }.filter { it.isNotBlank() }.joinToString("\t")

                    if (rowText.isNotBlank()) {
                        textBuilder.append(rowText)
                        textBuilder.append("\n")
                    }
                }
            }

            document.close()
            textBuilder.toString().trim()
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing DOCX: ${e.message}", e)
            throw Exception("Failed to parse Word document (.docx): ${e.message}", e)
        }
    }

    /**
     * Parse a Word .doc file (legacy binary format)
     */
    private fun parseDoc(inputStream: InputStream): String {
        return try {
            val document = HWPFDocument(inputStream)
            val extractor = WordExtractor(document)
            val text = extractor.text
            extractor.close()
            text.trim()
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing DOC: ${e.message}", e)
            throw Exception("Failed to parse Word document (.doc): ${e.message}", e)
        }
    }

    /**
     * Parse a plain text file
     */
    private fun parsePlainText(inputStream: InputStream): String {
        return try {
            inputStream.bufferedReader().use { it.readText() }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing plain text: ${e.message}", e)
            throw Exception("Failed to parse plain text: ${e.message}", e)
        }
    }

    /**
     * Get human-readable file type name from MIME type
     */
    fun getFileTypeName(mimeType: String?): String {
        return when (mimeType) {
            MimeTypes.PDF -> "PDF"
            MimeTypes.EPUB -> "EPUB"
            MimeTypes.XLSX, MimeTypes.XLS -> "Excel"
            MimeTypes.DOCX, MimeTypes.DOC -> "Word"
            "text/plain" -> "Text"
            else -> "Document"
        }
    }

}