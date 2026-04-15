package com.dark.tool_neuron.global

import android.annotation.SuppressLint
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ── Byte Size Formatting ──

/**
 * Formats bytes using binary units (1024-based).
 * Output: "256 B", "1.5 KB", "128.0 MB", "2.50 GB"
 */
@SuppressLint("DefaultLocale")
fun formatBytes(bytes: Long): String = when {
    bytes < 1024L -> "$bytes B"
    bytes < 1024L * 1024 -> String.format("%.1f KB", bytes / 1024.0)
    bytes < 1024L * 1024 * 1024 -> String.format("%.1f MB", bytes / (1024.0 * 1024.0))
    else -> String.format("%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0))
}

/**
 * Formats bytes from Int using binary units.
 */
fun formatBytes(bytes: Int): String = formatBytes(bytes.toLong())

/**
 * Formats bytes using decimal units (1000-based) — for download sizes.
 * Output: "256 B", "1.50 KB", "1.50 MB", "1.50 GB"
 */
@SuppressLint("DefaultLocale")
fun formatDecimalBytes(bytes: Long): String = when {
    bytes >= 1_000_000_000 -> String.format("%.2f GB", bytes / 1_000_000_000.0)
    bytes >= 1_000_000 -> String.format("%.2f MB", bytes / 1_000_000.0)
    bytes >= 1_000 -> String.format("%.2f KB", bytes / 1_000.0)
    else -> "$bytes B"
}

// ── Number Formatting ──

/**
 * Formats large numbers with K/M/B suffixes.
 * Output: "1500", "1.50K", "1.50M", "1.50B"
 */
@SuppressLint("DefaultLocale")
fun formatNumber(num: Int): String = when {
    num >= 1_000_000_000 -> String.format("%.2fB", num / 1_000_000_000.0)
    num >= 1_000_000 -> String.format("%.2fM", num / 1_000_000.0)
    num >= 1_000 -> String.format("%.2fK", num / 1_000.0)
    else -> num.toString()
}

// ── Timestamp Formatting ──

/**
 * Relative time: "now", "5m", "2h", "3d", or "Jan 15".
 * Use for chat list items.
 */
fun formatRelativeTime(timestamp: Long): String {
    val diff = System.currentTimeMillis() - timestamp
    return when {
        diff < 60_000 -> "now"
        diff < 3_600_000 -> "${diff / 60_000}m"
        diff < 86_400_000 -> "${diff / 3_600_000}h"
        diff < 604_800_000 -> "${diff / 86_400_000}d"
        else -> SimpleDateFormat("MMM dd", Locale.getDefault()).format(Date(timestamp))
    }
}

/**
 * Compact date: "Jan 15, 14:30" or "N/A" for 0.
 */
fun formatCompactDate(timestamp: Long): String {
    if (timestamp == 0L) return "N/A"
    return SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault()).format(Date(timestamp))
}

/**
 * Full date with year: "Jan 15 2026, 14:30".
 */
fun formatFullDate(timestamp: Long): String {
    return SimpleDateFormat("MMM dd yyyy, HH:mm", Locale.getDefault()).format(Date(timestamp))
}

/**
 * Date with year, no time: "Jan 15, 2026".
 */
fun formatDateOnly(timestamp: Long): String {
    return SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(Date(timestamp))
}

/**
 * Date with year and time: "Jan 15, 2026 14:30".
 */
fun formatDateWithTime(timestamp: Long): String {
    return SimpleDateFormat("MMM d, yyyy HH:mm", Locale.getDefault()).format(Date(timestamp))
}

/**
 * Time only: "14:30:45". Use for logs/terminal.
 */
fun formatTimeOnly(timestamp: Long): String {
    return SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(timestamp))
}

/**
 * Backup-safe timestamp for filenames: "20260305_1430"
 */
fun formatBackupTimestamp(timestamp: Long = System.currentTimeMillis()): String {
    return SimpleDateFormat("yyyyMMdd_HHmm", Locale.getDefault()).format(Date(timestamp))
}
