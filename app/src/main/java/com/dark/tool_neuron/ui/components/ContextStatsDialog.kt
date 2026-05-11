package com.dark.tool_neuron.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

data class ContextStatsReport(
    val nCtx: Int,
    val nUsed: Int,
    val contextUsagePct: Double,
    val modelMb: Double,
    val kvCacheMb: Double,
    val currentRssMb: Double,
    val peakRssMb: Double,
    val memAvailableMb: Double,
    val memTotalMb: Double,
    val threadMode: Int,
    val modelLoaded: Boolean,
    val vlmLoaded: Boolean,
    val vtCacheInit: Boolean,
    val vtCacheEntries: Int,
    val vtCacheBytes: Long,
    val vtCacheHits: Long,
    val vtCacheMisses: Long,
    val vlmKvCacheInit: Boolean,
    val vlmKvCacheEntries: Int,
    val vlmKvCacheBytes: Long,
    val vlmKvCacheHits: Long,
    val vlmKvCacheMisses: Long,
    val visionIndexEntries: Int,
    val visionIndexBytes: Long,
)

@Composable
fun ContextStatsDialog(
    report: ContextStatsReport,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Context & memory report") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                SectionHeader("Context")
                StatRow("KV usage", "${report.nUsed} / ${report.nCtx} tokens (${"%.1f".format(report.contextUsagePct)}%)")

                Spacer(Modifier.height(10.dp))
                SectionHeader("Memory (resident)")
                StatRow("Model", "%.0f MB".format(report.modelMb))
                StatRow("KV cache", "%.0f MB".format(report.kvCacheMb))
                StatRow("Process RSS (now)", "%.0f MB".format(report.currentRssMb))
                StatRow("Process RSS (peak)", "%.0f MB".format(report.peakRssMb))
                StatRow("Device available", "%.0f / %.0f MB".format(report.memAvailableMb, report.memTotalMb))

                Spacer(Modifier.height(10.dp))
                SectionHeader("Vision-tower cache")
                if (report.vtCacheInit) {
                    StatRow("Entries", "${report.vtCacheEntries}")
                    StatRow("Disk", "%.1f MB".format(report.vtCacheBytes / (1024.0 * 1024.0)))
                    StatRow("Hits / misses", "${report.vtCacheHits} / ${report.vtCacheMisses}")
                } else {
                    StatRow("Status", "uninitialized")
                }
                StatRow("HXS index entries", "${report.visionIndexEntries}")

                Spacer(Modifier.height(10.dp))
                SectionHeader("VLM-KV cache")
                if (report.vlmKvCacheInit) {
                    StatRow("Entries", "${report.vlmKvCacheEntries}")
                    StatRow("Disk", "%.1f MB".format(report.vlmKvCacheBytes / (1024.0 * 1024.0)))
                    StatRow("Hits / misses", "${report.vlmKvCacheHits} / ${report.vlmKvCacheMisses}")
                } else {
                    StatRow("Status", "uninitialized")
                }

                Spacer(Modifier.height(10.dp))
                SectionHeader("Engine state")
                StatRow("Model loaded", if (report.modelLoaded) "yes" else "no")
                StatRow("VLM projector", if (report.vlmLoaded) "loaded" else "not loaded")
                StatRow("Thread mode", report.threadMode.toString())
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        },
    )
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
    )
    HorizontalDivider(modifier = Modifier.fillMaxWidth())
}

@Composable
private fun StatRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
        )
    }
}
