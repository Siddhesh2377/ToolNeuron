package com.dark.neuroverse.ui.drawer

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowCircleDown
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.twotone.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dark.neuroverse.ui.theme.rDP
import com.dark.neuroverse.viewModel.PluginHostViewModel
import com.dark.plugins.manager.PluginManager

@Composable
fun SettingsDrawerContent(
    viewModel: PluginHostViewModel,
    onSettingsClick: () -> Unit,
    onModelsClick: () -> Unit,
    onPluginClick: () -> Unit,
    onPluginStoreClick: () -> Unit,
) {
    val context = LocalContext.current
    val loadedPluginsState = viewModel.loadedPlugins.collectAsState()
    val installedPlugins = viewModel.installedPlugins.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxHeight()
            .width(250.dp)
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp)
    ) {
        Text(
            text = "More Options", style = MaterialTheme.typography.headlineMedium.copy(
                fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold
            ), modifier = Modifier.padding(top = 12.dp, bottom = 24.dp)
        )

        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {

            item {
                Text(
                    text = "Running Plugins", style = MaterialTheme.typography.headlineSmall.copy(
                        fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold
                    ), modifier = Modifier.padding(top = 12.dp, bottom = 8.dp)
                )
            }

            items(loadedPluginsState.value) { loadedPluginWrapper ->
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier
                    .clickable {
                        viewModel.setCurrentByName(loadedPluginWrapper.manifest?.name ?: "")
                        onPluginClick()
                    }
                    .background(
                        MaterialTheme.colorScheme.surface, shape = MaterialTheme.shapes.small
                    )
                    .padding(rDP(10.dp))) {
                    Text(
                        text = loadedPluginWrapper.manifest?.name ?: "",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f)
                    )

                    Icon(
                        Icons.TwoTone.Delete,
                        contentDescription = "Delete",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier
                            .padding(start = 8.dp)
                            .clickable {
                                viewModel.stopPlugin(loadedPluginWrapper.manifest?.name ?: "")
                            })
                }

            }

            item {
                Text(
                    text = "Installed Plugins", style = MaterialTheme.typography.headlineSmall.copy(
                        fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold
                    ), modifier = Modifier.padding(top = 12.dp, bottom = 8.dp)
                )
            }
            items(installedPlugins.value) { installedPlugins ->
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier
                    .clickable {
                        viewModel.runPlugin(installedPlugins.pluginName, context)
                        onPluginClick()
                    }
                    .background(
                        MaterialTheme.colorScheme.surface, shape = MaterialTheme.shapes.small
                    )
                    .padding(rDP(10.dp))) {
                    Text(
                        text = installedPlugins.pluginName,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f)
                    )

                    Icon(
                        Icons.TwoTone.Delete,
                        contentDescription = "Delete",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier
                            .padding(start = 8.dp)
                            .clickable {
                                viewModel.deletePlugin(installedPlugins.pluginName)
                            })
                }

            }
        }

        Spacer(Modifier.height(16.dp))

        Row(Modifier
            .clickable {
                onPluginStoreClick()
            }
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface, shape = MaterialTheme.shapes.medium)
            .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                "Plugin-Store",
                style = MaterialTheme.typography.titleLarge.copy(fontFamily = FontFamily.Serif)
            )
            Icon(Icons.Outlined.GridView, contentDescription = "Settings")
        }

        Spacer(Modifier.height(16.dp))

        Row(Modifier
            .clickable {
                onModelsClick()
            }
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface, shape = MaterialTheme.shapes.medium)
            .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                "Models",
                style = MaterialTheme.typography.titleLarge.copy(fontFamily = FontFamily.Serif)
            )
            Icon(Icons.Outlined.ArrowCircleDown, contentDescription = "Settings")
        }

        Spacer(Modifier.height(16.dp))

        Row(Modifier
            .clickable {
                onSettingsClick()
            }
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface, shape = MaterialTheme.shapes.medium)
            .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                "Settings",
                style = MaterialTheme.typography.titleLarge.copy(fontFamily = FontFamily.Serif)
            )
            Icon(Icons.Outlined.Settings, contentDescription = "Settings")
        }
    }
}
