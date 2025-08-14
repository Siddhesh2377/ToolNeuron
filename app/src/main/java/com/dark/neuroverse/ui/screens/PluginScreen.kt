package com.dark.neuroverse.ui.screens

import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.twotone.Dashboard
import androidx.compose.material.icons.twotone.GridView
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dark.neuroverse.activity.MainActivity
import com.dark.neuroverse.ui.theme.Success
import com.dark.neuroverse.viewModel.PluginStoreScreenViewModel
import com.dark.plugins.model.PluginLocalDB
import com.dark.plugins.ui.theme.rDP

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PluginStoreScreen(
    viewModel: PluginStoreScreenViewModel = viewModel()
) {
    val context = LocalContext.current
    LaunchedEffect(Unit) { viewModel.init(context) }

    val installed by viewModel.installedPlugins.collectAsStateWithLifecycle(emptyList())
    val running by viewModel.runningPlugins.collectAsStateWithLifecycle(emptyList())
    val current by viewModel.currentPlugin.collectAsStateWithLifecycle(null)

    val runningNames by remember(running) {
        derivedStateOf { running.mapNotNull { it.api?.getPluginInfo()?.name }.toSet() }
    }
    val currentName by remember(current) {
        derivedStateOf { current?.api?.getPluginInfo()?.name }
    }

    val addLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(), onResult = { uri ->
            if (uri != null) {
                viewModel.addPluginFromUri(context, uri)
                Toast.makeText(context, "Installing plugin…", Toast.LENGTH_SHORT).show()
            }
        })

    Scaffold(topBar = {
        TopAppBar(
            title = {
            Text(
                "Plugin Store",
                style = MaterialTheme.typography.titleLarge.copy(fontFamily = FontFamily.Serif)
            )
        }, navigationIcon = {
            Icon(
                Icons.TwoTone.GridView,
                contentDescription = "Home",
                modifier = Modifier.padding(8.dp)
            )
        }, actions = {
            TextButton(onClick = {
                val intent = Intent(context, MainActivity::class.java)
                context.startActivity(intent)
            }) {
                Icon(Icons.TwoTone.Dashboard, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text(
                    "Home",
                    style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Serif)
                )
            }
        }, scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior(rememberTopAppBarState())
        )
    }, floatingActionButton = {
        FloatingActionButton(
            onClick = {
            addLauncher.launch(
                arrayOf(
                    "application/zip",
                    "application/java-archive",
                    "application/vnd.android.package-archive",
                    "*/*"
                )
            )
        },
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary
        ) {
            Icon(Icons.Default.Add, contentDescription = "Add plugin from file")
        }
    }) { inner ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to MaterialTheme.colorScheme.background,
                        1f to MaterialTheme.colorScheme.primaryContainer
                    )
                )
                .padding(inner)
        ) {
            if (installed.isEmpty()) {
                EmptyState(
                    onSeed = {
                        addLauncher.launch(
                            arrayOf(
                                "application/zip",
                                "application/java-archive",
                                "application/vnd.android.package-archive",
                                "*/*"
                            )
                        )
                    })
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(installed, key = { it.pluginPath }) { plugin ->
                        val isRunning = runningNames.contains(plugin.pluginName)
                        val isCurrent = currentName == plugin.pluginName

                        PluginCard(
                            plugin = plugin,
                            isRunning = isRunning,
                            isCurrent = isCurrent,
                            onRun = {
                                viewModel.runPlugin(
                                    context,
                                    plugin.pluginName,
                                    data = mapOf("source" to "PluginStoreScreen")
                                )
                                val intent = Intent(
                                    context, MainActivity::class.java
                                ).putExtra("plugin_name", plugin.pluginName)
                                context.startActivity(intent)
                            },
                            onStop = {
                                viewModel.stopPlugin(plugin.pluginName)
                            },
                            onSetCurrent = {
                                viewModel.setCurrentPluginByName(plugin.pluginName)
                            },
                            onDelete = {
                                val ok = viewModel.deletePlugin(plugin.pluginName)
                                val msg =
                                    if (ok) "Deleted ${plugin.pluginName}" else "Failed to delete ${plugin.pluginName}"
                                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                            })
                    }
                    item { Spacer(Modifier.height(56.dp)) }
                }
            }
        }
    }
}

@Composable
private fun PluginCard(
    plugin: PluginLocalDB,
    isRunning: Boolean,
    isCurrent: Boolean,
    onRun: () -> Unit,
    onStop: () -> Unit,
    onSetCurrent: () -> Unit,
    onDelete: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val containerColor = colors.surface

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        elevation = CardDefaults.cardElevation(defaultElevation = rDP(2.dp))
    ) {
        ColumnISH(modifier = Modifier.padding(rDP(14.dp))) {

            // Top Row: Name + Status + Delete
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Plugin name with running indicator
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = plugin.pluginName,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontFamily = FontFamily.Serif,
                            color = colors.onSurface
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    if (isRunning) {
                        Spacer(Modifier.width(6.dp))
                        Box(
                            modifier = Modifier
                                .width(10.dp)
                                .height(10.dp)
                                .background(Success, shape = MaterialTheme.shapes.small)
                        )
                    }
                }

                if (isCurrent) {
                    AssistChip(
                        onClick = {},
                        label = {
                            Text(
                                "Current",
                                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Serif)
                            )
                        },
                        leadingIcon = { Icon(Icons.Outlined.Star, contentDescription = null) }
                    )
                }

                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete", tint = colors.error)
                }
            }

            Spacer(Modifier.height(rDP(6.dp)))

            // Version & Path
            Text(
                "Version: ${plugin.pluginVersion}",
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Serif),
                color = colors.secondary
            )
            Text(
                text = "Path: ${plugin.pluginPath}",
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Serif),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = colors.secondary
            )

            Spacer(Modifier.height(rDP(10.dp)))

            // Actions Row
            Row(horizontalArrangement = Arrangement.spacedBy(rDP(8.dp))) {
                if (isRunning) {
                    Button(
                        onClick = onStop,
                        colors = ButtonDefaults.buttonColors(containerColor = colors.error)
                    ) {
                        Icon(Icons.Filled.Stop, contentDescription = null)
                        Spacer(Modifier.width(rDP(4.dp)))
                        Text("Stop", style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Serif))
                    }
                } else {
                    Button(
                        onClick = onRun,
                        colors = ButtonDefaults.buttonColors(containerColor = colors.primary)
                    ) {
                        Icon(Icons.Filled.PlayArrow, contentDescription = null)
                        Spacer(Modifier.width(rDP(4.dp)))
                        Text("Run", style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Serif))
                    }
                }

                OutlinedButton(onClick = onSetCurrent, enabled = !isCurrent) {
                    Text(
                        if (isCurrent) "Already Current" else "Set Current",
                        style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Serif)
                    )
                }
            }
        }
    }
}



@Composable
private fun EmptyState(onSeed: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp), contentAlignment = Alignment.Center
    ) {
        ColumnISH(horizontal = Alignment.CenterHorizontally) {
            Text(
                "No plugins yet",
                style = MaterialTheme.typography.titleLarge.copy(fontFamily = FontFamily.Serif)
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Tap below to Load Plugins From Device",
                style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Serif)
            )
            Spacer(Modifier.height(16.dp))
            Button(onClick = onSeed) {
                Text(
                    "+  Add Plugins",
                    style = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Serif)
                )
            }
        }
    }
}

@Composable
private fun ColumnISH(
    modifier: Modifier = Modifier,
    horizontal: Alignment.Horizontal = Alignment.Start,
    content: @Composable () -> Unit
) = Column(
    modifier = modifier, horizontalAlignment = horizontal, content = { content() })
