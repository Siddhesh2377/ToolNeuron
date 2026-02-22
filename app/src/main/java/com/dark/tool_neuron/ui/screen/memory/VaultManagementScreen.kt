package com.dark.tool_neuron.ui.screen.memory

import android.annotation.SuppressLint
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// Shared formatting utilities

@SuppressLint("DefaultLocale")
fun formatBytes(bytes: Long): String {
    return when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "${bytes / 1024} KB"
        bytes < 1024 * 1024 * 1024 -> "${bytes / (1024 * 1024)} MB"
        else -> String.format("%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0))
    }
}

fun formatDate(timestamp: Long): String {
    val sdf = SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault())
    return sdf.format(Date(timestamp))
}

fun formatTimestampFull(timestamp: Long): String {
    val sdf = SimpleDateFormat("MMM dd yyyy, HH:mm", Locale.getDefault())
    return sdf.format(Date(timestamp))
}
