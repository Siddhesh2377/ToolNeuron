package com.dark.tool_neuron.ui.screens.plugin_hub

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dark.tool_neuron.plugin.api.Plugin
import com.dark.tool_neuron.ui.components.ActionButton
import com.dark.tool_neuron.ui.components.ActionSwitch
import com.dark.tool_neuron.ui.icons.TnIcons
import com.dark.tool_neuron.ui.theme.LocalDimens
import com.dark.tool_neuron.ui.theme.LocalTnShapes
import com.dark.tool_neuron.viewmodel.PluginHubViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PluginHubScreen(
    onClose: () -> Unit,
    viewModel: PluginHubViewModel = hiltViewModel(),
) {
    val dimens = LocalDimens.current
    val enabledIds by viewModel.enabledIds.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Plugins",
                        fontWeight = FontWeight.Bold,
                    )
                },
                navigationIcon = {
                    ActionButton(
                        onClickListener = onClose,
                        icon = TnIcons.ChevronDown,
                        contentDescription = "Close",
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
        containerColor = MaterialTheme.colorScheme.surface,
    ) { innerPadding ->
        PluginList(
            plugins = viewModel.plugins,
            enabledIds = enabledIds,
            onToggle = viewModel::setEnabled,
            innerPadding = innerPadding,
            horizontalPadding = dimens.screenPadding,
        )
    }
}

@Composable
private fun PluginList(
    plugins: List<Plugin>,
    enabledIds: Set<String>,
    onToggle: (String, Boolean) -> Unit,
    innerPadding: PaddingValues,
    horizontalPadding: androidx.compose.ui.unit.Dp,
) {
    val dimens = LocalDimens.current
    if (plugins.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "No plugins registered",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
            )
        }
        return
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)
            .padding(horizontal = horizontalPadding),
        verticalArrangement = Arrangement.spacedBy(dimens.spacingSm),
    ) {
        items(plugins, key = { it.id }) { plugin ->
            PluginRow(
                plugin = plugin,
                enabled = plugin.id in enabledIds,
                onToggle = { onToggle(plugin.id, it) },
            )
        }
        item { Spacer(Modifier.height(dimens.spacingLg)) }
    }
}

@Composable
private fun PluginRow(
    plugin: Plugin,
    enabled: Boolean,
    onToggle: (Boolean) -> Unit,
) {
    val dimens = LocalDimens.current
    val tnShapes = LocalTnShapes.current
    var expanded by remember { mutableStateOf(false) }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = tnShapes.cardSmall,
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(
            modifier = Modifier.padding(dimens.cardPadding),
            verticalArrangement = Arrangement.spacedBy(dimens.spacingSm),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(dimens.spacingSm),
            ) {
                Surface(
                    shape = tnShapes.cardSmall,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                    modifier = Modifier.size(dimens.iconLg),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = plugin.icon,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(dimens.iconMd),
                        )
                    }
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = plugin.displayName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = plugin.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                    )
                }

                ActionSwitch(
                    checked = enabled,
                    onCheckedChange = onToggle,
                )
            }

            AnimatedVisibility(
                visible = expanded,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically(),
            ) {
                Column(
                    modifier = Modifier.padding(top = dimens.spacingSm),
                    verticalArrangement = Arrangement.spacedBy(dimens.spacingSm),
                ) {
                    plugin.Settings()
                }
            }
        }
    }
}
