package com.dark.tool_neuron.repo

import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DocumentParser @Inject constructor(
    @ApplicationContext private val context: Context
) {
    fun extractText(uri: Uri): Result<Pair<String, String>> {
        val resolver = context.contentResolver
        val mimeType = resolver.getType(uri) ?: "application/octet-stream"

        return when {
            mimeType.startsWith("text/") || mimeType == "application/json" -> {
                runCatching {
                    val text = resolver.openInputStream(uri)?.use { stream ->
                        stream.bufferedReader(Charsets.UTF_8).readText()
                    } ?: throw IllegalStateException("Could not open input stream")
                    text to mimeType
                }
            }

            mimeType == "application/pdf" -> {
                // TODO: integrate a PDF text extraction library
                Result.failure(UnsupportedOperationException("PDF support coming soon"))
            }

            else -> {
                runCatching {
                    val text = resolver.openInputStream(uri)?.use { stream ->
                        stream.bufferedReader(Charsets.UTF_8).readText()
                    } ?: throw IllegalStateException("Could not open input stream")
                    text to mimeType
                }
            }
        }
    }
}
