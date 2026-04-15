package com.dark.tool_neuron.ui.screen.home

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.dark.tool_neuron.models.state.AppState
import com.dark.tool_neuron.viewmodel.ChatViewModel
import com.dark.tool_neuron.viewmodel.ChatUiState
import com.dark.tool_neuron.viewmodel.LLMModelViewModel
import com.dark.tool_neuron.ui.icons.TnIcons
import com.dark.tool_neuron.global.Standards

// ── System Tab ──

@Composable
internal fun SystemTabContent(
    appState: AppState,
    modelViewModel: LLMModelViewModel,
    chatViewModel: ChatViewModel
) {
    val context = LocalContext.current
    val isTextModelLoaded by modelViewModel.isGgufModelLoaded.collectAsStateWithLifecycle()
    val isImageModelLoaded by modelViewModel.isDiffusionModelLoaded.collectAsStateWithLifecycle()
    val chatState by chatViewModel.chatUiState.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 260.dp)
            .padding(10.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(Standards.SpacingSm)
    ) {
        // Thinking Mode
        if (isTextModelLoaded) {
            DawSectionHeader("Generation")
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = TnIcons.Brain,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = if (chatState.thinkingEnabled) MaterialTheme.colorScheme.primary
                               else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Thinking Mode",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Switch(
                    checked = chatState.thinkingEnabled,
                    onCheckedChange = { chatViewModel.setThinkingMode(it) },
                    modifier = Modifier.scale(0.75f)
                )
            }
        }

        // Active Models
        if (isTextModelLoaded || isImageModelLoaded) {
            DawSectionHeader("Active Models")
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (isTextModelLoaded) {
                    CompactBadge("Text", MaterialTheme.colorScheme.primary)
                }
                if (isImageModelLoaded) {
                    CompactBadge("Image", MaterialTheme.colorScheme.tertiary)
                }
            }
        }

        // System Resources
        val memoryUsage by produceState("--") {
            value = withContext(Dispatchers.IO) {
                val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
                val memoryInfo = ActivityManager.MemoryInfo()
                activityManager.getMemoryInfo(memoryInfo)
                val usedMemory = (memoryInfo.totalMem - memoryInfo.availMem) / (1024 * 1024)
                val totalMemory = memoryInfo.totalMem / (1024 * 1024)
                "${usedMemory}MB / ${totalMemory}MB"
            }
        }

        val activeThreads by produceState("--") {
            value = "${Thread.activeCount()}"
        }

        DawSectionHeader("Resources")
        SystemMetricRow(TnIcons.Cpu, "RAM", memoryUsage)
        SystemMetricRow(TnIcons.Database, "CPU Cores", getCpuCores())
        SystemMetricRow(TnIcons.Gauge, "Threads", activeThreads)

        // Device Info
        DawSectionHeader("Device")
        InfoRow("Model", Build.MODEL)
        InfoRow("Android", Build.VERSION.RELEASE)
        InfoRow("SDK", Build.VERSION.SDK_INT.toString())
    }
}

@Composable
private fun DawSectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.SemiBold
    )
}

@Composable
internal fun SystemMetricRow(
    icon: ImageVector,
    label: String,
    value: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 1.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                modifier = Modifier.size(14.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
internal fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 1.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

// ── System Info Helpers ──

@Composable
internal fun getCpuCores(): String = remember {
    try {
        val text = java.io.File("/sys/devices/system/cpu/present").readText().trim()
        val parts = text.split("-")
        if (parts.size == 2) "${parts[1].toInt() + 1}" else "${Runtime.getRuntime().availableProcessors()}"
    } catch (_: Exception) {
        "${Runtime.getRuntime().availableProcessors()}"
    }
}
