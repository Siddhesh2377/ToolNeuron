package com.dark.tool_neuron.ui.screen.memory

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.dark.tool_neuron.global.Standards
import com.dark.tool_neuron.ui.components.BodyLabel
import com.dark.tool_neuron.ui.components.CaptionText
import com.dark.tool_neuron.ui.components.SectionDivider
import com.dark.tool_neuron.ui.components.StandardCard
import com.dark.tool_neuron.ui.theme.rDp
import com.dark.tool_neuron.viewmodel.memory.VaultInspectorScreen
import kotlinx.coroutines.launch

enum class VaultScreen(
    val title: String,
    val icon: ImageVector,
    val selectedIcon: ImageVector,
    val description: String
) {
    DATA_EXPLORER(
        "Data Explorer",
        Icons.Outlined.Layers,
        Icons.Filled.Layers,
        "Browse all vault data"
    ),
    MANAGEMENT(
        "Management",
        Icons.Outlined.Dashboard,
        Icons.Filled.Dashboard,
        "Stats & operations"
    ),
    LOGGER(
        "Logger",
        Icons.Outlined.Terminal,
        Icons.Filled.Terminal,
        "View operation logs"
    ),
    INSPECTOR(
        "Inspector",
        Icons.Outlined.BugReport,
        Icons.Filled.BugReport,
        "Debug & inspect"
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VaultDashboard() {
    var selectedScreen by remember { mutableStateOf(VaultScreen.DATA_EXPLORER) }
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                drawerShape = RoundedCornerShape(
                    topEnd = rDp(20.dp),
                    bottomEnd = rDp(20.dp)
                ),
                drawerContainerColor = MaterialTheme.colorScheme.surface
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxHeight()
                        .padding(rDp(Standards.SpacingLg)),
                    verticalArrangement = Arrangement.spacedBy(rDp(Standards.SpacingXs))
                ) {
                    // Header
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = rDp(Standards.SpacingLg)),
                        horizontalArrangement = Arrangement.spacedBy(rDp(Standards.SpacingMd)),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(rDp(44.dp))
                                .clip(RoundedCornerShape(rDp(Standards.CardCornerRadius)))
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Filled.Memory,
                                contentDescription = null,
                                modifier = Modifier.size(rDp(Standards.IconLg)),
                                tint = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                        Column {
                            Text(
                                "Memory Vault",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                "Data Management",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    SectionDivider()

                    // Navigation items
                    VaultScreen.entries.forEach { screen ->
                        VaultNavItem(
                            screen = screen,
                            isSelected = selectedScreen == screen,
                            onClick = { selectedScreen = screen }
                        )
                    }

                    Spacer(Modifier.weight(1f))

                    SectionDivider()

                    // Version info
                    StandardCard(
                        title = "MemoryVault",
                        description = "v1.0.0",
                        icon = Icons.Outlined.Info,
                        iconTint = MaterialTheme.colorScheme.onSurfaceVariant,
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    )
                }
            }
        },
        content = {
            when (selectedScreen) {
                VaultScreen.DATA_EXPLORER -> {
                    VaultDataExplorerScreen(onDrawerOpen = {
                        scope.launch {
                            drawerState.open()
                        }
                    })
                }
                VaultScreen.MANAGEMENT -> VaultManagementScreen()
                VaultScreen.LOGGER -> TerminalLoggerScreen()
                VaultScreen.INSPECTOR -> VaultInspectorScreen()
            }
        }
    )
}

@Composable
fun VaultNavItem(
    screen: VaultScreen,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    NavigationDrawerItem(
        icon = {
            Icon(
                if (isSelected) screen.selectedIcon else screen.icon,
                contentDescription = null,
                modifier = Modifier.size(rDp(22.dp))
            )
        },
        label = {
            Column {
                Text(
                    screen.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                )
                CaptionText(text = screen.description)
            }
        },
        selected = isSelected,
        onClick = onClick,
        shape = RoundedCornerShape(rDp(Standards.CardCornerRadius)),
        modifier = Modifier.padding(vertical = rDp(2.dp))
    )
}
