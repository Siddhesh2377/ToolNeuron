package com.dark.neuroverse.ui.screens.hub

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.Delete
import androidx.compose.material.icons.twotone.Download
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dark.neuroverse.ui.theme.rDP
import com.dark.neuroverse.viewModel.PluginStoreScreenViewModel

@Composable
fun OnlinePluginStoreScreen(
    viewModel: PluginStoreScreenViewModel = viewModel()
) {
    val plugins by viewModel.onlinePlugins.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        viewModel.loadOnlinePlugins()
    }

    LazyColumn(
        modifier = Modifier
            .padding(horizontal = rDP(12.dp)).padding(top = rDP(12.dp)),
        verticalArrangement = Arrangement.spacedBy(rDP(12.dp))
    ) {
        items(plugins) { pluginState ->
            val plugin = pluginState.onlinePlugin

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
                colors = CardDefaults.cardColors(MaterialTheme.colorScheme.secondary.copy(0.1f)),
            ) {
                Column(
                    modifier = Modifier.padding(rDP(16.dp)),
                    verticalArrangement = Arrangement.spacedBy(rDP(8.dp))
                ) {
                    // Title & Version Badge
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = plugin.name,
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold)
                        )
                        SuggestionChip(onClick = {}, label = { Text("v${plugin.version}") })
                    }

                    // Description
                    Text(
                        text = plugin.description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    // Metadata Row
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(rDP(16.dp)),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("📦 ${plugin.size}", style = MaterialTheme.typography.labelMedium)
                        Text("✍️ ${plugin.author}", style = MaterialTheme.typography.labelMedium)
                        plugin.apiVersion?.let {
                            Text("🔌 API $it", style = MaterialTheme.typography.labelMedium)
                        }
                    }

                    // Tags/Category
                    plugin.category?.let { category ->
                        Row(horizontalArrangement = Arrangement.spacedBy(rDP(8.dp))) {
                            SuggestionChip(onClick = {}, label = { Text(category) })
                        }
                    }

                    // Spacer
                    Spacer(modifier = Modifier.height(rDP(6.dp)))

                    // Progress or Actions
                    Crossfade(
                        targetState = when {
                            pluginState.isDownloading -> "downloading"
                            pluginState.isInstalled -> "installed"
                            else -> "idle"
                        }, label = "PluginActionCrossfade"
                    ) { state ->
                        when (state) {
                            "downloading" -> {
                                Column {
                                    LinearProgressIndicator(
                                        progress = { pluginState.progress },
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    Text(
                                        "${(pluginState.progress * 100).toInt()}%",
                                        style = MaterialTheme.typography.bodySmall,
                                        modifier = Modifier.align(Alignment.End)
                                    )
                                }
                            }

                            "installed" -> {
                                OutlinedButton(
                                    onClick = { viewModel.deleteOnlinePlugin(plugin, context) },
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        containerColor = MaterialTheme.colorScheme.errorContainer,
                                        contentColor = MaterialTheme.colorScheme.error
                                    ),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("Uninstall Plugin")
                                    Spacer(Modifier.width(rDP(10.dp)))
                                    Icon(Icons.TwoTone.Delete, "Delete")
                                }
                            }

                            else -> {
                                FilledTonalButton(
                                    onClick = { viewModel.installOnlinePlugin(plugin, context) },
                                    colors = ButtonDefaults.filledTonalButtonColors(
                                        containerColor = MaterialTheme.colorScheme.primary,
                                        contentColor = MaterialTheme.colorScheme.onPrimary
                                    ),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(Icons.TwoTone.Download, "Download")
                                    Spacer(Modifier.width(rDP(8.dp)))
                                    Text("Download & Install")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}