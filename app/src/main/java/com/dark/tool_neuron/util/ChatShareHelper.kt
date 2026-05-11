package com.dark.tool_neuron.util

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File

object ChatShareHelper {

    private const val AUTHORITY_SUFFIX = ".exports"
    private const val EXPORT_SUBDIR = "exports"

    fun writeAndShare(
        context: Context,
        filename: String,
        body: String,
        mimeType: String,
        title: String,
    ) {
        val dir = File(context.cacheDir, EXPORT_SUBDIR).apply { mkdirs() }
        val file = File(dir, filename)
        file.writeText(body)

        val authority = context.packageName + AUTHORITY_SUFFIX
        val uri = FileProvider.getUriForFile(context, authority, file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, title)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(send, "Share chat").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(chooser)
    }
}
