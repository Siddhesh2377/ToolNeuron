package com.dark.tool_neuron.ui.screen.memory

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dark.tool_neuron.ui.components.ActionButton
import com.dark.tool_neuron.ui.components.ActionToggleGroup
import com.dark.tool_neuron.ui.theme.ManropeFontFamily
import com.dark.tool_neuron.ui.theme.rDp
import com.dark.tool_neuron.ui.theme.rSp
import com.dark.tool_neuron.viewmodel.memory.VaultInspectorScreen

private enum class VaultTab(val label: String) {
    EXPLORER("Explorer"),
    MANAGE("Manage"),
    LOGS("Logs"),
    DEBUG("Debug")
}

@Composable
fun VaultDashboard(onNavigateBack: () -> Unit) {
    var selectedTab by remember { mutableIntStateOf(0) }
    var previousTab by remember { mutableIntStateOf(0) }
    val tabs = VaultTab.entries

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = rDp(8.dp), vertical = rDp(8.dp)),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ActionButton(
                    onClickListener = onNavigateBack,
                    icon = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back"
                )
                Icon(
                    Icons.Outlined.Layers,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = rDp(8.dp), end = rDp(8.dp))
                )
                Text(
                    "Memory Vault",
                    fontFamily = ManropeFontFamily,
                    fontSize = rSp(18.sp),
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            // Tab selector using ActionToggleGroup
            ActionToggleGroup(
                items = tabs.toList(),
                selectedItem = tabs[selectedTab],
                onItemSelected = {
                    previousTab = selectedTab
                    selectedTab = tabs.indexOf(it)
                },
                itemLabel = { it.label },
                modifier = Modifier.padding(horizontal = rDp(16.dp))
            )

            // Content with smooth transition
            AnimatedContent(
                targetState = selectedTab,
                transitionSpec = {
                    val direction = if (targetState > previousTab) 1 else -1
                    (slideInHorizontally(
                        initialOffsetX = { fullWidth -> direction * fullWidth / 5 },
                        animationSpec = tween(250)
                    ) + fadeIn(tween(200))) togetherWith
                    (slideOutHorizontally(
                        targetOffsetX = { fullWidth -> -direction * fullWidth / 8 },
                        animationSpec = tween(250)
                    ) + fadeOut(tween(150)))
                },
                label = "vault_content"
            ) { tab ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = rDp(8.dp))
                ) {
                    when (tab) {
                        0 -> VaultDataExplorerScreen(onDrawerOpen = {})
                        1 -> VaultManagementScreen()
                        2 -> TerminalLoggerScreen()
                        3 -> VaultInspectorScreen()
                    }
                }
            }
        }
    }
}
