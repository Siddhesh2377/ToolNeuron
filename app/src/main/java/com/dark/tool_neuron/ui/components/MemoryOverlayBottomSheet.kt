package com.dark.tool_neuron.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.dark.tool_neuron.global.Standards
import com.dark.tool_neuron.worker.ScoredVaultContent
import com.dark.tool_neuron.worker.VaultStatsInfo
import com.dark.tool_neuron.ui.icons.TnIcons

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MemoryOverlayBottomSheet(
    show: Boolean,
    isMemoryEnabled: Boolean,
    vaultStats: VaultStatsInfo?,
    memoryResults: List<ScoredVaultContent>,
    memoryEntryCount: Int,
    onDismiss: () -> Unit,
    onMemoryEnabledChange: (Boolean) -> Unit,
    onRefreshStats: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    if (show) {
        ModalBottomSheet(
            onDismissRequest = onDismiss,
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surface,
            dragHandle = {
                Box(
                    Modifier
                        .padding(vertical = Standards.SpacingMd)
                        .width(40.dp)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
                )
            }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 300.dp)
                    .padding(horizontal = Standards.SpacingLg),
                verticalArrangement = Arrangement.spacedBy(Standards.SpacingSm)
            ) {
                // Header
                SectionHeader(title = "Memory Vault") {
                    InfoBadge(
                        text = "$memoryEntryCount entries",
                        containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
                        contentColor = MaterialTheme.colorScheme.secondary
                    )
                }

                // Enable/Disable toggle
                SwitchRow(
                    title = "Enable Memory",
                    description = "Query personal knowledge vault when sending messages",
                    icon = TnIcons.ShieldLock,
                    checked = isMemoryEnabled,
                    onCheckedChange = onMemoryEnabledChange
                )

                // Vault stats
                if (vaultStats != null) {
                    SectionDivider(label = "Vault Statistics")

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(Standards.SpacingSm)
                    ) {
                        InfoCard(
                            title = "Messages",
                            value = "${vaultStats.messageCount}",
                            icon = TnIcons.Database,
                            modifier = Modifier.weight(1f)
                        )
                        InfoCard(
                            title = "Files",
                            value = "${vaultStats.fileCount}",
                            modifier = Modifier.weight(1f)
                        )
                        InfoCard(
                            title = "Size",
                            value = vaultStats.getFormattedSize(),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                // Memory results timeline
                if (memoryResults.isNotEmpty()) {
                    SectionDivider(label = "Recent Results")

                    MemoryTimelineView(
                        entries = memoryResults,
                        modifier = Modifier.heightIn(max = 400.dp)
                    )
                }

                Spacer(modifier = Modifier.height(Standards.SpacingLg))
            }
        }
    }
}
