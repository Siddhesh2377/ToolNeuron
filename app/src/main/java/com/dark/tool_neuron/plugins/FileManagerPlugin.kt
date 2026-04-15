package com.dark.tool_neuron.plugins

import android.content.Context
import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dark.tool_neuron.models.plugins.PluginInfo
import com.dark.tool_neuron.plugins.api.SuperPlugin
import com.dark.gguf_lib.toolcalling.ToolCall
import com.dark.gguf_lib.toolcalling.ToolDefinitionBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileNotFoundException

class FileManagerPlugin(private val context: Context) : SuperPlugin {

    companion object {
        private const val TAG = "FileManagerPlugin"
        const val TOOL_LIST_FILES = "list_files"
        const val TOOL_READ_TEXT_FILE = "read_text_file"
        const val TOOL_READ_PDF = "read_pdf"
        const val TOOL_READ_DOCUMENT = "read_document"
        const val TOOL_SEARCH_FILES = "search_files"
        const val TOOL_CREATE_FILE = "create_file"

        private const val MAX_FILE_LIST_ENTRIES = 100
    }

    /** App-private sandbox directory for all file operations. */
    private val sandboxDir: File by lazy {
        File(context.filesDir, "toolneuron_files").also { it.mkdirs() }
    }

    /**
     * Resolve a user-supplied path to a File inside the sandbox.
     * Relative paths are resolved against sandboxDir.
     * Absolute paths are allowed only if they fall inside sandboxDir.
     */
    private fun resolveSandboxPath(path: String): File {
        val candidate = if (path.isBlank()) {
            sandboxDir
        } else {
            val f = File(path)
            if (f.isAbsolute) f else File(sandboxDir, path)
        }
        val resolved = candidate.canonicalFile
        val sandbox = sandboxDir.canonicalFile
        require(resolved.path.startsWith(sandbox.path)) {
            "Access denied: path must be inside the app sandbox (${sandbox.path})"
        }
        return resolved
    }

    override fun getPluginInfo(): PluginInfo {
        return PluginInfo(
            name = "File Manager",
            description = "Create, browse, read, and search files in the app sandbox",
            author = "ToolNeuron",
            version = "1.1.0",
            toolDefinitionBuilder = listOf(
                ToolDefinitionBuilder(
                    TOOL_CREATE_FILE,
                    "Create or overwrite a file with the given content in the app sandbox"
                )
                    .stringParam("path", "Relative path inside the sandbox, e.g. 'notes/todo.txt'", required = true)
                    .stringParam("content", "Text content to write into the file", required = true)
                    .booleanParam("append", "Append to the file instead of overwriting (default: false)", required = false),

                ToolDefinitionBuilder(
                    TOOL_LIST_FILES,
                    "List files in a directory inside the app sandbox"
                )
                    .stringParam("path", "Relative directory path (default: sandbox root)", required = false)
                    .stringParam("filter", "Glob filter pattern, e.g. '*.pdf', '*.txt'", required = false)
                    .booleanParam("recursive", "Whether to list files recursively (default: false)", required = false),

                ToolDefinitionBuilder(
                    TOOL_READ_TEXT_FILE,
                    "Read the contents of a text file in the app sandbox"
                )
                    .stringParam("path", "Path to the text file", required = true)
                    .numberParam("max_chars", "Maximum number of characters to read (default: 5000)", required = false),

                ToolDefinitionBuilder(
                    TOOL_READ_PDF,
                    "Extract text content from a PDF file in the app sandbox"
                )
                    .stringParam("path", "Path to the PDF file", required = true)
                    .numberParam("max_pages", "Maximum number of pages to extract (default: 10)", required = false),

                ToolDefinitionBuilder(
                    TOOL_READ_DOCUMENT,
                    "Read a document file (txt, csv, log, xml, json, etc.) in the app sandbox"
                )
                    .stringParam("path", "Path to the document file", required = true)
                    .numberParam("max_chars", "Maximum number of characters to read (default: 5000)", required = false),

                ToolDefinitionBuilder(
                    TOOL_SEARCH_FILES,
                    "Search for files by name pattern in the app sandbox"
                )
                    .stringParam("query", "Search query or filename pattern to match", required = true)
                    .stringParam("path", "Relative directory to search in (default: sandbox root)", required = false)
                    .stringParam("file_type", "Filter by file type: 'pdf', 'text', 'image'", required = false)
            )
        )
    }

    override fun serializeResult(data: Any): String = when (data) {
        is FileManagerResponse -> JSONObject().apply {
            put("tool", data.tool)
            put("path", data.path)
            put("content", data.content)
            put("fileCount", data.fileCount)
            put("success", data.success)
        }.toString()
        else -> data.toString()
    }

    override suspend fun executeTool(toolCall: ToolCall): Result<Any> {
        return try {
            when (toolCall.name) {
                TOOL_CREATE_FILE -> executeCreateFile(toolCall)
                TOOL_LIST_FILES -> executeListFiles(toolCall)
                TOOL_READ_TEXT_FILE -> executeReadTextFile(toolCall)
                TOOL_READ_PDF -> executeReadPdf(toolCall)
                TOOL_READ_DOCUMENT -> executeReadDocument(toolCall)
                TOOL_SEARCH_FILES -> executeSearchFiles(toolCall)
                else -> Result.failure(IllegalArgumentException("Unknown tool: ${toolCall.name}"))
            }
        } catch (e: IllegalArgumentException) {
            Log.e(TAG, "Sandbox violation or bad args: ${e.message}", e)
            Result.failure(e)
        } catch (e: SecurityException) {
            Log.e(TAG, "Permission denied: ${e.message}", e)
            Result.failure(SecurityException("Permission denied: ${e.message}"))
        } catch (e: FileNotFoundException) {
            Log.e(TAG, "File not found: ${e.message}", e)
            Result.failure(FileNotFoundException("File not found: ${e.message}"))
        } catch (e: Exception) {
            Log.e(TAG, "Error executing tool ${toolCall.name}: ${e.message}", e)
            Result.failure(e)
        }
    }

    // --- create_file ---

    private suspend fun executeCreateFile(toolCall: ToolCall): Result<Any> = withContext(Dispatchers.IO) {
        val path = toolCall.getString("path")
        val content = toolCall.getString("content")
        val append = toolCall.getBoolean("append", false)

        val file = resolveSandboxPath(path)

        // Create parent directories if needed
        file.parentFile?.mkdirs()

        if (append) {
            file.appendText(content, Charsets.UTF_8)
        } else {
            file.writeText(content, Charsets.UTF_8)
        }

        val action = if (append) "appended to" else "created"
        Log.d(TAG, "File $action: ${file.absolutePath} (${content.length} chars)")

        Result.success(
            FileManagerResponse(
                tool = TOOL_CREATE_FILE,
                path = file.absolutePath,
                content = "File $action successfully (${content.length} characters written)",
                fileCount = 1,
                success = true
            )
        )
    }

    // --- list_files ---

    private suspend fun executeListFiles(toolCall: ToolCall): Result<Any> = withContext(Dispatchers.IO) {
        val rawPath = toolCall.getString("path", "")
        val filter = toolCall.getString("filter", "")
        val recursive = toolCall.getBoolean("recursive", false)

        val directory = resolveSandboxPath(rawPath)
        val path = directory.absolutePath
        if (!directory.exists()) {
            return@withContext Result.failure(FileNotFoundException("Directory not found: $path"))
        }
        if (!directory.isDirectory) {
            return@withContext Result.failure(IllegalArgumentException("Path is not a directory: $path"))
        }

        val filterRegex = if (filter.isNotBlank()) {
            globToRegex(filter)
        } else {
            null
        }

        val files = if (recursive) {
            directory.walkTopDown()
                .filter { it.isFile }
                .filter { file -> filterRegex == null || filterRegex.matches(file.name) }
                .take(MAX_FILE_LIST_ENTRIES)
                .toList()
        } else {
            directory.listFiles()
                ?.filter { file -> filterRegex == null || !file.isFile || filterRegex.matches(file.name) }
                ?.take(MAX_FILE_LIST_ENTRIES)
                ?: emptyList()
        }

        val fileNames = files.map { file ->
            val suffix = if (file.isDirectory) "/" else ""
            file.name + suffix
        }

        val content = if (fileNames.isEmpty()) {
            "No files found in $path" + if (filter.isNotBlank()) " matching '$filter'" else ""
        } else {
            fileNames.joinToString("\n")
        }

        Result.success(
            FileManagerResponse(
                tool = TOOL_LIST_FILES,
                path = path,
                content = content,
                fileCount = fileNames.size,
                success = true
            )
        )
    }

    // --- read_text_file ---

    private suspend fun executeReadTextFile(toolCall: ToolCall): Result<Any> = withContext(Dispatchers.IO) {
        val rawPath = toolCall.getString("path")
        val maxChars = toolCall.getInt("max_chars", 5000).coerceIn(1, 50000)

        val file = resolveSandboxPath(rawPath)
        val path = file.absolutePath
        if (!file.exists()) {
            return@withContext Result.failure(FileNotFoundException("File not found: $path"))
        }
        if (!file.isFile) {
            return@withContext Result.failure(IllegalArgumentException("Path is not a file: $path"))
        }

        val text = try {
            val fullText = file.readText(Charsets.UTF_8)
            if (fullText.length > maxChars) {
                fullText.take(maxChars) + "\n... [truncated at $maxChars characters, total: ${fullText.length}]"
            } else {
                fullText
            }
        } catch (e: Exception) {
            return@withContext Result.failure(
                IllegalArgumentException("Failed to read file as text: ${e.message}")
            )
        }

        Result.success(
            FileManagerResponse(
                tool = TOOL_READ_TEXT_FILE,
                path = path,
                content = text,
                fileCount = 1,
                success = true
            )
        )
    }

    // --- read_pdf ---

    private suspend fun executeReadPdf(toolCall: ToolCall): Result<Any> = withContext(Dispatchers.IO) {
        val rawPath = toolCall.getString("path")
        val maxPages = toolCall.getInt("max_pages", 10).coerceIn(1, 100)

        val file = resolveSandboxPath(rawPath)
        val path = file.absolutePath
        if (!file.exists()) {
            return@withContext Result.failure(FileNotFoundException("File not found: $path"))
        }
        if (!file.isFile) {
            return@withContext Result.failure(IllegalArgumentException("Path is not a file: $path"))
        }

        val content = try {
            extractPdfText(file, maxPages)
        } catch (e: Exception) {
            Log.e(TAG, "PDF extraction failed: ${e.message}", e)
            return@withContext Result.failure(
                IllegalArgumentException("Failed to extract PDF text: ${e.message}")
            )
        }

        Result.success(
            FileManagerResponse(
                tool = TOOL_READ_PDF,
                path = path,
                content = content,
                fileCount = 1,
                success = true
            )
        )
    }

    private fun extractPdfText(file: File, maxPages: Int): String {
        // Try using PdfRenderer (available on API 21+)
        try {
            val fd = android.os.ParcelFileDescriptor.open(file, android.os.ParcelFileDescriptor.MODE_READ_ONLY)
            val renderer = android.graphics.pdf.PdfRenderer(fd)
            val pageCount = renderer.pageCount
            val pagesToRead = minOf(pageCount, maxPages)

            val sb = StringBuilder()
            sb.appendLine("PDF: ${file.name}")
            sb.appendLine("Total pages: $pageCount (reading first $pagesToRead)")
            sb.appendLine("---")

            for (i in 0 until pagesToRead) {
                val page = renderer.openPage(i)
                sb.appendLine("[Page ${i + 1}] (${page.width}x${page.height})")
                // PdfRenderer does not directly extract text; it renders bitmaps.
                // We note that text extraction is limited without a dedicated library.
                sb.appendLine("(Page rendered - direct text extraction requires a PDF text library)")
                page.close()
            }

            renderer.close()
            fd.close()

            sb.appendLine("---")
            sb.appendLine("Note: PdfRenderer can render pages but cannot extract embedded text directly. " +
                    "For full text extraction, a library such as Apache PDFBox or iText is needed.")

            return sb.toString()
        } catch (e: Exception) {
            Log.w(TAG, "PdfRenderer failed, trying raw text extraction: ${e.message}")
        }

        // Fallback: attempt to read raw bytes and extract printable text
        return try {
            val bytes = file.readBytes()
            val rawText = String(bytes, Charsets.ISO_8859_1)

            // Try to extract text between stream markers in PDF
            val textParts = mutableListOf<String>()
            val streamRegex = Regex("stream\\s*\\n(.*?)\\nendstream", RegexOption.DOT_MATCHES_ALL)
            val matches = streamRegex.findAll(rawText)

            for (match in matches) {
                val streamContent = match.groupValues[1]
                // Filter to printable ASCII characters
                val printable = streamContent.filter { it in ' '..'~' || it == '\n' || it == '\r' || it == '\t' }
                if (printable.length > 20) {
                    textParts.add(printable.trim())
                }
            }

            if (textParts.isNotEmpty()) {
                val extracted = textParts.joinToString("\n---\n").take(5000)
                "PDF: ${file.name}\n(Raw text extraction - formatting may be lost)\n\n$extracted"
            } else {
                "PDF: ${file.name}\nUnable to extract text content. " +
                        "The PDF may contain scanned images or encoded text that requires a dedicated PDF library to read."
            }
        } catch (e: Exception) {
            "PDF: ${file.name}\nText extraction is not available: ${e.message}"
        }
    }

    // --- read_document ---

    private suspend fun executeReadDocument(toolCall: ToolCall): Result<Any> = withContext(Dispatchers.IO) {
        val rawPath = toolCall.getString("path")
        val maxChars = toolCall.getInt("max_chars", 5000).coerceIn(1, 50000)

        val file = resolveSandboxPath(rawPath)
        val path = file.absolutePath
        if (!file.exists()) {
            return@withContext Result.failure(FileNotFoundException("File not found: $path"))
        }
        if (!file.isFile) {
            return@withContext Result.failure(IllegalArgumentException("Path is not a file: $path"))
        }

        val extension = file.extension.lowercase()
        val content = when (extension) {
            "txt", "text", "csv", "tsv", "log", "md", "markdown",
            "json", "xml", "html", "htm", "yaml", "yml",
            "ini", "cfg", "conf", "properties",
            "sh", "bat", "py", "js", "kt", "java", "c", "cpp", "h",
            "css", "sql", "gradle", "toml" -> {
                // Directly readable text-based formats
                try {
                    val fullText = file.readText(Charsets.UTF_8)
                    if (fullText.length > maxChars) {
                        fullText.take(maxChars) + "\n... [truncated at $maxChars characters, total: ${fullText.length}]"
                    } else {
                        fullText
                    }
                } catch (e: Exception) {
                    "Failed to read file: ${e.message}"
                }
            }
            "pdf" -> {
                // Delegate to PDF extraction
                try {
                    extractPdfText(file, 10)
                } catch (e: Exception) {
                    "Failed to extract PDF text: ${e.message}"
                }
            }
            "doc", "docx", "odt", "rtf" -> {
                // Try basic text extraction for these formats
                try {
                    val bytes = file.readBytes()
                    val rawText = String(bytes, Charsets.ISO_8859_1)
                    val printable = rawText.filter { it in ' '..'~' || it == '\n' || it == '\r' || it == '\t' }
                    val cleaned = printable.replace(Regex("\\s{3,}"), "\n").trim()
                    if (cleaned.length > maxChars) {
                        cleaned.take(maxChars) + "\n... [truncated at $maxChars characters]"
                    } else if (cleaned.isNotEmpty()) {
                        "Document: ${file.name} (basic text extraction - formatting lost)\n\n$cleaned"
                    } else {
                        "Document: ${file.name}\nUnable to extract readable text. " +
                                "This file format requires a dedicated library (e.g., Apache POI) for proper reading."
                    }
                } catch (e: Exception) {
                    "Failed to read document: ${e.message}"
                }
            }
            else -> {
                // Try reading as plain text
                try {
                    val fullText = file.readText(Charsets.UTF_8)
                    if (fullText.length > maxChars) {
                        fullText.take(maxChars) + "\n... [truncated at $maxChars characters, total: ${fullText.length}]"
                    } else {
                        fullText
                    }
                } catch (e: Exception) {
                    "Unsupported or binary file format '.$extension'. Unable to extract text content."
                }
            }
        }

        Result.success(
            FileManagerResponse(
                tool = TOOL_READ_DOCUMENT,
                path = path,
                content = content,
                fileCount = 1,
                success = true
            )
        )
    }

    // --- search_files ---

    private suspend fun executeSearchFiles(toolCall: ToolCall): Result<Any> = withContext(Dispatchers.IO) {
        val query = toolCall.getString("query")
        val rawPath = toolCall.getString("path", "")
        val fileType = toolCall.getString("file_type", "").lowercase()

        val directory = resolveSandboxPath(rawPath)
        val path = directory.absolutePath
        if (!directory.exists()) {
            return@withContext Result.failure(FileNotFoundException("Directory not found: $path"))
        }
        if (!directory.isDirectory) {
            return@withContext Result.failure(IllegalArgumentException("Path is not a directory: $path"))
        }

        val queryRegex = globToRegex(query)

        val typeExtensions = when (fileType) {
            "pdf" -> setOf("pdf")
            "text" -> setOf("txt", "text", "md", "csv", "log", "json", "xml", "html", "yml", "yaml")
            "image" -> setOf("jpg", "jpeg", "png", "gif", "bmp", "webp", "svg")
            else -> null
        }

        val matchedFiles = directory.walkTopDown()
            .filter { it.isFile }
            .filter { file ->
                val nameMatches = queryRegex.matches(file.name) ||
                        file.name.contains(query, ignoreCase = true)
                val typeMatches = typeExtensions == null ||
                        file.extension.lowercase() in typeExtensions
                nameMatches && typeMatches
            }
            .take(MAX_FILE_LIST_ENTRIES)
            .toList()

        val fileNames = matchedFiles.map { file ->
            file.absolutePath
        }

        val content = if (fileNames.isEmpty()) {
            "No files found matching '$query' in $path" +
                    if (fileType.isNotBlank()) " (type: $fileType)" else ""
        } else {
            fileNames.joinToString("\n")
        }

        Result.success(
            FileManagerResponse(
                tool = TOOL_SEARCH_FILES,
                path = path,
                content = content,
                fileCount = matchedFiles.size,
                success = true
            )
        )
    }

    // --- Utility ---

    private fun globToRegex(glob: String): Regex {
        val regexStr = buildString {
            append("^")
            for (ch in glob) {
                when (ch) {
                    '*' -> append(".*")
                    '?' -> append(".")
                    '.' -> append("\\.")
                    '\\' -> append("\\\\")
                    '[' -> append("\\[")
                    ']' -> append("\\]")
                    '{' -> append("\\{")
                    '}' -> append("\\}")
                    '(' -> append("\\(")
                    ')' -> append("\\)")
                    '+' -> append("\\+")
                    '^' -> append("\\^")
                    '$' -> append("\\$")
                    '|' -> append("\\|")
                    else -> append(ch)
                }
            }
            append("$")
        }
        return Regex(regexStr, RegexOption.IGNORE_CASE)
    }

    // --- Composable UI ---

    @Composable
    override fun ToolCallUI() {
        // No standalone UI needed
    }

    @Composable
    override fun CacheToolUI(data: JSONObject) {
        val tool = data.optString("tool", "")

        when (tool) {
            TOOL_CREATE_FILE -> ReadFileResultUI(data) // reuse file result UI
            TOOL_LIST_FILES -> ListFilesResultUI(data)
            TOOL_READ_TEXT_FILE, TOOL_READ_PDF, TOOL_READ_DOCUMENT -> ReadFileResultUI(data)
            TOOL_SEARCH_FILES -> SearchFilesResultUI(data)
            else -> {
                // Fallback: try to infer from data
                when {
                    data.has("fileCount") && data.optInt("fileCount", 0) > 1 -> ListFilesResultUI(data)
                    data.has("content") -> ReadFileResultUI(data)
                    else -> {
                        Text(
                            text = data.toString(2),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(8.dp)
                        )
                    }
                }
            }
        }
    }

    @Composable
    private fun ListFilesResultUI(data: JSONObject) {
        val path = data.optString("path", "")
        val fileCount = data.optInt("fileCount", 0)
        val content = data.optString("content", "")

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(6.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)
        ) {
            Column(
                modifier = Modifier.padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = "File Listing",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = path,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "$fileCount files",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                if (content.isNotBlank()) {
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    ) {
                        Text(
                            text = content.take(500) + if (content.length > 500) "\n..." else "",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(8.dp),
                            maxLines = 15,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }

    @Composable
    private fun ReadFileResultUI(data: JSONObject) {
        val tool = data.optString("tool", "")
        val path = data.optString("path", "")
        val content = data.optString("content", "")

        val title = when (tool) {
            TOOL_CREATE_FILE -> "File Created"
            TOOL_READ_TEXT_FILE -> "Text File"
            TOOL_READ_PDF -> "PDF Document"
            TOOL_READ_DOCUMENT -> "Document"
            else -> "File Content"
        }

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(6.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)
        ) {
            Column(
                modifier = Modifier.padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Text(
                    text = path,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                if (content.isNotBlank()) {
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    ) {
                        Text(
                            text = content.take(500) + if (content.length > 500) "..." else "",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(8.dp),
                            maxLines = 20,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }

    @Composable
    private fun SearchFilesResultUI(data: JSONObject) {
        val path = data.optString("path", "")
        val fileCount = data.optInt("fileCount", 0)
        val content = data.optString("content", "")

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(6.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)
        ) {
            Column(
                modifier = Modifier.padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = "File Search",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "In: $path",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "$fileCount matches",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                if (content.isNotBlank()) {
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    ) {
                        Text(
                            text = content.take(500) + if (content.length > 500) "\n..." else "",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(8.dp),
                            maxLines = 15,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

data class FileManagerResponse(
    val tool: String,
    val path: String,
    val content: String,
    val fileCount: Int,
    val success: Boolean
)
