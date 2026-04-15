package com.dark.tool_neuron.ui.screen.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.dark.tool_neuron.global.HardwareProfile
import com.dark.tool_neuron.global.PerformanceMode
import com.dark.tool_neuron.global.Standards
import com.dark.tool_neuron.models.table_schema.Model
import com.dark.tool_neuron.service.ModelDownloadService
import com.dark.tool_neuron.ui.components.ActionTextButton
import com.dark.tool_neuron.ui.components.ActionToggleGroup
import com.dark.tool_neuron.ui.components.BodyLabel
import com.dark.tool_neuron.ui.components.SectionDivider
import com.dark.tool_neuron.ui.components.SectionHeader
import com.dark.tool_neuron.ui.components.StandardCard
import com.dark.tool_neuron.ui.components.SwitchRow
import com.dark.tool_neuron.ui.icons.TnIcons
import com.dark.tool_neuron.viewmodel.SettingsViewModel

// ── General Settings Section ──

internal fun LazyListScope.generalSettingsSection(
    toolCallingEnabled: Boolean,
    toolCallingBypassEnabled: Boolean,
    hasToolCallingModel: Boolean,
    toolCallingDownloadState: ModelDownloadService.DownloadState?,
    viewModel: SettingsViewModel
) {
    // ==================== General ====================
    item { SectionHeader(title = "General") }

    item {
        val canEnableToolCalling = hasToolCallingModel || toolCallingBypassEnabled
        SwitchRow(
            title = "Tool Calling",
            description = when {
                toolCallingBypassEnabled -> "Bypass enabled — tool calling available for all models"
                hasToolCallingModel -> "Any model with a chat template can use tools"
                else -> "Install a GGUF model to enable tool calling"
            },
            checked = toolCallingEnabled && canEnableToolCalling,
            onCheckedChange = { viewModel.setToolCallingEnabled(it) },
            enabled = canEnableToolCalling
        )
    }

    // Download recommended tool calling model card
    if (!hasToolCallingModel) {
        item {
            ModelDownloadCard(
                title = "Recommended Tool Calling Model",
                description = "Ruvltra Claude Code 0.5B · ~400 MB\nCompact model optimized for tool calling",
                downloadState = toolCallingDownloadState,
                onDownload = { viewModel.downloadToolCallingModel() }
            )
        }
    }

    // Bypass tool calling model check — red warning card
    item {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(Standards.CardCornerRadius),
            color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f),
            border = BorderStroke(
                width = 1.dp,
                color = MaterialTheme.colorScheme.error.copy(alpha = 0.5f)
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(Standards.SpacingMd),
                verticalArrangement = Arrangement.spacedBy(Standards.SpacingSm)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Standards.SpacingSm)
                ) {
                    Icon(
                        TnIcons.AlertTriangle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = "Bypass Model Check",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                Text(
                    text = "Force tool calling on models without a chat template. May cause errors or unexpected output.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
                SwitchRow(
                    title = "Enable Bypass",
                    description = if (toolCallingBypassEnabled) "Tool calling forced for all models" else "Only models with chat templates can use tools",
                    checked = toolCallingBypassEnabled,
                    onCheckedChange = { viewModel.setToolCallingBypassEnabled(it) },
                    titleColor = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

// ── LLM Settings Section ──

internal fun LazyListScope.llmSettingsSection(
    streamingEnabled: Boolean,
    chatMemoryEnabled: Boolean,
    askModelReloadDialog: Boolean,
    viewModel: SettingsViewModel
) {
    item { Spacer(Modifier.height(Standards.SpacingSm)) }
    item { SectionDivider() }
    item { SectionHeader(title = "LLM") }

    item {
        SwitchRow(
            title = "Streaming Response",
            description = "Stream tokens as they generate in real-time",
            checked = streamingEnabled,
            onCheckedChange = { viewModel.setStreamingEnabled(it) }
        )
    }

    item {
        SwitchRow(
            title = "Chat Memory",
            description = "Remember previous messages in conversation (faster without)",
            checked = chatMemoryEnabled,
            onCheckedChange = { viewModel.setChatMemoryEnabled(it) }
        )
    }

    item {
        SwitchRow(
            title = "Ask to Reload Model",
            description = "Show dialog on startup to reload last model. When off, auto-loads silently.",
            checked = askModelReloadDialog,
            onCheckedChange = { viewModel.setAskModelReloadDialog(it) }
        )
    }
}

// ── Chat Settings Section ──

internal fun LazyListScope.chatSettingsSection(
    codeHighlightEnabled: Boolean,
    viewModel: SettingsViewModel
) {
    item { Spacer(Modifier.height(Standards.SpacingSm)) }
    item { SectionDivider() }
    item { SectionHeader(title = "Chat") }

    item {
        SwitchRow(
            title = "Code Syntax Highlighting",
            description = "Colorize code blocks based on language (disable for faster scrolling)",
            checked = codeHighlightEnabled,
            onCheckedChange = { viewModel.setCodeHighlightEnabled(it) }
        )
    }
}

// ── Hardware Tuning Section ──

internal fun LazyListScope.hardwareTuningSection(
    hardwareTuningEnabled: Boolean,
    performanceMode: PerformanceMode,
    hardwareProfile: HardwareProfile?,
    viewModel: SettingsViewModel
) {
    item { Spacer(Modifier.height(Standards.SpacingSm)) }
    item { SectionDivider() }
    item { SectionHeader(title = "Hardware Tuning") }

    item {
        SwitchRow(
            title = "Hardware-Based Tuning",
            description = "Automatically optimize model parameters based on your device's hardware. Disable to set parameters manually.",
            checked = hardwareTuningEnabled,
            onCheckedChange = { viewModel.setHardwareTuningEnabled(it) }
        )
    }

    // ── Performance Mode ──
    item {
        Column {
            Text(
                text = "Performance Mode",
                style = MaterialTheme.typography.titleSmall,
                color = if (hardwareTuningEnabled) MaterialTheme.colorScheme.onSurface
                else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            )
            Spacer(Modifier.height(Standards.SpacingSm))
            ActionToggleGroup(
                items = PerformanceMode.entries.toList(),
                selectedItem = performanceMode,
                onItemSelected = { viewModel.setPerformanceMode(it) },
                itemLabel = { mode ->
                    when (mode) {
                        PerformanceMode.PERFORMANCE -> "Performance"
                        PerformanceMode.BALANCED -> "Balanced"
                        PerformanceMode.POWER_SAVING -> "Power Saver"
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = hardwareTuningEnabled
            )
            Spacer(Modifier.height(Standards.SpacingXs))
            Text(
                text = if (!hardwareTuningEnabled) {
                    "Enable hardware tuning to use performance presets"
                } else when (performanceMode) {
                    PerformanceMode.PERFORMANCE -> "Uses all fast cores. Best speed, higher battery use."
                    PerformanceMode.BALANCED -> "Uses performance cores only. Good speed and battery balance."
                    PerformanceMode.POWER_SAVING -> "Minimal threads and memory. Best battery life."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }

    hardwareProfile?.let { profile ->
        item {
            val topo = profile.cpuTopology
            val coreInfo = if (topo.scanSucceeded) {
                buildString {
                    if (topo.primeCoreCount > 0) append("${topo.primeCoreCount}P")
                    if (topo.performanceCoreCount > 0) {
                        if (isNotEmpty()) append("+")
                        append("${topo.performanceCoreCount}P")
                    }
                    if (topo.efficiencyCoreCount > 0) {
                        if (isNotEmpty()) append("+")
                        append("${topo.efficiencyCoreCount}E")
                    }
                    append(" cores")
                }
            } else {
                "${profile.cpuCores} cores"
            }

            StandardCard(
                title = "${profile.totalRamMB} MB RAM · $coreInfo · ${profile.cpuArch}",
                description = profile.deviceModel
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    ActionTextButton(
                        onClickListener = { viewModel.rescanHardware() },
                        icon = TnIcons.Refresh,
                        text = "Rescan",
                        shape = RoundedCornerShape(Standards.CardSmallCornerRadius)
                    )
                }
            }
        }
    }
}

// ── Model Configuration Section ──

internal fun LazyListScope.modelConfigurationSection(
    hardwareTuningEnabled: Boolean,
    installedModels: List<Model>,
    onModelEditor: () -> Unit
) {
    item { Spacer(Modifier.height(Standards.SpacingSm)) }
    item { SectionDivider() }
    item {
        SectionHeader(title = "Model Configuration") {
            ActionTextButton(
                onClickListener = onModelEditor,
                icon = TnIcons.Sparkles,
                text = "Configure",
                shape = RoundedCornerShape(Standards.CardSmallCornerRadius),
                enabled = !hardwareTuningEnabled
            )
        }
    }

    if (hardwareTuningEnabled) {
        item {
            Text(
                text = "Model parameters are managed by the performance engine. Disable hardware tuning to edit manually.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
            )
        }
    }

    if (installedModels.isEmpty()) {
        item {
            StandardCard(
                description = "No models installed. Download models from the store."
            )
        }
    } else {
        items(installedModels.size, key = { installedModels[it].id }) { index ->
            val model = installedModels[index]
            StandardCard(
                title = model.modelName,
                description = model.providerType.name,
                icon = TnIcons.Sparkles,
                onClick = if (!hardwareTuningEnabled) onModelEditor else ({})
            )
        }
    }
}

// ── AI Memory Section ──

internal fun LazyListScope.aiMemorySection(
    aiMemoryEnabled: Boolean,
    onAiMemoryClick: () -> Unit,
    viewModel: SettingsViewModel
) {
    item { Spacer(Modifier.height(Standards.SpacingSm)) }
    item { SectionDivider() }
    item { SectionHeader(title = "AI Memory") }

    item {
        SwitchRow(
            title = "AI Memory",
            description = "Remember facts about you across conversations",
            checked = aiMemoryEnabled,
            onCheckedChange = { viewModel.setAiMemoryEnabled(it) }
        )
    }

    item {
        Surface(
            onClick = onAiMemoryClick,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(Standards.RadiusMd),
            color = MaterialTheme.colorScheme.surfaceContainerLow
        ) {
            Column(modifier = Modifier.padding(Standards.SpacingLg)) {
                Text(
                    "View Memories",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    "See, search, and manage what the AI remembers about you",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

// ── Image Generation Section ──

internal fun LazyListScope.imageGenerationSection(
    imageBlurEnabled: Boolean,
    viewModel: SettingsViewModel
) {
    item { Spacer(Modifier.height(Standards.SpacingSm)) }
    item { SectionDivider() }
    item { SectionHeader(title = "Image Generation") }

    item {
        SwitchRow(
            title = "Blur Generated Images",
            description = "Blur images by default, tap to reveal",
            checked = imageBlurEnabled,
            onCheckedChange = { viewModel.setImageBlurEnabled(it) }
        )
    }
}

// ── About Section ──

internal fun LazyListScope.aboutSection(appVersion: String) {
    item { Spacer(Modifier.height(Standards.SpacingSm)) }
    item { SectionDivider() }
    item { SectionHeader(title = "About") }

    item {
        StandardCard(
            title = "ToolNeuron",
            description = "On-device AI — LLM, Image Generation, TTS"
        ) {
            BodyLabel(
                text = "Version $appVersion",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
